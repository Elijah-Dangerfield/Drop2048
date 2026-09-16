plugins {
    id("drop2048.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.dangerfield.drop2048.libraries.leaderboards.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.leaderboards)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            // SPEC 10's `feature.leaderboards` kill switch, read here rather
            // than at the call sites so that switching it off also stops
            // submissions and not just the entry point.
            implementation(projects.libraries.gameconfig)
            // `gameconfig` keeps `config` on `implementation`, so
            // `ConfiguredValue.invoke` — the operator that reads a flag — is not
            // on the compile classpath without this. The symptom is not a
            // missing import but `Unresolved reference 'not' for operator '!'`
            // on `!featureEnabled()`.
            implementation(projects.libraries.config)
        }

        commonTest.dependencies {
            implementation(projects.libraries.leaderboards)
            // RealLeaderboards implements AutoInit, so the supertype has to be
            // resolvable from the test classpath or nothing can construct one.
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.gameconfig)
            implementation(projects.libraries.config)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
