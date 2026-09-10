plugins {
    id("drop2048.feature")
}

android {
    namespace = "com.dangerfield.drop2048.features.achievements"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The badge copy lives here rather than in `impl` on purpose: the
            // stacked-out sheet in :features:game:impl needs a name for a badge
            // it just unlocked, and a feature impl may only see another
            // feature's api.
            implementation(projects.libraries.achievements)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.resources)

            implementation(compose.runtime)
            implementation(compose.components.resources)
        }

        commonTest.dependencies {
            implementation(projects.libraries.achievements)
            // AchievementCopyTest compares StringResources by key rather than
            // resolving them, which still needs the type on the test classpath.
            implementation(compose.components.resources)
        }
    }
}
