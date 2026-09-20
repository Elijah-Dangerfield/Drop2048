package com.dangerfield.drop2048.baselineprofile

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.anyOnScreenText
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.describeScreen
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.launchIntent
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.reachMenu
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.ACHIEVEMENTS
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.AD_GATE_INPUTS
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.COOLDOWN_PATH
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.GO_PRO
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.MAINTENANCE_BANNER
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.MAINTENANCE_MESSAGE_PATH
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.MAINTENANCE_MODE_PATH
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.PAYWALL_ANCHOR
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.PAYWALL_RESTORE
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.achievementsSummary
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.anyRestoreAnswer
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.awaitMatching
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.awaitText
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.clearOverrides
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.openConfigOverrides
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.openDebugMenu
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.openSettings
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.scrollToRow
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.setOverride
import com.dangerfield.drop2048.baselineprofile.SurfaceJourney.tapRow
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The R8 smoke test for everything [MinifiedReleaseSmokeTest]'s one journey does
 * not touch.
 *
 * C13a proved R8 needs no extra keep rules for the tutorial, a cascade, three
 * routes and a Room screen, and said plainly what that did **not** cover: ads,
 * billing, the paywall, sharing, leaderboards, achievements, the launch gates and
 * remote config. This walks the ones a device can reach. What it cannot reach is
 * named below rather than quietly dropped.
 *
 * ## The assertion that carries the chunk
 *
 * [aConfigOverrideSurvivesARelaunchAndRaisesItsGate] types a string that **does
 * not exist anywhere in the binary** into `upgrade.maintenanceMessage`, force
 * stops the app, launches it cold, and asserts that string is on the first
 * screen. For that to pass, the value has to be written through
 * `ConfigCacheSnapshot`'s generated serializer, survive a process boundary, be
 * read back through the same serializer, be decoded by `ConfigJsonConverter`,
 * resolve through a `StringConfigValue`, reach `resolveLaunchGates`, and be drawn
 * by the gate host. A keep rule missing anywhere on that chain and the sentinel
 * never appears.
 *
 * That is deliberately not an assertion that a screen opened. A screen opening
 * proves a route survived; a value the APK has never seen appearing on it proves
 * the serializer did.
 *
 * ## What no instrumented test can reach here, and why
 *
 * - **Buying anything.** Play Billing needs a licensed tester account and a
 *   published product. The paywall test asserts the store *answered* instead,
 *   which is the half R8 can break: a renamed listener leaves the button pressed
 *   and the screen silent. On a bare emulator the answer is "could not reach the
 *   store", and that is still an answer.
 * - **A filled ad.** A release build has no house network by design (C15, and
 *   `apps/compose/build.gradle.kts` fails the build if that changes), so every
 *   ad goes to AdMob, which needs network and fill. What is asserted instead is
 *   the gate readout, which is computed from the `@Serializable` `AdState` cache
 *   and the config values, and is the part of the ad layer that is this app's
 *   code rather than Google's.
 * - **Leaderboards.** `Leaderboards.isOfferable` is false without a signed-in
 *   Play Games account, so the row is not drawn at all and there is nothing to
 *   tap. The achievements grid that would host it is covered;
 *   `AchievementPlatformSync` is not.
 * - **Sharing.** It launches the system chooser, which is another app's UI, and
 *   `:libraries:sharing` carries no `@Serializable` model, so the hazard this
 *   file exists for is not present there. Its R8 surface is `Intent` extras.
 *
 * ```
 * ./gradlew :apps:baselineprofile:connectedBenchmarkReleaseAndroidTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class MinifiedReleaseSurfacesTest {

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * The paywall sheet, and the store being asked a question.
     *
     * `PaywallRoute` is a `@Serializable` route registered with `bottomSheet<>`,
     * which resolves by type like every other route, so the sheet appearing is
     * the same proof [MinifiedReleaseSmokeTest] takes from Stats.
     * The restore is the part that is not navigation: it crosses into Play
     * Billing and comes back.
     */
    @Test
    fun theMinifiedAppOpensThePaywallAndTheStoreAnswers() {
        device.launchCold()
        device.reachMenu()
        device.openSettings()

        device.tapRow(GO_PRO)
        check(device.awaitText(PAYWALL_ANCHOR)) {
            "Tapped $GO_PRO but the paywall sheet never appeared. " + device.describeScreen()
        }

        device.tapRow(PAYWALL_RESTORE)
        check(device.awaitMatching(anyRestoreAnswer, STORE_TIMEOUT_MS)) {
            "The store never answered a restore. " + device.describeScreen()
        }
    }

    /**
     * The achievements grid, which C13a gave up on after two runs.
     *
     * The summary line is the anchor rather than the title, because the title is
     * also the settings row that opened it. "N of M earned" is rendered from the
     * Room-backed counters, so a grid that drew with the row still unresolved
     * would not produce it.
     */
    @Test
    fun theMinifiedAppDrawsTheAchievementsGrid() {
        device.launchCold()
        device.reachMenu()
        device.openSettings()

        device.tapRow(ACHIEVEMENTS)
        check(device.awaitMatching(achievementsSummary)) {
            "Tapped $ACHIEVEMENTS but the grid never drew its summary. " + device.describeScreen()
        }
    }

    /**
     * Remote config, the ad gate and the launch gates, in one walk.
     *
     * Three assertions, in increasing strength:
     *
     * 1. The ad gate readout is drawn at all, which means `AdState` was read back
     *    out of its own persistent cache.
     * 2. An override of `upgrade.maintenanceMessage` survives a force stop and
     *    raises a banner carrying its exact text, which is the serializer round
     *    trip and the launch gates.
     * 3. An override of `ads.interstitial.cooldownSeconds` is in that same
     *    relaunched process's ad gate readout, which is the typed `IntConfigValue`
     *    path landing in the ad layer rather than only in the screen that set it.
     *
     * The cooldown is checked **after** the relaunch rather than immediately, and
     * a run spent finding out why: an override is written to disk straight away
     * and the gate that reads it does not necessarily re-resolve mid-process. A
     * check that only holds after a relaunch is a weaker claim about caching and
     * a stronger one about persistence, which is the claim this file is for.
     *
     * It clears up after itself because overrides are persisted, and a test that
     * left a maintenance banner behind would change what every later run sees.
     */
    @Test
    fun aConfigOverrideSurvivesARelaunchAndRaisesItsGate() {
        device.launchCold()
        device.reachMenu()
        device.openSettings()
        device.openDebugMenu()
        device.scrollToRow(AD_GATE_INPUTS)

        device.openConfigOverrides()
        device.setOverride(COOLDOWN_PATH, SENTINEL_COOLDOWN, expectedCount = 1)
        device.setOverride(MAINTENANCE_MESSAGE_PATH, SENTINEL_MESSAGE, expectedCount = 2)
        device.setOverride(MAINTENANCE_MODE_PATH, MAINTENANCE_BANNER, expectedCount = THREE_OVERRIDES)

        device.launchCold()
        check(device.awaitText(SENTINEL_MESSAGE, LAUNCH_TIMEOUT_MS)) {
            "A config override did not survive a cold launch, or the gate it " +
                "raises did not draw. " + device.describeScreen()
        }

        device.reachMenu()
        device.openSettings()
        device.openDebugMenu()
        device.scrollToRow(cooldownReadout())

        device.openConfigOverrides()
        device.clearOverrides()

        device.launchCold()
        check(!device.awaitText(SENTINEL_MESSAGE, BANNER_SETTLE_MS)) {
            "Clearing the overrides left the maintenance banner up. " + device.describeScreen()
        }
    }

    /**
     * Starts the app from nothing, as a player's launcher tap does.
     *
     * A force stop rather than only `CLEAR_TASK`, because the point of the third
     * test is a **process** boundary: an activity relaunched into a warm process
     * reads config out of memory and would pass with the serializer in pieces.
     */
    private fun UiDevice.launchCold() {
        pressHome()
        executeShellCommand("am force-stop ${BenchmarkJourney.PACKAGE}")
        waitForIdle()
        InstrumentationRegistry.getInstrumentation().context.startActivity(
            launchIntent().addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )
        check(wait(Until.hasObject(anyOnScreenText), LAUNCH_TIMEOUT_MS) == true) {
            "The minified app never rendered a first frame. " + describeScreen()
        }
    }

    private fun cooldownReadout(): String = "cooldown remaining: 0s of ${SENTINEL_COOLDOWN}s"

    private companion object {
        const val LAUNCH_TIMEOUT_MS = 30_000L

        /** Play Billing's first connection is slower than any screen transition. */
        const val STORE_TIMEOUT_MS = 30_000L

        /**
         * How long an absence has to hold before it counts as one.
         *
         * The maintenance banner fades in over the game, so "it is not there this
         * instant" is what a passing screenshot of a failing app looks like.
         */
        const val BANNER_SETTLE_MS = 5_000L

        const val THREE_OVERRIDES = 3

        /**
         * Two values that exist nowhere in the APK.
         *
         * That is the whole point of both. A sentinel drawn from the app's own
         * strings would pass against a compiled default, which is exactly the
         * failure a broken serializer produces.
         */
        const val SENTINEL_MESSAGE = "R8 smoke sentinel, not a compiled string"
        const val SENTINEL_COOLDOWN = "137"
    }
}
