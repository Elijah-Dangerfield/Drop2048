plugins {
    id("drop2048.feature")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.dangerfield.drop2048.features.settings.impl"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// L39: `captureRoboImage` is a no-op unless a flag is set, so a plain
// `testDebugUnitTest` runs every screenshot test, passes every one, and never
// compares a pixel. The project's standard verification command is fixed, so
// the harness has to be meaningful under it. Proven here the same way C2c and
// C3b proved theirs — a golden was swapped for a different image and the task
// went red.
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
            implementation(projects.features.settings)
            implementation(projects.features.achievements)
            implementation(projects.features.debug)
            implementation(projects.features.game)
            implementation(projects.features.home)
            implementation(projects.libraries.navigation)

            implementation(projects.libraries.ads)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.gameconfig)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.drop2048.storage)

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
            implementation(projects.features.settings)
            implementation(projects.libraries.ads)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.config)
            implementation(projects.libraries.gameconfig)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.core)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.drop2048.storage)
            implementation(projects.libraries.ui)
        }
    }
}
