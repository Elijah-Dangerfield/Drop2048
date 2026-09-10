plugins {
    id("drop2048.feature")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.dangerfield.drop2048.features.daily.impl"

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

// Compare by default, exactly as `:libraries:ui` and `:features:game:impl` do,
// and for the reason L39 records: `captureRoboImage` is a no-op unless a flag is
// set, so a plain `testDebugUnitTest` runs every screenshot test, passes every
// one of them, and never looks at a pixel. Verified here the way C2c verified it
// there — by swapping a golden for a different image and watching the task fail.
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
            implementation(projects.features.daily)
            implementation(projects.features.game)
            implementation(projects.libraries.navigation)

            implementation(projects.libraries.core)
            implementation(projects.libraries.progress)
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

        // The screen's screenshot harness. Android-only, and that is a limitation
        // worth stating rather than hiding: Roborazzi renders through
        // Robolectric, which is a JVM implementation of the *Android* framework,
        // so it can only ever capture the Android target.
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
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.progress)
            implementation(projects.libraries.core)
            implementation(projects.libraries.ui)
        }
    }
}
