plugins {
    id("drop2048.feature")
}

android {
    namespace = "com.dangerfield.drop2048.features.daily"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.navigation)

            implementation(compose.runtime)
        }
    }
}
