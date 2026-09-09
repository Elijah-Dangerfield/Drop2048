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
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            // RunRecordDao's supertype. Needed on the classpath even though this
            // module never names ClearableDao itself.
            implementation(projects.libraries.drop2048.storage)
        }
        commonTest.dependencies {
            implementation(projects.libraries.progress)
            implementation(projects.libraries.drop2048.storage)
            implementation(projects.libraries.flowroutines.testing)
        }
    }
}
