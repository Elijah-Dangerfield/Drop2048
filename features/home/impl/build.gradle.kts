plugins {
    id("drop2048.feature")
}

android {
    namespace = "com.dangerfield.drop2048.features.home.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.home)
            implementation(projects.libraries.navigation)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.drop2048)

            // Compose dependencies (navigation and lifecycle provided by drop2048.feature plugin)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        // One test, and it needs JVM reflection: `PlayerFeedbackSeamTest` pins
        // the shape of `FeedbackRepository` against a parameter being re-added
        // *with a default value*, which breaks no caller and is how the dead
        // `screenshots` parameter came back. Same reasoning as
        // `NoIdentitySeamsTest`, which is why it is not in commonTest.
        androidUnitTest.dependencies {
            implementation(libs.junit)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.core)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.ui)
        }
    }
}