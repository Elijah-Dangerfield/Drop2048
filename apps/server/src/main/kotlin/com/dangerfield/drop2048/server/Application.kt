package com.dangerfield.drop2048.server

import com.dangerfield.drop2048.server.config.AdminConfig
import com.dangerfield.drop2048.server.config.ServerConfig
import com.dangerfield.drop2048.server.data.InMemoryExampleSource
import com.dangerfield.drop2048.server.data.WebhookConfigChangeNotifier
import com.dangerfield.drop2048.server.db.Database
import com.dangerfield.drop2048.server.di.ServerComponent
import com.dangerfield.drop2048.server.di.create
import com.dangerfield.drop2048.server.domain.ConfigChangeNotifier
import com.dangerfield.drop2048.server.plugins.installAdminWeb
import com.dangerfield.drop2048.server.plugins.installCors
import com.dangerfield.drop2048.server.plugins.installHttpServerTracing
import com.dangerfield.drop2048.server.plugins.installObservability
import com.dangerfield.drop2048.server.plugins.installOpenTelemetry
import com.dangerfield.drop2048.server.plugins.installRateLimits
import com.dangerfield.drop2048.server.plugins.installSentry
import com.dangerfield.drop2048.server.plugins.installSerialization
import com.dangerfield.drop2048.server.plugins.installStatusPages
import com.dangerfield.drop2048.server.plugins.installWebSockets
import com.dangerfield.drop2048.server.routes.appConfigRoutes
import com.dangerfield.drop2048.server.routes.configAdminRoutes
import com.dangerfield.drop2048.server.routes.exampleRoutes
import com.dangerfield.drop2048.server.routes.healthRoutes
import io.ktor.server.application.Application
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory

/**
 * Single source of truth for how the app boots. Stays small on purpose — the
 * plugins/ and routes/ packages own their concerns and this wires them together
 * in the right order.
 *
 *  - [module] does production-only setup (observability, the DB connection) and
 *    builds the DI graph, then delegates to [installApp].
 *  - [installApp] installs the functional plugins + mounts every route. It is
 *    the seam reused by full-stack tests: a test builds a [ServerComponent]
 *    against a Testcontainers database to exercise the real plugins + routes
 *    + DB.
 *
 * Graceful degradation: with no `DATABASE_URL` the server runs in limited mode
 * (health + example) rather than refusing to boot — so you can clone and run
 * with zero config.
 *
 * Order matters: serialization before status pages (so error envelopes encode),
 * CORS early.
 */
fun Application.module(config: ServerConfig) {
    val logger = LoggerFactory.getLogger("Bootstrap")
    logger.info("Booting server on ${config.http.host}:${config.http.port}")

    // Production-only observability, kept out of [installApp] so tests don't pay
    // for it. Sentry first (so later boot failures are captured), then OTel + HTTP
    // tracing (so subsequent plugins' spans export), then request logging.
    installSentry(config.sentry)
    val openTelemetry = installOpenTelemetry(config.observability)
    installHttpServerTracing(openTelemetry)
    installObservability()

    val database = config.database?.let {
        Database.connect(it).also { logger.info("Database connected and migrations applied") }
    }
    if (database == null) {
        logger.warn("DATABASE_URL not set — limited mode (no DB-backed routes). See apps/server/README.md.")
    }

    val component = database?.let { ServerComponent::class.create(it) }
    installApp(
        component = component,
        adminConfig = config.admin,
        configChangeNotifier = component?.let {
            WebhookConfigChangeNotifier(
                webhookUrl = config.configChange.webhookUrl,
                environment = config.observability.environment,
                scope = it.provideServerCoroutineScope(),
            )
        } ?: ConfigChangeNotifier {},
    )

    // The hosted admin console (static bundle at /admin). Outside installApp so
    // integration tests don't need a bundle on disk.
    installAdminWeb(config.admin.webDir)
}

/**
 * Installs the functional plugins + every route. Shared by production [module]
 * and full-stack tests (which pass a real [component]).
 *
 * [component] is null only in limited mode (no `DATABASE_URL`). Health + the
 * example resource are always served.
 */
fun Application.installApp(
    component: ServerComponent?,
    adminConfig: AdminConfig = AdminConfig(apiToken = null),
    configChangeNotifier: ConfigChangeNotifier = ConfigChangeNotifier {},
) {
    installSerialization()
    installCors()
    installRateLimits()
    installStatusPages()
    installWebSockets()

    routing {
        healthRoutes()
        exampleRoutes(component?.exampleSource ?: InMemoryExampleSource())
        if (component != null) {
            appConfigRoutes(component.appConfigSource)
            // Admin API is inert without a token: requireAdmin 401s every call
            // when ADMIN_API_TOKEN is unset, so mounting is gated for clarity,
            // not security.
            if (!adminConfig.apiToken.isNullOrBlank()) {
                configAdminRoutes(
                    config = adminConfig,
                    repository = component.appConfigAdminRepository,
                    manifestRepository = component.appConfigManifestRepository,
                    notifier = configChangeNotifier,
                )
            }
        }
    }
}
