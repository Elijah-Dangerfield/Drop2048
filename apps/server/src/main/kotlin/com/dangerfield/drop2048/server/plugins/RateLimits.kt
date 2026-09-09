package com.dangerfield.drop2048.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.request.header
import io.ktor.server.request.path
import kotlin.time.Duration.Companion.minutes

/**
 * Rate limiting. A global per-IP bucket guards every route. A route that wants
 * a tighter cap registers a named bucket here and opts in with
 * `rateLimit(RateLimitName(MY_LIMIT)) { … }` at the route.
 *
 * Keying is per-IP — there is no account to key on. `/_health` is excluded so health probes don't drain the
 * bucket. Limits are deliberately loose — they catch hot loops and trivial
 * abuse, not concerted DoS (your edge/CDN owns that).
 */
fun Application.installRateLimits() {
    install(RateLimit) {
        global {
            rateLimiter(limit = 600, refillPeriod = 1.minutes)
            requestKey { call -> call.clientIp() }
            requestWeight { call, _ -> if (call.request.path().startsWith("/_health")) 0 else 1 }
        }
    }
}

/**
 * Best-effort client IP. Trusts the standard reverse-proxy headers (Fly's
 * `Fly-Client-IP` first, then `X-Forwarded-For`, then the socket). Order
 * matters: the wrong choice makes everyone share the edge IP and the limiter
 * degenerates to one global bucket.
 */
internal fun io.ktor.server.application.ApplicationCall.clientIp(): String {
    request.header("Fly-Client-IP")?.takeIf { it.isNotBlank() }?.let { return it }
    request.header("X-Forwarded-For")?.split(',')?.firstOrNull()?.trim()
        ?.takeIf { it.isNotBlank() }?.let { return it }
    return request.local.remoteHost
}
