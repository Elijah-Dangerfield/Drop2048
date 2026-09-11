plugins {
    id("drop2048.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.dangerfield.drop2048.libraries.ads"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
        }
    }
}
