plugins {
    id("drop2048.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.dangerfield.drop2048.libraries.billing"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            // `Entitlements.isPro` is a StateFlow in the public signature, so
            // every consumer has to be able to name the type.
            api(libs.kotlinx.coroutines.core)
        }
    }
}
