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
            implementation(projects.libraries.core)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.storage)
            // `pro.upsell.enabled` and `pro.price.tier`, wired by C7.
            implementation(projects.libraries.gameconfig)
            // ConfiguredValue is the supertype of both keys and :gameconfig keeps
            // it internal to its own compilation, so calling one needs it here.
            implementation(projects.libraries.config)
        }

        commonTest.dependencies {
            implementation(projects.libraries.billing)
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
