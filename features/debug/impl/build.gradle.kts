plugins {
    id("drop2048.feature")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.dangerfield.drop2048.features.debug.impl"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// L39: `captureRoboImage` does nothing unless verification is on, so a plain
// `testDebugUnitTest` runs every screenshot test, passes every one and never
// compares a pixel. Proven here the way every other module proved it — a golden
// was replaced with a different image and the task went red.
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
            implementation(projects.features.debug)
            implementation(projects.features.game)
            implementation(projects.features.settings)
            implementation(projects.libraries.navigation)

            implementation(projects.libraries.cascade)
            implementation(projects.libraries.core)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.drop2048.storage)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.progress)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.ui)

            implementation(libs.kotlinx.serialization.json)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

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
            implementation(projects.features.debug)
            implementation(projects.libraries.cascade)
            implementation(projects.libraries.core)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.drop2048.storage)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.progress)
            implementation(projects.libraries.ui)
        }
    }
}
