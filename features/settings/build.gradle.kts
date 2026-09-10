plugins {
    id("drop2048.feature")
}

android {
    namespace = "com.dangerfield.drop2048.features.settings"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
