plugins {
    id("drop2048.kotlin.multiplatform")
}

android {
    namespace = "com.dangerfield.drop2048.apps.integration"
}

// End-to-end integration harness. The tests run as Android unit tests on the
// host JVM (`testDebugUnitTest`) — the same path the feature view models already
// compile through — so they can drive the REAL client stack against a REAL
// in-process Ktor server over a REAL Postgres (Testcontainers). Everything lives in the `androidUnitTest` source set;
// commonMain stays empty (nothing ships here, and the iOS target must not try
// to link the JVM-only server).
//
// No `jvm{}` targets are added to the client libraries — we reuse their
// existing Android variants on the host JVM. The one unusual edge this module
// proves out is consuming the JVM-only `:apps:server` from an Android
// unit-test classpath.
/**
 * The repo root, for the one test that reads the repo as text.
 *
 * `FeedbackTriageQueryContractTest` holds `FeedbackKind`'s tag values against the
 * queries in `.claude/skills/feedback-triage/SKILL.md`, which means reading two
 * files that are not on any classpath. A unit test's working directory is the
 * module, so it needs telling where the root is; the alternative is walking up
 * looking for `settings.gradle.kts`, which is the same fact guessed at instead
 * of supplied.
 */
tasks.withType<Test>().configureEach {
    systemProperty("drop2048.repoRoot", rootDir.absolutePath)
}

kotlin {
    sourceSets {
        androidUnitTest.dependencies {
            // Real server: installApp, ServerComponent, Database.connect.
            implementation(projects.apps.server)

            // Real client stack beneath the view models.
            implementation(projects.libraries.networking)
            implementation(projects.libraries.networking.impl)
            implementation(projects.libraries.config)
            implementation(projects.libraries.config.impl)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.core)

            // Boot a real server on an ephemeral port. Declared here because
            // :apps:server's dependencies are
            // `implementation`-scoped and don't leak to consumers' compile
            // classpaths.
            implementation(libs.ktor.serverCore)
            implementation(libs.ktor.serverNetty)
            // The client's HttpClient {} resolves its engine per platform;
            // supply the Android/JVM one explicitly so engine discovery is
            // deterministic on the host JVM.
            implementation(libs.ktor.client.okhttp)

            // Real Postgres for the server side (same recipe as the server's
            // own DatabaseTest — shared container per JVM, Flyway migrations
            // through the production Database.connect path).
            implementation(libs.testcontainers.postgres)

            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
