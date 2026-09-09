plugins {
    id("drop2048.kotlin.multiplatform")
}

android {
    namespace = "com.dangerfield.drop2048.libraries.drop2048.storage"
}

moduleConfig.storage()


kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.storage)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}