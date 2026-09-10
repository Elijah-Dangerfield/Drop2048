plugins {
    id("drop2048.feature")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.dangerfield.drop2048.features.achievements.impl"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// L39: `captureRoboImage` is a no-op unless a flag is set, so a plain
// `testDebugUnitTest` runs every screenshot test, passes every one, and never
// compares a pixel. The project's standard verification command is fixed, so the
// harness has to be meaningful under it. Proven here the way C2c, C3b and C11
// proved theirs — a golden was swapped for a different image and the task went
// red naming this test.
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
            implementation(projects.features.achievements)

            implementation(projects.libraries.achievements)
            implementation(projects.libraries.leaderboards)
            implementation(projects.libraries.core)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.resources)

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
            implementation(projects.features.achievements)
            implementation(projects.libraries.achievements)
            implementation(projects.libraries.leaderboards)
            implementation(projects.libraries.core)
            // The screenshot harness lives in androidUnitTest, and a commonMain
            // `implementation` dependency is not on a test compilation's
            // classpath — it has to be named again here.
            implementation(projects.libraries.ui)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(projects.libraries.flowroutines.testing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
