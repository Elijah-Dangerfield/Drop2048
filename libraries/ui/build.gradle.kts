plugins {
    id("drop2048.compose.multiplatform")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.dangerfield.drop2048.libraries.ui"

    // Robolectric needs the module's real resources and assets on the classpath.
    // Compose Multiplatform packages `composeResources` — including the bundled
    // Fredoka and Nunito — as Android assets, so without this every screenshot
    // renders in the platform fallback face and the goldens are of a design
    // nobody shipped.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Make the screenshot tests compare by default, rather than only under
// `verifyRoborazziDebug`.
//
// Out of the box Roborazzi's `captureRoboImage` is a no-op unless a flag is set,
// so a plain `testDebugUnitTest` runs every screenshot test, passes every one of
// them, and never looks at a pixel. That was measured, not assumed: a golden was
// swapped for a different image and the task stayed green. A suite that is green
// because it is not running is worse than no suite, because it is also a claim.
//
// The project's standard verification command is `testDebugUnitTest` and three
// other tasks, and it is fixed, so the harness has to be meaningful under that
// command or it will not be run. Verify is therefore the default and is only
// stood down for the record task, which has to be allowed to write.
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

        androidMain.dependencies {
            api(compose.preview)
            api(compose.uiTooling)
        }

        // The screenshot harness. Android-only, and that is a limitation worth
        // stating rather than hiding: Roborazzi renders through Robolectric,
        // which is a JVM implementation of the *Android* framework, so it can
        // only ever capture the Android target. See ScreenshotTest's KDoc for
        // what that does and does not prove about iOS.
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit.rule)
            implementation(libs.androidx.compose.ui.test.junit4.android)
            implementation(libs.androidx.compose.ui.test.manifest)
        }

        commonMain.dependencies {
            implementation(projects.libraries.core)
            // TODO honestly the drop2048 library should expose the component that require drop2048 domain
            implementation(projects.libraries.drop2048)

            api(compose.ui)
            api(compose.uiUtil)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.components.resources)
            api(compose.components.uiToolingPreview)
            api(compose.materialIconsExtended)
            api(compose.material3AdaptiveNavigationSuite)
            api(libs.compose.backhandler)

            api(libs.compottie)
            api(libs.compottie.resources)
            api(libs.compottie.dot)
            api(libs.compottie.lite)
            api(libs.compottie.network)
        }
    }
}
