package com.dangerfield.drop2048.baselineprofile

import android.graphics.Rect
import android.os.SystemClock
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.SCREEN_TIMEOUT_MS
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.SETTINGS
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.ciText
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.describeScreen
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.tapMatching
import com.dangerfield.drop2048.baselineprofile.BenchmarkJourney.tapRequired
import java.util.regex.Pattern

/**
 * The verbs [MinifiedReleaseSurfacesTest] needs and [BenchmarkJourney] does not.
 *
 * Kept apart from the profile journey on purpose. [BenchmarkJourney] is walked by
 * the two profile generators as well as the smoke test, so a verb added there
 * changes what gets AOT-compiled; these screens are worth R8 coverage and are not
 * worth baseline-profile coverage, because nobody opens the debug menu on a cold
 * start. The selector primitives are still the shared ones, so a typography or
 * copy change still lands in one place.
 *
 * ## Scrolling is the whole difficulty, and it has three causes
 *
 * C13a abandoned the achievements grid after two emulator runs on "the swipe
 * worked, the row was found, the tap reported success and the screen never
 * changed". Driving the minified build by hand and then failing a first run
 * found three separate reasons for that one symptom:
 *
 * 1. **A row scrolled under the top bar is still `hasObject`.** The list scrolls
 *    behind a translucent `TopBar`, so UiAutomator reports a node at y=54 on a
 *    screen whose bar covers the top 280px, and the click lands on the bar.
 *    [aboveReach] is why a row inside that margin is scrolled back down instead
 *    of tapped.
 * 2. **A swipe down the middle of the column grabs a text field**, not the list.
 *    The config screen is mostly `OutlinedTextField`s. [scrollContent] swipes
 *    down the left margin, where there is nothing but the list.
 * 3. **`waitForIdle` returns while the list is still flinging.** This is the one
 *    that failed the first run of all three tests. See [awaitStill].
 *
 * Text is entered with [UiObject2.setText] rather than through the IME. It is one
 * accessibility action instead of a focus, a keyboard raise and a per-character
 * dispatch, and it cannot be defeated by a soft keyboard covering the button that
 * has to be pressed next.
 */
object SurfaceJourney {

    const val ACHIEVEMENTS = "Achievements"
    const val GO_PRO = "Go Pro"
    const val RESTORE = "Restore purchases"
    const val VERSION = "Version"

    /**
     * Verbatim from `BuildTypeDebugMenuGate`, which is the point.
     *
     * The passphrase is a compiled-in constant guarding a menu that can grant Pro
     * on a store binary. If somebody changes it, a test that walks a release
     * build is exactly where that should surface.
     */
    const val DEBUG_PASSPHRASE = "cascade"

    const val DEBUG_ROW = "Debug menu"

    /**
     * The snackbar the seventh tap raises.
     *
     * Named here because it is a **prefix of** [DEBUG_ROW], so any selector that
     * finds the row finds this first while it is up.
     */
    const val DEBUG_UNLOCKED_SNACKBAR = "Debug menu unlocked."
    const val DEBUG_PASSPHRASE_TITLE = "Release build"
    const val DEBUG_UNLOCK = "Unlock"
    const val DEBUG_SESSION_BANNER = "Debug session"
    const val CONFIG_ROW = "Config overrides"
    const val CONFIG_CLEAR_ALL = "Clear all overrides"
    const val CONFIG_SET = "Set"
    const val CONFIG_NONE = "No local overrides"

    const val PAYWALL_ANCHOR = "One purchase. Yours forever."
    const val PAYWALL_RESTORE = "Restore purchase"

    const val AD_GATE_INPUTS = "Gate inputs"

    const val COOLDOWN_PATH = "ads.interstitial.cooldownSeconds"
    const val MAINTENANCE_MODE_PATH = "upgrade.maintenanceMode"
    const val MAINTENANCE_MESSAGE_PATH = "upgrade.maintenanceMessage"
    const val MAINTENANCE_BANNER = "banner"

    /**
     * How many taps on the version row open the debug menu.
     *
     * `SettingsViewModel.TapsToUnlockDebug`. The unlock is persisted in
     * `PlayerSettings`, so a device that has been here before is already past it
     * and the taps are a no-op rather than a problem.
     */
    const val TAPS_TO_UNLOCK = 7

    /** Twice [TAPS_TO_UNLOCK], so a dropped tap costs a tap rather than a run. */
    private const val MAX_VERSION_TAPS = 14

    private const val ARRIVAL_TIMEOUT_MS = 2_000L
    private const val TAP_CADENCE_MS = 200L
    private const val SNACKBAR_TIMEOUT_MS = 15_000L
    private const val STILL_POLL_MS = 100L
    private const val STILL_TIMEOUT_MS = 4_000L
    private const val SWIPE_STEPS = 40
    private const val SMALL_SWIPE_STEPS = 12
    private const val MAX_SCROLLS = 30
    private const val SCROLLS_TO_TOP = 14
    private const val TAP_ATTEMPTS = 3
    private const val SET_ATTEMPTS = 3
    private const val COUNT_TIMEOUT_MS = 4_000L

    /**
     * How much of the screen the app's own top bar can cover.
     *
     * Measured at 280px of 2424 on a Pixel 9, which is 11.6%. Fifteen leaves room
     * for a device whose bar is taller and still costs nothing: a row inside the
     * margin is scrolled down rather than given up on.
     */
    private const val TOP_BAR_MARGIN = 0.15

    /** Any number of anything, then "of", a number, and "earned". */
    val achievementsSummary: BySelector
        get() = By.text(Pattern.compile("\\d+ of \\d+ earned.*", Pattern.CASE_INSENSITIVE))

    /**
     * Any of the four things the store may answer a restore with.
     *
     * All four are a pass. A fresh emulator with no Play account answers "could
     * not reach the store", a device with the product owned answers "restored",
     * and which one arrives is a property of the account rather than of R8. What
     * is being asserted is that the billing client answered **at all**: a renamed
     * listener leaves the button pressed and the screen silent forever.
     */
    val anyRestoreAnswer: BySelector
        get() = BenchmarkJourney.ciAnyOf(
            "Pro restored",
            "Nothing to restore on this device.",
            "Could not reach the store.",
            "Asking the store",
        )

    /** Every `OutlinedTextField` on screen, in layout order. */
    private val anyTextField: BySelector get() = By.clazz("android.widget.EditText")

    /**
     * Opens Settings from a menu overlay and waits for a row only Settings has.
     *
     * Anchored on "Sound and feel" rather than on the title, because "Settings"
     * is also the button that was just tapped and is still on screen behind the
     * push transition.
     */
    fun UiDevice.openSettings() {
        tapRequired(SETTINGS)
        check(
            wait(
                Until.hasObject(ciText(BenchmarkJourney.SETTINGS_SOUND_SECTION)),
                SCREEN_TIMEOUT_MS,
            ) == true
        ) {
            "Tapped $SETTINGS but the settings screen never appeared. " + describeScreen()
        }
    }

    /**
     * Scrolls until [label] is somewhere a tap will actually reach it.
     *
     * Returns the node rather than a boolean so the caller can click the thing
     * that was measured, instead of re-finding it and possibly getting a
     * different one.
     */
    fun UiDevice.scrollToRow(label: String): UiObject2 =
        scrollToRowOrNull(label)
            ?: error("Scrolled the whole screen without putting \"$label\" in reach. " + describeScreen())

    /**
     * [scrollToRow] for a caller that has something better to say about an absence.
     *
     * Two things here are not obvious and both cost a run.
     *
     * **It waits before it scrolls.** A screen that is still sliding in has none
     * of its rows in the tree yet, so a loop that scrolls the instant it fails to
     * find something spends its whole budget scrolling a screen that has not
     * arrived. That is how a walk looking for the *second* row on the config
     * screen ended up at the bottom of it.
     *
     * **It gives up downwards and starts again from the top.** Otherwise the
     * direction the caller happens to be scrolled in decides whether a row exists.
     */
    fun UiDevice.scrollToRowOrNull(label: String): UiObject2? {
        val selector = ciText(label)
        wait(Until.hasObject(selector), ARRIVAL_TIMEOUT_MS)
        repeat(MAX_SCROLLS) { attempt ->
            waitForIdle()
            val found = runCatchingStale { findObject(selector) }
            when {
                found != null && !awaitStill(found) -> Unit
                found != null && aboveReach(found) -> scrollContent(down = false, small = true)
                found != null -> return found
                attempt == MAX_SCROLLS / 2 -> scrollToTop()
                else -> scrollContent(down = true, small = false)
            }
        }
        return null
    }

    /**
     * Polls a node's rectangle until it stops moving.
     *
     * This is the fix for the failure C13a described as "the swipe worked, the
     * row was found, the tap reported success and the screen never changed", and
     * the cause is not the one that reading sounds like. A list is still
     * **flinging** when `waitForIdle` returns, because Compose's fling is an
     * animation and animations do not emit the accessibility events
     * `waitForIdle` waits on. So the tap arrives at coordinates the row has
     * already left, Compose reads it as the gesture that stops the fling, and
     * both the tap and the assertion are honest about what they saw.
     *
     * A poll rather than a sleep because a fling has no completion signal to
     * await, which is the case `docs/practices/testing.md` names as the one a
     * bounded poll is for.
     */
    private fun UiDevice.awaitStill(node: UiObject2): Boolean {
        val deadline = SystemClock.uptimeMillis() + STILL_TIMEOUT_MS
        var previous: Rect? = null
        while (SystemClock.uptimeMillis() < deadline) {
            waitForIdle()
            val current = runCatchingStale { Rect(node.visibleBounds) } ?: return false
            if (current == previous) return true
            previous = current
            SystemClock.sleep(STILL_POLL_MS)
        }
        return false
    }

    /** Back to the head of whatever list is on screen. */
    fun UiDevice.scrollToTop() {
        repeat(SCROLLS_TO_TOP) { scrollContent(down = false, small = false) }
        waitForIdle()
    }

    /**
     * Asserts the config screen says exactly [count] overrides are in force.
     *
     * This is the settle after a Set as well as the check on it. The line is
     * rendered from the repository rather than from the field that was typed
     * into, so it cannot agree with a write that did not happen, and waiting for
     * it means no test here needs a sleep.
     */
    fun UiDevice.awaitOverrideCount(count: Int) {
        check(overrideCountIs(count)) {
            "Expected \"${overrideCountText(count)}\" on the config screen. " + describeScreen()
        }
    }

    private fun UiDevice.overrideCountIs(count: Int): Boolean {
        scrollToTop()
        return wait(Until.hasObject(ciText(overrideCountText(count))), COUNT_TIMEOUT_MS) == true
    }

    /** `DebugCopy.overriddenCount`, verbatim. */
    private fun overrideCountText(count: Int): String = when (count) {
        0 -> CONFIG_NONE
        1 -> "1 local override in force."
        else -> "$count local overrides in force."
    }

    /** [scrollToRow], then click it, re-finding if it recomposes under the tap. */
    fun UiDevice.tapRow(label: String) {
        repeat(TAP_ATTEMPTS) {
            val row = scrollToRow(label)
            try {
                row.click()
                waitForIdle()
                return
            } catch (_: StaleObjectException) {
                waitForIdle()
            }
        }
        error("Found \"$label\" in reach but could not click it. " + describeScreen())
    }

    /**
     * Seven taps on the version row, then the passphrase if the build asks.
     *
     * The two locks have different lifetimes and the walk has to be correct for
     * both. `debugMenuUnlocked` is persisted in `PlayerSettings`, so the row is
     * already there on a device that has been here before and the extra taps are
     * a no-op; the passphrase is per-process and has to be answered again on
     * every launch. That is why the taps are unconditional and the passphrase is
     * conditional, rather than the other way round.
     *
     * ## Two things here cost a run each
     *
     * **The taps go to a coordinate and they are counted by their effect.**
     * Seven `UiObject2.click()` calls re-resolve the node every time, and a node
     * that goes stale between two of them silently drops a tap, so the counter
     * lands on six and nothing happens; a run failed exactly that way. The row
     * does not move while it is being tapped, so its centre is the honest thing
     * to aim at, and the loop keeps tapping until the snackbar says the gesture
     * landed rather than counting to seven and hoping.
     *
     * **The row it reveals is drawn below the version row that revealed it**, and
     * on a shorter screen that is past the fold. Checking `hasObject` for it
     * reports "the taps did not work" for a menu that unlocked perfectly, which
     * is what a first run of this reported. It has to be scrolled to.
     *
     * ## And the snackbar is L75 again, exactly
     *
     * The seventh tap raises a snackbar reading **"Debug menu unlocked."**, which
     * the selector for the **"Debug menu"** row matches, because these selectors
     * are prefixes. So the walk found the snackbar, tapped the snackbar, and
     * reported that the passphrase screen never opened. The snackbar also sits
     * over the bottom of the list, which is where the row it is announcing is
     * drawn, so even an exact selector would have been tapping through it.
     * Waiting for it to go fixes both.
     */
    fun UiDevice.openDebugMenu() {
        val version = scrollToRow(VERSION)
        val centre = Rect(version.visibleBounds)
        var taps = 0
        while (taps < MAX_VERSION_TAPS && !hasObject(ciText(DEBUG_UNLOCKED_SNACKBAR))) {
            click(centre.centerX(), centre.centerY())
            SystemClock.sleep(TAP_CADENCE_MS)
            taps++
        }
        waitForIdle()
        wait(Until.gone(ciText(DEBUG_UNLOCKED_SNACKBAR)), SNACKBAR_TIMEOUT_MS)

        checkNotNull(scrollToRowOrNull(DEBUG_ROW)) {
            "$taps taps on $VERSION did not reveal the $DEBUG_ROW row. " + describeScreen()
        }
        tapRow(DEBUG_ROW)

        check(
            wait(
                Until.hasObject(BenchmarkJourney.ciAnyOf(DEBUG_PASSPHRASE_TITLE, DEBUG_SESSION_BANNER)),
                SCREEN_TIMEOUT_MS,
            ) == true
        ) {
            "Tapped $DEBUG_ROW and neither the passphrase prompt nor the menu appeared. " +
                describeScreen()
        }

        if (hasObject(ciText(DEBUG_PASSPHRASE_TITLE))) {
            val field = requireNotNull(runCatchingStale { findObject(anyTextField) }) {
                "The passphrase prompt is up but has no text field. " + describeScreen()
            }
            field.text = DEBUG_PASSPHRASE
            waitForIdle()
            tapRequired(DEBUG_UNLOCK)
        }

        check(wait(Until.hasObject(ciText(DEBUG_SESSION_BANNER)), SCREEN_TIMEOUT_MS) == true) {
            "The passphrase did not open the debug menu. " + describeScreen()
        }
    }

    /**
     * Writes [value] into the row whose key is [path] and does not return until
     * the screen agrees that [expectedCount] overrides are now in force.
     *
     * The row is located by its **path** rather than its display name because the
     * path is the key the app reads and the name is copy. The field and the
     * button are then the first of each *below* that label, which is what the
     * layout guarantees: `ConfigValueField` draws the field under the row and the
     * Set under the field.
     *
     * ## Why it counts rather than trusting the press
     *
     * Setting an override **grows its own row**: an "Overridden locally" line and
     * a "Remove the override" row appear under it, and every row below moves. A
     * second Set aimed at a row that has just shifted can land on a Set that is
     * now disabled, and `UiObject2.click()` on a disabled Compose button is a
     * silent no-op. A run of this wrote two of three overrides and said nothing
     * until the assertion three steps later.
     *
     * The count line is rendered from the repository rather than from the field
     * that was typed into, so it cannot agree with a write that did not happen.
     * Reading it is both the check and the retry condition.
     */
    fun UiDevice.setOverride(path: String, value: String, expectedCount: Int) {
        repeat(SET_ATTEMPTS) {
            writeOverride(path, value)
            if (overrideCountIs(expectedCount)) return
        }
        error("Setting \"$path\" to \"$value\" never took. " + describeScreen())
    }

    private fun UiDevice.writeOverride(path: String, value: String) {
        val label = scrollToRow(path)
        val anchor = label.visibleBounds.bottom

        val field = requireNotNull(firstBelow(anyTextField, anchor)) {
            "No text field under \"$path\". " + describeScreen()
        }
        field.text = value
        waitForIdle()

        val set = requireNotNull(firstBelow(ciText(CONFIG_SET), anchor)) {
            "No $CONFIG_SET button under \"$path\". " + describeScreen()
        }
        runCatchingStale { set.click() }
        waitForIdle()
    }

    /**
     * Opens the config overrides screen and waits for a control only it has.
     *
     * Named separately from a bare [tapRow] because the walk immediately goes
     * looking for a row near the top of it, and a screen that is still sliding in
     * has no rows at all.
     */
    fun UiDevice.openConfigOverrides() {
        tapRow(CONFIG_ROW)
        check(wait(Until.hasObject(ciText(CONFIG_CLEAR_ALL)), SCREEN_TIMEOUT_MS) == true) {
            "Tapped $CONFIG_ROW but the overrides screen never appeared. " + describeScreen()
        }
    }

    /** Presses Clear all overrides and waits for the screen to say there are none. */
    fun UiDevice.clearOverrides() {
        tapRow(CONFIG_CLEAR_ALL)
        check(wait(Until.hasObject(ciText(CONFIG_NONE)), SCREEN_TIMEOUT_MS) == true) {
            "$CONFIG_CLEAR_ALL left overrides behind. " + describeScreen()
        }
    }

    /**
     * Whether [text] is anywhere on screen, waiting for it to arrive.
     *
     * Named for what it is so that a call site reads as an assertion about the
     * app rather than as a UiAutomator incantation.
     */
    fun UiDevice.awaitText(text: String, timeoutMs: Long = SCREEN_TIMEOUT_MS): Boolean =
        wait(Until.hasObject(ciText(text)), timeoutMs) == true

    fun UiDevice.awaitMatching(selector: BySelector, timeoutMs: Long = SCREEN_TIMEOUT_MS): Boolean =
        wait(Until.hasObject(selector), timeoutMs) == true

    /** Taps something and does not care whether it was there. Used for dismissals. */
    fun UiDevice.tapIfPresent(text: String) {
        if (hasObject(ciText(text))) tapMatching(ciText(text), SCREEN_TIMEOUT_MS)
    }

    private fun UiDevice.firstBelow(selector: BySelector, y: Int): UiObject2? =
        runCatchingStale {
            findObjects(selector)
                .filter { (runCatchingStale { it.visibleBounds.top } ?: 0) >= y }
                .minByOrNull { runCatchingStale { it.visibleBounds.top } ?: Int.MAX_VALUE }
        }

    /**
     * Only the **top** edge is constrained, and that asymmetry is the finding.
     *
     * `visibleBounds` is already clipped by the system bars, so a row at the
     * bottom of the screen reports the rectangle a tap would land in and needs no
     * margin. The app's own `TopBar` is a sibling in the same window, which
     * UiAutomator has no way to know sits on top, so the top edge is the only one
     * a test has to defend.
     */
    private fun UiDevice.aboveReach(node: UiObject2): Boolean {
        val bounds = runCatchingStale { node.visibleBounds } ?: return false
        return bounds.top < safeTop()
    }

    private fun UiDevice.safeTop(): Int = (displayHeight * TOP_BAR_MARGIN).toInt()

    /**
     * One list scroll, taken down the **left margin**.
     *
     * A swipe down the middle of the config screen lands on an `OutlinedTextField`
     * and moves a cursor instead of the list. The margin is empty on every screen
     * this walks.
     */
    private fun UiDevice.scrollContent(down: Boolean, small: Boolean) {
        val x = (displayWidth * MARGIN_X).toInt()
        val far = (displayHeight * FAR_EDGE).toInt()
        val near = (displayHeight * NEAR_EDGE).toInt()
        val steps = if (small) SMALL_SWIPE_STEPS else SWIPE_STEPS
        if (down) swipe(x, far, x, near, steps) else swipe(x, near, x, far, steps)
        waitForIdle()
    }

    private const val MARGIN_X = 0.04
    private const val FAR_EDGE = 0.82
    private const val NEAR_EDGE = 0.28

    private fun <T> runCatchingStale(block: () -> T): T? =
        try {
            block()
        } catch (_: StaleObjectException) {
            null
        }
}
