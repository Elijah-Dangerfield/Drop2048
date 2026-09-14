plugins {
    id("drop2048.compose.multiplatform")
    alias(libs.plugins.roborazzi)
}

moduleConfig {
    di()
}

android {
    namespace = "com.dangerfield.drop2048.libraries.ads.fake"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// L39/L60: `captureRoboImage` is a no-op unless verification is switched on, so
// a plain `testDebugUnitTest` would run every screenshot test, pass every one,
// and never compare a pixel. Verify by default; stand down only for recording.
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
            implementation(projects.libraries.ads)
            implementation(projects.libraries.core)
            implementation(projects.libraries.ui)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
        }

        androidUnitTest.dependencies {
            // The screenshot harness draws the real surface, so it needs the
            // same compose artifacts commonMain does. An androidUnitTest source
            // set does not inherit a commonMain `implementation` here.
            implementation(projects.libraries.ads)
            implementation(projects.libraries.ui)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)

            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.roborazzi)
            implementation(libs.roborazzi.compose)
            implementation(libs.roborazzi.junit.rule)
            implementation(libs.androidx.compose.ui.test.junit4.android)
            implementation(libs.androidx.compose.ui.test.manifest)
        }

        commonTest.dependencies {
            implementation(projects.libraries.ads)
            implementation(projects.libraries.core)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
