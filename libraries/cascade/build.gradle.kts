plugins {
    id("drop2048.kotlin.multiplatform")
}

android {
    namespace = "com.dangerfield.drop2048.libraries.cascade"
}

// The JVM target exists so `tools/balance` (C1a) can play the shipped engine
// headless — the same code that balances the spawn table is the code that runs
// on device, so there is no second implementation to drift.
//
// Nothing from `:libraries:*` is depended on here, deliberately. SPEC 4.1 makes
// the engine a value-in/value-out state machine with no coroutines, no clock and
// no logging; the only thing on the compile classpath is the serialization
// runtime that `@Serializable` needs, and it is `api` rather than
// `implementation` because `GameState` is part of this module's public surface.
kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.core)
        }

        // Test-only. The determinism test has to compare *bytes*, not `equals`,
        // because what Daily Challenge and a replayed bug report actually depend
        // on is the serialized form round-tripping identically. Nothing in
        // commonMain knows JSON exists.
        commonTest.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
