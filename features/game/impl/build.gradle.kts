plugins {
    id("drop2048.feature")
}

android {
    namespace = "com.dangerfield.drop2048.features.game.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.game)
            implementation(projects.libraries.navigation)

            implementation(projects.libraries.cascade)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.drop2048)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.cascade)
            implementation(projects.libraries.core)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.ui)
        }
    }
}
