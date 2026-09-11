plugins {
    id("drop2048.feature")
}

android {
    namespace = "com.dangerfield.drop2048.features.paywall"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.navigation)

            implementation(compose.runtime)
        }
    }
}
