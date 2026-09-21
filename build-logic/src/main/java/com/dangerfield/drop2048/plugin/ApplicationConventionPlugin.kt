package com.dangerfield.drop2048.plugin

import com.android.build.api.dsl.ApplicationExtension
import com.dangerfield.drop2048.ext.ConfigurationExtension
import com.dangerfield.drop2048.util.SharedConstants
import com.dangerfield.drop2048.util.configureAndroid
import com.dangerfield.drop2048.util.configureComposeMetrics
import com.dangerfield.drop2048.util.configureKotlinInject
import com.dangerfield.drop2048.util.configureKotlinMultiplatform
import com.dangerfield.drop2048.util.configureReleaseSigning
import com.dangerfield.drop2048.util.enforceModuleBoundaries
import com.dangerfield.drop2048.util.libs
import com.dangerfield.drop2048.util.verifyGitHooksInstalled
import com.dangerfield.drop2048.util.loadVersionMetadata
import com.dangerfield.drop2048.util.optInKotlinMarkers
import com.dangerfield.drop2048.util.VersionMetadata
import com.dangerfield.drop2048.util.writeCommonMetadata
import com.github.gmazzo.buildconfig.BuildConfigExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention plugin for the main Android application module.
 *
 * **When to use this plugin:**
 * - The main app module that gets installed on devices
 * - The module that contains MainActivity and app-level configuration
 * - The module that defines the applicationId and app metadata
 *
 * **What this plugin provides:**
 * - Android application plugin configuration
 * - Kotlin Multiplatform setup with Android and iOS targets
 * - Compose and Compose Compiler plugins
 * - iOS framework configuration for KMP
 * - Application-specific build configuration (version codes, signing, etc.)
 * - Activity Compose dependencies
 *
 * **Examples of modules that should use this:**
 * - apps:compose (your main app)
 * - apps:desktop (if you have a desktop app variant)
 *
 * **Don't use this plugin for:**
 * - Feature modules (use drop2048.feature instead)
 * - Library modules (use drop2048.compose.multiplatform or drop2048.kotlin.multiplatform)
 * - Server modules (these wouldn't be Android applications)
 */
class ApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val versionMetadata = loadVersionMetadata()
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.application")
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply(libs.plugins.kotlinSerialization.get().pluginId)
                apply(libs.plugins.buildconfig.get().pluginId)
            }

            configureComposeMetrics()

            project.optInKotlinMarkers("kotlin.time.ExperimentalTime")
            project.optInKotlinMarkers("kotlin.uuid.ExperimentalUuidApi")

            configureKotlinMultiplatform {
                binaries.framework {
                    baseName = "ComposeApp"
                    isStatic = true
                    binaryOption("bundleId", "com.dangerfield.drop2048")
                    export(project(":libraries:core"))
                    // Swift reads `AdUnits` directly: the ad unit id lives in
                    // one file on purpose, so the iOS ad code asks Kotlin for it
                    // rather than keeping a second copy that could drift to a
                    // test id while Kotlin held the real one. Kotlin/Native only
                    // exports declarations reachable from the framework's API,
                    // and nothing in Kotlin reads `AdUnits` on iOS, so without
                    // this the `AdUnits.shared.ios(format:)` calls in
                    // `AdNetwork.swift` do not resolve.
                    export(project(":libraries:ads"))
                }
            }
            configureKotlinInject()

            extensions.configure<ApplicationExtension> {
                configureAndroid()

                defaultConfig {
                    applicationId = versionMetadata.applicationId
                    targetSdk = SharedConstants.targetSdk
                    versionCode = versionMetadata.versionCode
                    versionName = versionMetadata.versionName
                }

                packaging {
                    resources {
                        excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    }
                }

                val releaseSigning = configureReleaseSigning(this)

                buildTypes {
                    debug {
                        applicationIdSuffix = ".debug"
                    }
                    release {
                        // R8 on. Play flags an app under 25% obfuscation as
                        // below its threshold with a Feb 2027 deadline, and the
                        // size and startup wins come with it. The keep rules in
                        // apps/compose/proguard-rules.pro are what make this
                        // survive: R8 breaks whatever is resolved by name at
                        // runtime, and every failure mode is at runtime, so a
                        // release build that merely compiles proves nothing.
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                        signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
                    }
                }
            }

            if (extensions.findByName("moduleConfig") == null) {
                extensions.create("moduleConfig", ConfigurationExtension::class.java)
            }
            configureAppBuildConfig(versionMetadata)

            verifyGitHooksInstalled()
            enforceModuleBoundaries()
        }
    }

    private fun Project.configureAppBuildConfig(metadata: VersionMetadata) {
        extensions.configure(BuildConfigExtension::class.java) {
            packageName("${metadata.applicationId}.appconfig")
            className("AppBuildConfig")
            useKotlinOutput {
                internalVisibility = false
            }
            writeCommonMetadata(metadata)
        }
    }
}