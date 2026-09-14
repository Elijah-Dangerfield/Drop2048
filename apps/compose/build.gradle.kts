plugins {
    id("drop2048.application")
    id("co.touchlab.skie") version "0.10.12"
    alias(libs.plugins.sentryAndroid)
    alias(libs.plugins.baselineProfile)
}

android {
    namespace = "com.dangerfield.drop2048"
}

/**
 * Consume the committed Baseline Profile rather than regenerating it on every
 * release build — generation starts an emulator and walks the app, which is
 * minutes nobody wants in the release path.
 *
 * Regenerate deliberately when the app's shape changes:
 * `./gradlew :apps:compose:generateBaselineProfile`
 */
baselineProfile {
    automaticGenerationDuringBuild = false
}

dependencies {
    baselineProfile(projects.apps.baselineprofile)

    /**
     * The house ad network, on the **debug** variant only.
     *
     * This is the whole structural guarantee behind `HouseAds`. The house
     * network can answer `AdShowResult.Rewarded` without an ad having played,
     * which is exactly what L66 says a stand-in must be incapable of; the type
     * cannot express that impossibility here, so the build does. A release APK
     * is compiled and merged without this module on the classpath, so it
     * contains no house network, no binding for one, and no code that draws a
     * placeholder — `NoHouseAds` is the only `HouseAds` anvil can find, and its
     * `network` is null.
     *
     * `verifyNoHouseAdsInRelease` below is what stops that being a comment.
     */
    debugImplementation(projects.libraries.ads.fake)

    /**
     * The owner's directive channel, on the **debug** variant only.
     *
     * Same argument as the house network, one step further: this module records
     * every frame of the app into a graphics layer so it can screenshot itself,
     * draws a draggable button over the game, and uploads the session log
     * without asking — because the only person who can reach it wrote the app.
     * A `BuildInfo.isTesterBuild` check around all of that is a runtime answer
     * to a question the build can answer, so the build answers it: a release APK
     * contains no button, no panel, no layer recording and no JPEG encoder.
     *
     * `verifyNoDevFeedbackInRelease` below is what stops that being a comment.
     */
    debugImplementation(projects.libraries.devfeedback.tester)
}

/**
 * Fails the build if the house ad network ever reaches a release artifact.
 *
 * A dependency scoped to one variant is only structural for as long as nobody
 * moves it, and moving it is a one-word edit in a file nobody reads twice
 * (`debugImplementation` → `implementation`). This resolves the release runtime
 * classpath and looks, which is the same question a reviewer would have to ask
 * and cannot answer by eye.
 *
 * Wired into `check` rather than into `assembleRelease` on purpose: the mistake
 * has to be caught by the build everyone runs, not by the one nobody runs
 * locally.
 */
val verifyNoHouseAdsInRelease = tasks.register("verifyNoHouseAdsInRelease") {
    val classpath = configurations.named("releaseRuntimeClasspath")
    val names = classpath.map { config ->
        config.incoming.resolutionResult.allComponents.map { it.id.displayName }
    }
    inputs.property("releaseComponents", names)
    doLast {
        val offenders = names.get().filter { it.contains(":libraries:ads:fake") }
        check(offenders.isEmpty()) {
            "The house ad network is on the release runtime classpath: $offenders. " +
                "It must stay a debugImplementation — see HouseAds."
        }
    }
}

tasks.named("check") { dependsOn(verifyNoHouseAdsInRelease) }

/**
 * Fails the build if the directive channel ever reaches a release artifact.
 *
 * The twin of `verifyNoHouseAdsInRelease`, and it earns its own task rather than
 * a shared one because the two modules are held out for different reasons and a
 * combined failure message would explain neither. This one is about a surface
 * that photographs the player's screen and uploads their session log.
 *
 * A second `debugImplementation` is a second one-word edit waiting to happen,
 * and the first one already proved that a variant scope is structural only for
 * as long as nobody moves it.
 */
val verifyNoDevFeedbackInRelease = tasks.register("verifyNoDevFeedbackInRelease") {
    val classpath = configurations.named("releaseRuntimeClasspath")
    val names = classpath.map { config ->
        config.incoming.resolutionResult.allComponents.map { it.id.displayName }
    }
    inputs.property("releaseComponents", names)
    doLast {
        val offenders = names.get().filter { it.contains(":libraries:devfeedback:tester") }
        check(offenders.isEmpty()) {
            "The tester directive channel is on the release runtime classpath: $offenders. " +
                "It must stay a debugImplementation — see DevFeedback."
        }
    }
}

tasks.named("check") { dependsOn(verifyNoDevFeedbackInRelease) }

/**
 * Sentry's Android Gradle plugin, for exactly one job: making obfuscated crash
 * reports readable.
 *
 * R8 renames methods, so from the first minified release every Sentry frame
 * arrives as `a.b.c`. Deobfuscating needs two things — the mapping file
 * uploaded, and a ProGuard UUID stamped into the build tying that mapping to
 * this APK. A hand-rolled `sentry-cli upload-proguard` step supplies only the
 * first: with no UUID to match against, the upload associates with nothing and
 * still reports success. Confirm this is working by unzipping the APK and
 * checking `assets/sentry-debug-meta.properties` for `io.sentry.ProguardUuids`,
 * not by trusting a green upload step.
 */
sentry {
    org.set(providers.environmentVariable("SENTRY_ORG"))
    projectName.set(providers.environmentVariable("SENTRY_PROJECT"))
    authToken.set(providers.environmentVariable("SENTRY_AUTH_TOKEN"))

    // This project already uses the Kotlin Multiplatform Sentry SDK.
    // Auto-installation would add `sentry-android` on top of it, and two SDKs
    // initialising in one process is not a thing to discover in production.
    autoInstallation { enabled.set(false) }

    // Always stamp the UUID: it is what makes a mapping associable at all, and
    // it costs nothing in a build without a token.
    includeProguardMapping.set(true)

    // Only upload when a token exists, so a contributor can still build a
    // release locally without one.
    autoUploadProguardMapping.set(
        providers.environmentVariable("SENTRY_AUTH_TOKEN").isPresent,
    )

    // No build-time telemetry to Sentry about our Gradle builds.
    telemetry.set(false)
}

kotlin {

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.splashscreen)
            implementation(libs.androidx.work.runtime)
            implementation(compose.uiTooling)
        }

        iosMain.dependencies {
            // iOS has no build-type source sets, so the house network is linked
            // into every iOS binary and the guard is `BuildInfo.isDebug`
            // (`Platform.isDebugBinary`, set by the Xcode configuration) inside
            // `HouseAdNetwork.select`. Stated here rather than assumed: this is
            // the one platform where the guarantee is a runtime check.
            implementation(projects.libraries.ads.fake)

            // Same gap, and here it is load-bearing rather than merely
            // tolerated: a TestFlight build *is* a release binary, so holding
            // the directive channel out of every release iOS binary would mean
            // the owner could only file directives from a cable. The guard is
            // `BuildInfo.isTesterBuild` inside `DevFeedbackHost.Host`, which on
            // iOS is `Platform.isDebugBinary` or a sandbox receipt.
            implementation(projects.libraries.devfeedback.tester)
        }

        commonMain.dependencies {
            // Project dependencies
            api(projects.libraries.core)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.drop2048.impl)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.navigation.impl)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.review)
            implementation(projects.libraries.review.impl)

            implementation(projects.libraries.storage)
            implementation(projects.libraries.storage.impl)
            implementation(projects.libraries.drop2048.storage)
            implementation(projects.libraries.config)
            implementation(projects.libraries.config.impl)
            implementation(projects.libraries.gameconfig)
            implementation(projects.libraries.drop2048.storage)
            implementation(projects.libraries.progress)
            implementation(projects.libraries.progress.impl)
            implementation(projects.libraries.ads)
            implementation(projects.libraries.ads.impl)
            implementation(projects.libraries.devfeedback)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.billing.impl)
            implementation(projects.libraries.achievements)
            implementation(projects.libraries.achievements.impl)
            implementation(projects.libraries.leaderboards)
            implementation(projects.libraries.leaderboards.impl)
            implementation(projects.libraries.sharing)
            implementation(projects.libraries.sharing.impl)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.networking.impl)
            implementation(projects.libraries.telemetry.impl)

            implementation(projects.features.achievements)
            implementation(projects.features.achievements.impl)
            implementation(projects.features.daily)
            implementation(projects.features.daily.impl)
            implementation(projects.features.debug)
            implementation(projects.features.debug.impl)
            implementation(projects.features.game)
            implementation(projects.features.game.impl)
            implementation(projects.features.gate)
            implementation(projects.features.gate.impl)
            implementation(projects.features.home)
            implementation(projects.features.home.impl)
            implementation(projects.features.paywall)
            implementation(projects.features.paywall.impl)
            implementation(projects.features.settings)
            implementation(projects.features.settings.impl)
            implementation(projects.features.stats)
            implementation(projects.features.stats.impl)

            implementation(libs.atomicfu)
            
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }
    }
}