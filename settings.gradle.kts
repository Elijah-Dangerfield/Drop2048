enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "Drop2048"

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        google()
        mavenCentral()
    }
}

// `-DserverOnly=true` (set by apps/server/Dockerfile) trims the build graph to
// just :apps:server so a server image build needs no Android SDK or Kotlin/Native
// toolchain. The property name is intentionally project-agnostic so the rename
// tooling can't break the Dockerfile↔settings contract. The server is a plain JVM
// module with no client-library deps, so nothing else has to be included. If
// apps/server ever depends on a :libraries:* module, add an always-included
// `include(...)` for it here (outside the `if`) and a matching COPY in the Dockerfile.
val serverOnly = System.getProperty("serverOnly") == "true"

// Apps (always included)
include(":apps")
include(":apps:server")

if (!serverOnly) {
    include(":apps:compose")
    // Note: iOS app is not a Gradle module - it's an Xcode project in apps/ios/

    // End-to-end integration harness: the real client stack (real view models)
    // driven against a real in-process Ktor server over a Testcontainers
    // Postgres. Depends on client impl modules + :apps:server, so it's gated
    // out of the server-only build.
    include(":apps:integration")

    // Baseline Profile generation + the minified-release smoke test. A
    // com.android.test module that ships nothing and exists only at build time,
    // so it is client-side and gated out of the server-only graph.
    include(":apps:baselineprofile")

    // Compose Multiplatform (web) admin console for remote config. The server
    // serves the prebuilt bundle at /admin; CI builds it — the server build
    // itself stays JS-toolchain-free, so it's gated out of the server-only
    // graph like the rest of the client. The first (and only) JS target.
    include(":apps:admin")

    // Features
    // SPEC 15's badge grid. The catalog and the fold are in
    // :libraries:achievements; this is the screen that draws them.
    include(":features:achievements")
    include(":features:achievements:impl")
    // SPEC 14's Daily Challenge screen. The board it opens is :features:game
    // with a mode argument, not a second game screen.
    include(":features:daily")
    include(":features:daily:impl")
    include(":features:game")
    include(":features:game:impl")
    // Force update, maintenance and legal re-accept. Rendered *instead of* the
    // nav host rather than navigated to, so a blocking gate has no back stack
    // entry to pop and no destination a deep link can land behind.
    include(":features:gate")
    include(":features:gate:impl")
    include(":features:home")
    include(":features:home:impl")
    include(":features:settings")
    include(":features:settings:impl")
    include(":features:stats")
    include(":features:stats:impl")

    // Libraries
    // SPEC 15's twenty-four badges: the catalog, the fold over the fact log, and
    // the `achievement_fact` / `achievement_unlock` tables. They unlock and post
    // and pay nothing — SPEC 2 cut the currency they used to pay in.
    include(":libraries:achievements")
    include(":libraries:achievements:impl")
    // SPEC 15's platform boards. Game Center on iOS; Play Games is not in v1, so
    // Android binds an inert seam rather than a second implementation.
    include(":libraries:leaderboards")
    include(":libraries:leaderboards:impl")
    // The share sheet and the string that goes in it. Deliberately dependency-free.
    include(":libraries:sharing")
    include(":libraries:sharing:impl")
    // The game engine. Zero project dependencies on purpose (SPEC 4.1) — it is a
    // pure state machine and everything downstream assumes it stays that way.
    include(":libraries:cascade")
    include(":libraries:config")
    include(":libraries:config:impl")
    include(":libraries:core")
    // SPEC 10's remote keys, one `ConfiguredValue` each, plus the assembler that
    // turns the gameplay half into an `EngineConfig`. Sits between :config and
    // :cascade so the engine keeps its zero project dependencies.
    include(":libraries:gameconfig")
    include(":libraries:flowroutines")
    include(":libraries:flowroutines:testing")
    include(":libraries:navigation")
    include(":libraries:navigation:impl")
    include(":libraries:networking")
    include(":libraries:networking:impl")
    // One row per completed run (SPEC 11), and every stat in SPEC 15 folded out
    // of that table. The only place a best score is allowed to come from.
    include(":libraries:progress")
    include(":libraries:progress:impl")
    include(":libraries:resources")
    include(":libraries:review")
    include(":libraries:review:impl")
    include(":libraries:storage")
    include(":libraries:storage:impl")
    // No api sibling on purpose: the public surface is the `logEvent`
    // extension in :libraries:core; this impl only hosts the experimental
    // opentelemetry-kotlin dependency + the GrafanaLogTree wiring.
    include(":libraries:telemetry:impl")
    include(":libraries:drop2048")
    include(":libraries:drop2048:impl")
    include(":libraries:drop2048:storage")
    include(":libraries:ui")

    // The balance harness. A plain JVM module that ships nothing and exists only
    // at build time: it plays :libraries:cascade headless under scripted policies
    // so SPEC 5.3's spawn table is measured rather than guessed. Client-side dev
    // tooling, so it is gated out of the server-only graph like everything else.
    include(":tools:balance")

    // Custom detekt rules — a standalone JVM jar detekt loads via
    // `detektPlugins`. Dev/CI tooling only, never shipped; gated out of the
    // server-only Docker build like every other client module.
    include(":detekt-rules")
}