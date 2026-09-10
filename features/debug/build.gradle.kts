plugins {
    id("drop2048.feature")
}

android {
    namespace = "com.dangerfield.drop2048.features.debug"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.navigation)

            // The engine, because every seam here is expressed in its types: a
            // preset board is a `Board`, a forced block is a `Block`, and a
            // replayed run is a seed. Nothing here re-describes the game.
            api(projects.libraries.cascade)
            implementation(projects.libraries.progress)

            implementation(compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
