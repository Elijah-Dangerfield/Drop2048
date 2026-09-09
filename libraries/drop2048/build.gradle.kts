plugins {
    id("drop2048.kotlin.multiplatform")
}

moduleConfig {
    optIn("kotlin.time.ExperimentalTime")
}

android {
    namespace = "com.dangerfield.drop2048.libraries.drop2048"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            api(projects.libraries.storage)
            implementation(libs.configuration.annotations)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }

        androidMain.dependencies {
            implementation(libs.androidx.lifecycle.process)
        }
    }
}