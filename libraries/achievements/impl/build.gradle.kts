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
            // AchievementPlatformSync only. This is the one direction the
            // achievements/leaderboards coupling can run without putting Room
            // underneath a platform shim — see that file.
            implementation(projects.libraries.leaderboards)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
        }
        commonTest.dependencies {
            implementation(projects.libraries.achievements)
            implementation(projects.libraries.progress)
            implementation(projects.libraries.leaderboards)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.flowroutines.testing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
