plugins {
    id("drop2048.feature")
}

android {
    namespace = "com.dangerfield.drop2048.features.gate"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)

            implementation(compose.runtime)
        }
    }
}
