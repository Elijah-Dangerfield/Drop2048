package com.dangerfield.drop2048.baselineprofile

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.STATS
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.ciText
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.describeScreen
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.launchIntent
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.reachMenu
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.visitDetailScreens
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the **minified** app through a real journey. This is the R8 smoke test.
 *
 * ## Why it is not a BaselineProfileRule test
 *
 * `BaselineProfileRule` refuses to run against a minified variant — it needs
 * unobfuscated output to write a profile against — so pointing it at
 * `benchmarkRelease` reports SKIPPED, which reads like a pass. A plain
 * instrumented test runs where the generators cannot. When checking this passed,
 * confirm the result XML says the test RAN; a skip is not a pass.
 *
 * ## Why it exists
 *
 * A release APK building cleanly says nothing about whether it works. R8 breaks
 * what is resolved *by name at runtime*, and a KMP app of this shape has three
 * of those: the `@Serializable` models (serializers are generated and reached
 * only through a `Companion`), the `@Serializable` navigation routes (renaming
 * one breaks type-safe nav with an argument error, not a missing-class error),
 * and the generated DI graph.
 *
 * Drop 2048 puts all three on the shortest journey it has. The tutorial writes a
 * `GameState` through its generated serializer on the first drop, every screen
 * this visits is a `@Serializable` route, and the graph is built before the
 * first frame. Stats reads Room.
 *
 * ## It shares the journey rather than copying it
 *
 * It used to keep its own copy of the walk, and the copy drifted: it was still
 * tapping "Continue as guest" after the identity stack was deleted, exactly like
 * the generators. The journey lives in [BenchmarkJourney] as `UiDevice`
 * extensions so that this test and the two generators cannot disagree about what
 * the app looks like.
 *
 * ```
 * ./gradlew :apps:baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class MinifiedReleaseSmokeTest {

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun theMinifiedAppReachesTheMenuAndNavigates() {
        device.pressHome()
        InstrumentationRegistry.getInstrumentation().context.startActivity(
            launchIntent().addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )

        check(device.wait(Until.hasObject(BenchmarkJourney.anyOnScreenText), LAUNCH_TIMEOUT_MS) == true) {
            "The minified app never rendered a first frame. " + device.describeScreen()
        }

        // Playing through the tutorial to the menu means the DI graph built, the
        // engine ran a cascade, and a GameState round-tripped through its
        // generated serializer — the first things R8 could have broken.
        device.reachMenu()

        // Each push resolves a @Serializable route by type. If R8 renamed one,
        // this fails with an argument error rather than a missing class, which
        // is the failure mode hardest to attribute in the wild.
        device.visitDetailScreens()

        check(device.wait(Until.hasObject(ciText(STATS)), SCREEN_TIMEOUT_MS) == true) {
            "Popping back to the menu failed. " + device.describeScreen()
        }
    }

    private companion object {
        const val LAUNCH_TIMEOUT_MS = 30_000L
        const val SCREEN_TIMEOUT_MS = 15_000L
    }
}
