plugins {
    id("drop2048.kotlin.multiplatform")
}

android {
    namespace = "com.dangerfield.drop2048.libraries.storage.impl"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
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
            implementation(projects.libraries.progress)
            implementation(projects.libraries.achievements)
            implementation(libs.kotlinx.serialization.json)
        }

        androidUnitTest.dependencies {
            implementation(projects.libraries.progress)
            implementation(projects.libraries.achievements)
            implementation(projects.libraries.drop2048.storage)
            implementation(projects.libraries.flowroutines)
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

/**
 * The exported schemas are test input, not just build output.
 *
 * `AppDatabaseMigrationTest` builds a version 6 database from `6.json` and then
 * opens it through the production builder, so it needs the directory the Room
 * plugin writes into. Handing the path in from here rather than reconstructing
 * it in the test is what stops the two drifting if the schema location moves.
 */
tasks.withType<Test>().configureEach {
    systemProperty("drop2048.schemaDir", layout.projectDirectory.dir("schemas").asFile.absolutePath)
}

tasks.matching { it.name.contains("kspCommonMainKotlinMetadata", ignoreCase = true) }
    .configureEach { enabled = false }