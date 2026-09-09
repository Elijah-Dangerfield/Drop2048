plugins {
    id("drop2048.kotlin.multiplatform")
}

android {
    namespace = "com.dangerfield.drop2048.libraries.review"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
