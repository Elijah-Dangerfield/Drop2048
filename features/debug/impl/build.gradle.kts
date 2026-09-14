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

            // SPEC 19's "grant Pro". The flag is `ProGrant` in :libraries:billing
            // rather than a type this feature owns, because the Daily's second
            // attempt is decided in a library impl that cannot read a feature.
            implementation(projects.libraries.billing)
            // SPEC 19's force-show controls and the gate's own inputs. The api
            // module only: the house network lives in `:libraries:ads:fake`, and
            // a feature that named it would put it back into the release build
            // that `HouseAds` exists to keep it out of.
            implementation(projects.libraries.ads)
            // `ConfigOverrideRepository` at last has a writer. `:libraries:config`
            // rather than `:gameconfig` because the screen is over the whole
            // `Set<QaConfigValue>` and never names a key.
            implementation(projects.libraries.config)
            // The QA screen's one switch writes `DevFeedbackFabCache`. The api
            // module only: the button, the panel and the screenshot capture live
            // in `:libraries:devfeedback:tester`, which a release build does not
            // contain, and a feature that named it would undo that.
            implementation(projects.libraries.devfeedback)
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
            implementation(projects.libraries.billing)
            // SPEC 19's force-show controls and the gate's own inputs. The api
            // module only: the house network lives in `:libraries:ads:fake`, and
            // a feature that named it would put it back into the release build
            // that `HouseAds` exists to keep it out of.
            implementation(projects.libraries.ads)
            // `ConfigOverrideRepository` at last has a writer. `:libraries:config`
            // rather than `:gameconfig` because the screen is over the whole
            // `Set<QaConfigValue>` and never names a key.
            implementation(projects.libraries.config)
            implementation(projects.libraries.devfeedback)
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
