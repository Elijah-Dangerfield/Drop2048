package com.dangerfield.drop2048.plugin

import com.android.build.gradle.LibraryExtension
import com.dangerfield.drop2048.ext.ConfigurationExtension
import com.dangerfield.drop2048.util.configureAndroid
import com.dangerfield.drop2048.util.configureComposeMetrics
import com.dangerfield.drop2048.util.configureKotlinInject
import com.dangerfield.drop2048.util.configureKotlinMultiplatform
import com.dangerfield.drop2048.util.enforceModuleBoundaries
import com.dangerfield.drop2048.util.libs
import com.dangerfield.drop2048.util.optInKotlinMarkers
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Convention plugin for library modules that need Compose UI capabilities.
 * 
 * **When to use this plugin:**
 * - UI component libraries that provide reusable Compose components
 * - Libraries that contain Compose UI utilities and extensions
 * - Any library module that needs to create or manipulate Compose UI
 * - Modules that provide design system components
 * 
 * **What this plugin provides:**
 * - Full Kotlin Multiplatform setup (Android, iOS, JVM targets)
 * - Compose Multiplatform and Compose Compiler plugins
 * - Android library configuration
 * - Common KMP dependencies (coroutines, kotlin-test)
 * - Compose runtime test dependencies
 * 
 * **Examples of modules that should use this:**
 * - libraries:ui (your UI component library)
 * - libraries:design-system (if you have one)
 * - Any library that exports @Composable functions
 * 
 * **Don't use this plugin for:**
 * - Feature modules with screens and navigation (use drop2048.feature instead)
 * - Pure Kotlin libraries without UI (use drop2048.kotlin.multiplatform instead)
 * - The main application module (use drop2048.android.application instead)
 */
class ComposeMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.library")
                apply("org.jetbrains.compose")
                apply("org.jetbrains.kotlin.plugin.compose")

                // The other three convention plugins have always applied this.
                // This one not doing so was a trap rather than a decision: a
                // `@Serializable` class in a compose-convention module compiles
                // to an annotation and nothing else, the whole app builds green,
                // and the first object to construct a cache throws `Serializer
                // for class 'X' is not found` on the main thread at boot. It
                // caught DevFeedbackFabState, and then AdState. The plugin costs
                // nothing in a module with no `@Serializable` in it.
                apply(libs.plugins.kotlinSerialization.get().pluginId)
            }

            project.optInKotlinMarkers("kotlin.time.ExperimentalTime")
            project.optInKotlinMarkers("kotlin.uuid.ExperimentalUuidApi")

            configureKotlinMultiplatform()
            configureKotlinInject()
            configureComposeTestDependencies()
            configureComposeMetrics()
            
            extensions.configure<LibraryExtension> {
                configureAndroid()
            }

            if (extensions.findByName("moduleConfig") == null) {
                extensions.create("moduleConfig", ConfigurationExtension::class.java)
            }

            enforceModuleBoundaries()
        }
    }
    
    private fun Project.configureComposeTestDependencies() {
        // Add compose runtime dependency for test source sets to satisfy compose compiler
        dependencies {
            add("commonTestImplementation", "org.jetbrains.compose.runtime:runtime:1.9.1")
        }
    }
    

}