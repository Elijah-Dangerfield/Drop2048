plugins {
    id("drop2048.kotlin.multiplatform")
}

android {
    namespace = "com.dangerfield.drop2048.libraries.progress"
}

moduleConfig.storage()

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.storage)
            // ClearableDao. Every table that holds player data joins the wipe set
            // by implementing it, so "reset progress" never needs a list edit.
            implementation(projects.libraries.drop2048.storage)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
