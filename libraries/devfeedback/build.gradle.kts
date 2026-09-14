plugins {
    // Compose, for one declaration: `DevFeedback.Host()`. The floating button
    // and its panel have to be drawable from a module the release binary does
    // not contain, and a composable on the seam is what lets `App` wrap itself
    // in them without naming the implementation. Nothing here draws.
    id("drop2048.compose.multiplatform")

    // `drop2048.compose.multiplatform` does **not** apply this, unlike
    // `drop2048.kotlin.multiplatform` and `drop2048.feature`. Without it
    // `@Serializable` on `DevFeedbackFabState` is an annotation and nothing
    // else: the module compiles, the app builds, and the first thing that
    // constructs the cache throws `Serializer for class 'DevFeedbackFabState'
    // is not found` on the main thread at boot. `FabStateSerializationTest`
    // is what notices next time.
    alias(libs.plugins.kotlinSerialization)
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
