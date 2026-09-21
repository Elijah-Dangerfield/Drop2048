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

/**
 * Whether this invocation may put the house ad network on an iOS compile
 * classpath.
 *
 * Kotlin/Native has no build-type source sets. `iosArm64CompileKlibraries` and
 * `iosSimulatorArm64CompileKlibraries` are one configuration each, shared by the
 * debug and the release framework link, so there is no `debugImplementation` to
 * scope `:libraries:ads:fake` to and it used to be linked into every iOS binary:
 * a release `ComposeApp` contained the anvil provider
 * `provideHouseAdNetworkHouseAds`, the `HouseAdHost` composable and every
 * `HouseAdNetwork` member. iOS was left holding only the `BuildInfo.isDebug`
 * check inside `HouseAds`, which is the weakest of the three layers.
 *
 * The build type is known one level up, so that is where it is read. Xcode sets
 * `CONFIGURATION` for the run-script phase that calls
 * `embedAndSignAppleFrameworkForXcode`, which is how every shipped iOS artifact
 * is produced, and it is the same signal the Kotlin plugin itself reads to pick
 * which framework to embed.
 *
 * **Fail closed.** Anything that is not demonstrably a Debug Xcode build counts
 * as a release build, so a bare `./gradlew linkReleaseFramework…`, CI, fastlane
 * and an unset environment all land on the safe side. A command-line debug link
 * that actually wants to see house ads opts back in with
 * `-Pdrop2048.iosHouseAds=true`. Getting this wrong costs a debug build its
 * placeholder ads; getting it wrong the other way ships a network that can grant
 * a reward without an ad.
 */
val iosLinksHouseAds: Boolean = providers.environmentVariable("CONFIGURATION")
    .map { it.startsWith("Debug", ignoreCase = true) }
    .orElse(providers.gradleProperty("drop2048.iosHouseAds").map(String::toBoolean))
    .getOrElse(false)

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
 * The iOS twin of [verifyNoHouseAdsInRelease], on the graph.
 *
 * `iosLinksHouseAds` is the exclusion; this is what stops it being a comment,
 * and it is not a tautology over that flag. It also catches the module arriving
 * **transitively**. `:libraries:ads:impl` or `:features:debug:impl` picking up
 * `:libraries:ads:fake` would put the house network back into every iOS binary
 * without anyone editing the `iosMain` block this file guards.
 *
 * Both Apple targets, because the simulator one is the target everybody builds
 * and `iosArm64` is the one that reaches the App Store.
 *
 * Skipped, rather than inverted, when the invocation *is* a debug iOS build:
 * there is nothing it could truthfully assert then, and `check` is not something
 * Xcode runs.
 */
val verifyNoHouseAdsInIosRelease = tasks.register("verifyNoHouseAdsInIosRelease") {
    val classpaths = listOf("iosArm64CompileKlibraries", "iosSimulatorArm64CompileKlibraries")
        .map { name ->
            name to configurations.named(name).map { config ->
                config.incoming.resolutionResult.allComponents.map { it.id.displayName }
            }
        }
    val required = !iosLinksHouseAds
    classpaths.forEach { (name, components) -> inputs.property(name, components) }
    onlyIf("this invocation is not building a debug iOS binary") { required }
    doLast {
        val offenders = classpaths.flatMap { (name, components) ->
            components.get().filter { it.contains(":libraries:ads:fake") }.map { "$name: $it" }
        }
        check(offenders.isEmpty()) {
            "The house ad network is on an iOS release compile classpath: $offenders. " +
                "It may only be linked when `iosLinksHouseAds`. See HouseAds."
        }
    }
}

tasks.named("check") { dependsOn(verifyNoHouseAdsInIosRelease) }

/**
 * The same question asked of the linked artifact, which is the only thing that
 * actually proves anything.
 *
 * The graph check above trusts `iosLinksHouseAds` to have decided correctly. If
 * that flag ever reads true during a release link, whether from a renamed Xcode
 * variable, a daemon holding a stale environment or a future edit to the
 * expression, then the graph check *skips itself* and the module is linked.
 * That is precisely the silent regression this whole exercise is about, and the
 * only thing that can see it is `ComposeApp` itself.
 *
 * Free, because it only runs as a finalizer of a link that already took minutes.
 *
 * The `NoHouseAds` probe is not belt and braces. A search for an absent string
 * passes whether or not the search works, so a toolchain that stopped embedding
 * Kotlin type names would turn this task into a green light forever (L: a clean
 * run does not prove the check ran). `NoHouseAds` is the binding a release build
 * must resolve `HouseAds` to, so it has to be present; if neither string is
 * there, the task is not looking at what it thinks it is and says so.
 */
listOf("IosArm64", "IosSimulatorArm64").forEach { target ->
    val binary = layout.buildDirectory.file(
        "bin/${target.replaceFirstChar(Char::lowercaseChar)}/releaseFramework/" +
            "ComposeApp.framework/ComposeApp",
    )
    val houseNetwork = "com.dangerfield.drop2048.libraries.ads.fake"
    val probe = "NoHouseAds"

    val verify = tasks.register("verifyNoHouseAdsIn${target}ReleaseFramework") {
        outputs.upToDateWhen { false }
        doLast {
            val file = binary.get().asFile
            check(file.isFile) { "No linked release framework at $file to check." }
            val bytes = file.readBytes()
            val present = listOf(houseNetwork, probe).filter { needle ->
                val pattern = needle.encodeToByteArray()
                var start = 0
                var found = false
                while (!found && start <= bytes.size - pattern.size) {
                    var i = 0
                    while (i < pattern.size && bytes[start + i] == pattern[i]) i++
                    found = i == pattern.size
                    start++
                }
                found
            }
            check(houseNetwork !in present) {
                "The house ad network is linked into ${file.name} for $target. " +
                    "A release iOS binary must contain no house network. See HouseAds."
            }
            check(probe in present) {
                "Found neither $houseNetwork nor $probe in ${file.name}. This check reads " +
                    "Kotlin type names out of the binary, and finding neither means it is no " +
                    "longer reading them, not that the binary is clean."
            }
        }
    }

    tasks.matching { it.name == "linkReleaseFramework$target" }
        .configureEach { finalizedBy(verify) }
}

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
            // The house network, on debug iOS binaries only. Kotlin/Native has
            // no `debugImplementation`, so the build type comes from
            // `iosLinksHouseAds` above and the two `verifyNoHouseAdsInIos…`
            // tasks are what stop that being a comment. `HouseAds`' runtime
            // `BuildInfo.isDebug` check stays: it is now the third layer here
            // rather than the only one.
            if (iosLinksHouseAds) {
                implementation(projects.libraries.ads.fake)
            }

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
            // `api`, not `implementation`, because `ApplicationConventionPlugin`
            // exports this to the iOS framework so `AdNetwork.swift` can read
            // `AdUnits`. Kotlin/Native refuses to export a dependency that is
            // not an API dependency.
            api(projects.libraries.ads)
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