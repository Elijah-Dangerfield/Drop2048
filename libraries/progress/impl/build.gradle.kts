plugins {
    id("drop2048.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.dangerfield.drop2048.libraries.progress.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.progress)
            // SPEC 12's second Daily attempt. The one `Entitlements` every part
            // of the app now reads, C10.
            implementation(projects.libraries.billing)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            // RunRecordDao's supertype. Needed on the classpath even though this
            // module never names ClearableDao itself.
            implementation(projects.libraries.drop2048.storage)
            // SPEC 10's `feature.dailyChallenge` kill switch and the rewarded
            // Daily retry cap. Both keys already exist here with compiled-in
            // defaults; the Daily is their first consumer.
            implementation(projects.libraries.gameconfig)
            // ConfiguredValue is the supertype of both keys and :gameconfig keeps
            // it internal to its own compilation, so calling one needs it here.
            implementation(projects.libraries.config)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(projects.libraries.progress)
            implementation(projects.libraries.billing)
            implementation(projects.libraries.drop2048.storage)
            implementation(projects.libraries.gameconfig)
            implementation(projects.libraries.config)
            implementation(projects.libraries.flowroutines.testing)
            implementation(libs.kotlinx.datetime)
        }
    }
}
