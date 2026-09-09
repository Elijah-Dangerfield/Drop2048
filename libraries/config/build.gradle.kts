plugins {
    id("drop2048.kotlin.multiplatform")
}

android {
    namespace = "com.dangerfield.drop2048.libraries.config"
}


kotlin {
    sourceSets {
        commonMain.dependencies {

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(libs.kotlin.inject.runtime.kmp)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}