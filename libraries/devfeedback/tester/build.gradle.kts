plugins {
    id("drop2048.compose.multiplatform")
    alias(libs.plugins.roborazzi)
}

moduleConfig {
    di()
}

android {
    namespace = "com.dangerfield.drop2048.libraries.devfeedback.tester"

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
            implementation(projects.libraries.devfeedback)
            implementation(projects.libraries.core)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.ui)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.coroutines.core)
        }

        androidUnitTest.dependencies {
            // The screenshot harness draws the real button and the real panel,
            // so it needs the same compose artifacts commonMain does. An
            // androidUnitTest source set does not inherit a commonMain
            // `implementation` here.
            implementation(projects.libraries.devfeedback)
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
            implementation(projects.libraries.devfeedback)
            implementation(projects.libraries.core)
            // The reporter's whole job is choosing a FeedbackKind, so a test of
            // it has to be able to name one. A commonMain `implementation` does
            // not reach this source set.
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.flowroutines.testing)
            implementation(compose.ui)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
