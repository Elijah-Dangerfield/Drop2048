plugins {
    id("drop2048.compose.multiplatform")
}

android {
    namespace = "com.dangerfield.drop2048.libraries.resources"
}

compose.resources {
    publicResClass = true
    generateResClass = auto
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)

            api(compose.components.resources)
            api(compose.ui)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.components.resources)
            api(compose.components.uiToolingPreview)
            api(compose.materialIconsExtended)
            api(compose.material3AdaptiveNavigationSuite)
        }
    }
}