plugins {
    id("drop2048.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.dangerfield.drop2048.libraries.achievements.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.achievements)
            implementation(projects.libraries.progress)
        }
        commonTest.dependencies {
            implementation(projects.libraries.achievements)
            implementation(projects.libraries.progress)
            implementation(projects.libraries.flowroutines.testing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
