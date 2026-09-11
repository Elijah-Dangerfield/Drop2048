plugins {
    id("drop2048.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.dangerfield.drop2048.libraries.ads.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.ads)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.core)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.storage)
            // SPEC 14's rewarded retry seam lives on the Daily's api, so the
            // binding for it lives here — the ad system is what can answer it.
            implementation(projects.libraries.progress)
            // The seven ad keys C7 wired, plus `ads.enabled`.
            implementation(projects.libraries.gameconfig)
            // ConfiguredValue is the supertype of every key and :gameconfig keeps
            // it internal to its own compilation, so calling one needs it here.
            implementation(projects.libraries.config)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(projects.libraries.ads)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.core)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.progress)
            implementation(projects.libraries.gameconfig)
            implementation(projects.libraries.config)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.google.play.services.ads)
            implementation(libs.google.ump)
        }
    }
}
