plugins {
    // Compose, for one declaration: `DevFeedback.Host()`. The floating button
    // and its panel have to be drawable from a module the release binary does
    // not contain, and a composable on the seam is what lets `App` wrap itself
    // in them without naming the implementation. Nothing here draws.
    // Applies the kotlinx-serialization plugin too, which `DevFeedbackFabState`
    // needs. That was an explicit `alias` here until the same missing plugin
    // crashed the iOS app on `AdState`; it now lives in the convention plugin so
    // no module has to remember. `FabStateSerializationTest` is what notices if
    // it ever comes back out.
    id("drop2048.compose.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.dangerfield.drop2048.libraries.devfeedback"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            api(projects.libraries.storage)
            implementation(compose.runtime)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.serialization.json)
        }

        androidUnitTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit)
        }
    }
}
