plugins {
    id("drop2048.kotlin.multiplatform")
}

android {
    namespace = "com.dangerfield.drop2048.libraries.achievements"
}

moduleConfig.storage()

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The explicit storage dependency is load-bearing on iOS:
            // `moduleConfig.storage()` adds it to the project-level
            // `implementation` configuration, which the Android target picks up
            // and the Kotlin/Native one does not.
            implementation(projects.libraries.storage)
            // ClearableDao is deliberately *not* implemented by this module's
            // DAO — see AchievementDao — but `run_record`'s mode enum is in the
            // progress api and a fact is keyed on it.
            api(projects.libraries.progress)
            // The engine, for the transcript a run's facts are read out of and
            // for the scoring coefficients the score ladder is derived from. It
            // has zero dependencies of its own, so this adds nothing else.
            api(projects.libraries.cascade)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
        }
    }
}
