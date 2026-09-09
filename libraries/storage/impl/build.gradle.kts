plugins {
    id("drop2048.kotlin.multiplatform")
}

android {
    namespace = "com.dangerfield.drop2048.libraries.storage.impl"
}

moduleConfig.storage()

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.storage)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.drop2048)
            implementation(projects.libraries.drop2048.storage)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

tasks.matching { it.name.contains("kspCommonMainKotlinMetadata", ignoreCase = true) }
    .configureEach { enabled = false }