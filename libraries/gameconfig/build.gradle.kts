plugins {
    id("drop2048.kotlin.multiplatform")
}

android {
    namespace = "com.dangerfield.drop2048.libraries.gameconfig"
}

moduleConfig {
    di()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            // SPEC 10's gameplay keys are exactly `EngineConfig`'s fields, so
            // this module deserializes straight into the engine's own value
            // types rather than mirroring them.
            api(projects.libraries.cascade)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
        }
    }
}
