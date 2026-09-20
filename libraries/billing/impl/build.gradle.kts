plugins {
    id("drop2048.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.dangerfield.drop2048.libraries.billing.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.billing)
            // `AdImpressions`: the stacked-out card claims "Tired of the ads?",
            // so it may not be drawn before there have been any (D28). The api
            // module only, because only `:apps:*` may depend on an impl.
            implementation(projects.libraries.ads)
            implementation(projects.libraries.core)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.storage)
            // `pro.upsell.enabled`.
            implementation(projects.libraries.gameconfig)
            // ConfiguredValue is the key's supertype and :gameconfig keeps it
            // internal to its own compilation, so calling one needs it here.
            implementation(projects.libraries.config)
        }

        commonTest.dependencies {
            implementation(projects.libraries.billing)
            implementation(projects.libraries.ads)
            implementation(projects.libraries.core)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.gameconfig)
            implementation(projects.libraries.config)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.android.billing)
            implementation(libs.android.billing.ktx)
        }
    }
}
