plugins {
    id("drop2048.feature")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.dangerfield.drop2048.features.gate.impl"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// See `:features:settings:impl` and L39. Verification is on by default because
// the standard gate command is `testDebugUnitTest`, and Roborazzi compares
// nothing without it.
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
            implementation(projects.features.gate)

            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.gameconfig)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.drop2048)

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
            implementation(projects.features.gate)
            implementation(projects.libraries.ui)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            implementation(projects.libraries.gameconfig)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.drop2048)
        }
    }
}
