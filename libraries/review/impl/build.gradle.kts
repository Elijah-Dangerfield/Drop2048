plugins {
    id("drop2048.kotlin.multiplatform")
}

moduleConfig {
    di()
}

android {
    namespace = "com.dangerfield.drop2048.libraries.review.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.review)
            implementation(projects.libraries.core)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.storage)
        }
        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.review)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.storage)
        }

        androidMain.dependencies {
            implementation(libs.google.play.review)
            implementation(libs.google.play.review.ktx)
        }
    }
}
