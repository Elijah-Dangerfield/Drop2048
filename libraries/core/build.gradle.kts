plugins {
    id("drop2048.kotlin.multiplatform")
}

android {
    namespace = "com.dangerfield.drop2048.libraries.core"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.kotlin.inject.runtime.kmp)
        }
    }
}