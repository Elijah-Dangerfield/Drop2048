plugins {
    id("drop2048.feature")
}

android {
    namespace = "com.dangerfield.drop2048.features.game"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.navigation)
            // GameMode is a GameRoute argument, so it is api: anything that
            // navigates to a run has to be able to name the mode it wants.
            api(projects.libraries.progress)

            implementation(compose.runtime)
        }
    }
}
