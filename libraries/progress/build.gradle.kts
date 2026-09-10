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
            // LocalDate is in DailyResult's signature, so it is api rather than
            // implementation: every consumer of the daily has to be able to name
            // the type the day is keyed on.
            api(libs.kotlinx.datetime)
            // GameMode is @Serializable: it is a field of the saved-run blob and
            // a GameRoute argument. See its KDoc.
            api(libs.kotlinx.serialization.core)
        }
    }
}
