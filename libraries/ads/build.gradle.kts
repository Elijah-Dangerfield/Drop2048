plugins {
    // Compose, for one declaration: `HouseAds.Surface()`. The house placeholder
    // has to be drawable from a module the release binary does not contain, and
    // a composable on the seam is what lets `App` call it without naming the
    // implementation. Nothing else here draws.
    id("drop2048.compose.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.dangerfield.drop2048.libraries.ads"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            implementation(compose.runtime)
            // `BannerSurface.Banner` takes a `Modifier`: the caller decides where
            // the strip sits, this module only decides what goes in it.
            implementation(compose.ui)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
