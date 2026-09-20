plugins {
    id("drop2048.compose.multiplatform")
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.dangerfield.drop2048.libraries.navigation"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.flowroutines)
            api(libs.jetbrains.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }

        // Repeated from commonMain rather than inherited. The Android unit test
        // compilation is AGP's, not the Kotlin plugin's, and it does not see
        // `implementation` dependencies of the main source set the way the Apple
        // test compilations do; `bottomSheet`/`dialog` hand their content lambda
        // a `BottomSheetState`/`DialogState` from `:libraries:ui`, so resolving
        // either builder needs it on the classpath.
        commonTest.dependencies {
            implementation(projects.libraries.ui)
        }

        // Robolectric supplies one class, `android.net.Uri`, which a plain JVM
        // unit test gets as a throwing stub. `NavUri` is a typealias for it on
        // the Android target, so every deep-link assertion needs it.
        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
        }
    }
}