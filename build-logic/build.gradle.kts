import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.dangerfield.drop2048.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.composeCompiler.gradlePlugin)
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    api(libs.ksp.gradlePlugin)
    api(libs.androidx.room.gradlePlugin)
    api(libs.kotlinx.serialization.gradlePlugin)
    api(libs.buildconfig.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kotlinMultiplatform") {
            id = "drop2048.kotlin.multiplatform"
            implementationClass = "com.dangerfield.drop2048.plugin.KotlinMultiplatformConventionPlugin"
        }
        register("composeMultiplatform") {
            id = "drop2048.compose.multiplatform"
            implementationClass = "com.dangerfield.drop2048.plugin.ComposeMultiplatformConventionPlugin"
        }
        register("feature") {
            id = "drop2048.feature"
            implementationClass = "com.dangerfield.drop2048.plugin.FeatureConventionPlugin"
        }
        register("application") {
            id = "drop2048.application"
            implementationClass = "com.dangerfield.drop2048.plugin.ApplicationConventionPlugin"
        }
    }
}