plugins {
    id("drop2048.feature")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.dangerfield.drop2048.features.paywall.impl"

    // Robolectric needs the module's real resources and assets on the classpath.
    // Compose Multiplatform packages `composeResources` — including the bundled
    // Fredoka and Nunito that `:libraries:ui` ships — as Android assets, so
    // without this every screenshot renders in the platform fallback face and the
    // goldens are of a design nobody shipped.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Compare by default, for the reason L39 and L60 record between them:
// `captureRoboImage` is a no-op unless a flag is set, and even with the flag the
// goldens are not declared task inputs, so a swapped PNG leaves the task
// UP-TO-DATE. Proving the verifier runs here needs `--rerun`, and that is how it
// was proved.
val recordingGoldens = gradle.startParameter.taskNames.any {
    it.contains("recordRoborazzi", ignoreCase = true)
}

if (!recordingGoldens) {
    tasks.withType<Test>().configureEach {
        systemProperty("roborazzi.test.verify", "true")
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.paywall)
            implementation(projects.libraries.navigation)

            implementation(projects.libraries.billing)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        // Android-only, and that is a limitation worth stating rather than
        // hiding: Roborazzi renders through Robolectric, a JVM implementation of
        // the *Android* framework, so it can only ever capture the Android
        // target.
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit.rule)
            implementation(libs.androidx.compose.ui.test.junit4.android)
            implementation(libs.androidx.compose.ui.test.manifest)
        }

        commonTest.dependencies {
            implementation(projects.libraries.billing)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.ui)

            // Repeated from commonMain rather than inherited. The Android unit
            // test compilation is AGP's, not the Kotlin plugin's, and it does not
            // see `implementation` dependencies of the main source set the way
            // the Apple test compilations do. `PaywallGraphTest` compiles for
            // iOS and fails to resolve `Route` for Android without these.
            implementation(projects.features.paywall)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.resources)
        }
    }
}
