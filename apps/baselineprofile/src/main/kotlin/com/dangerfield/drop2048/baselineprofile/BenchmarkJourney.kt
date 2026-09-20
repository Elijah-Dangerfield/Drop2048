package com.dangerfield.drop2048.baselineprofile

import android.content.Intent
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/**
 * The journey the profile generators and the R8 smoke test all drive.
 *
 * Shared because the alternative is three copies of the same UiAutomator taps
 * drifting apart, and the first symptom of that drift is a profile that quietly
 * covers less while every assertion still passes.
 *
 * ## What this walks
 *
 * Drop 2048 has no home screen — C5 deleted it — so the destination is the
 * **pause overlay**: Restart, Quit, Stats, Settings, over a run
 * in progress. Getting there on a fresh install means playing, because SPEC 13
 * drops first launch straight into the scripted tutorial and
 * `Tutorial.SkippableFromDrop = 3` withholds "Skip tutorial" until the third
 * drop. So [reachMenu] steers, hard-drops, acknowledges a card, hard-drops
 * again, takes the skip, and pauses the run it lands in.
 *
 * That is not a detour around the real work. It is the falling block, the
 * cascade transcript and its playback, the coach mark, the tutorial state
 * machine and the first write to the app cache — the code a cold first launch
 * actually runs.
 *
 * ## Keep these two things
 *
 * 1. **The adaptive structure.** [reachMenu] reads what is on screen and
 *    responds, rather than replaying a fixed tap sequence. It has to: iteration
 *    one of a generation run sees the tutorial, and every iteration after it
 *    does not, because finishing it persists `hasUserOnboarded`. A hardcoded
 *    sequence is wrong on at least one of those two and silently.
 * 2. **[describeScreen].** A failure that says only "the thing I wanted was not
 *    there" cannot separate an R8 breakage from an error dialog, a spinner, or a
 *    step nobody knew existed. Those need different fixes and guessing between
 *    them costs a full emulator run each time. This is not theoretical: it is
 *    what printed the tutorial's first coach mark, word for word, and turned the
 *    template-journey diagnosis into one nine-minute run instead of four.
 *
 * ## If you add a hook to skip the tutorial
 *
 * Generation runs a **release** variant, so a benchmark hook gated on
 * `BuildConfig.DEBUG` is dead exactly where it is needed. There is no such hook
 * here, and there should not be one: the tutorial is the first-launch path and a
 * profile that skips it is a profile that misses the launch it exists to speed
 * up.
 *
 * Nothing on the launch path may be network-bound. The config fetch is
 * fire-and-forget and the launch gates render from cache, which is what keeps
 * the first frame independent of a server this run cannot reach.
 */
object BenchmarkJourney {

    const val PACKAGE = "com.dangerfield.drop2048"

    /** Launch intent that starts the app cold on its real first screen. */
    fun launchIntent(): Intent = Intent().apply {
        // By class name, not MAIN/LAUNCHER: once benchmark flags and extras are
        // attached, category-based intent resolution fails outright.
        setClassName(PACKAGE, "$PACKAGE.MainActivity")
    }

    /**
     * Walks whatever is on screen until the app's menu is up.
     *
     * ## The menu is the pause overlay, and finding that out cost two runs
     *
     * The obvious destination is the **start** overlay — Play, Stats, Settings,
     * over a board that has not begun — and it is very nearly
     * unreachable from here. `finishTutorial` calls `resetToRun`, which sets the
     * phase to `Playing` and starts the clock, so skipping the tutorial drops
     * the player straight into a live run rather than into a menu. Every
     * iteration after the first has a saved run and resumes into `Playing` for
     * the same reason. The start overlay belongs to a launch with no saved run
     * and no tutorial, which is a state a generation run is almost never in.
     *
     * The **pause** overlay carries the same `MenuOptions` — that composable
     * exists precisely so the two cannot drift — and it is reachable from any
     * live run by one tap. So that is the destination.
     *
     * ## Two more things here are scar tissue
     *
     * **The tutorial check waits rather than asking.** `hasObject` with no
     * timeout asks whether a coach mark is on screen *this instant*, and it is
     * not: it fades in over the board the previous tap was aimed at.
     *
     * **A coach mark is identified by its title, never by its button.** The
     * start overlay's primary button and the tutorial's last card both read
     * "Play". Tapping on the button alone started a real run and then
     * hard-dropped through it; all three tests failed holding a board at level
     * 2, and one had stacked out and been offered a rewarded continue.
     */
    /**
     * Launch, and stop the moment the app has drawn something.
     *
     * This is the whole of the **startup** journey, and the separation is the
     * point. A startup profile is placed differently in the DEX layout and
     * Android's guidance is explicit that an oversized one "overflows into
     * subsequent DEX files… and slows down startup".
     *
     * C13a measured that rather than assuming it. With the startup generator
     * sharing [reachMenu] — which plays the tutorial, and therefore the engine,
     * the transcript, the cascade playback and every screen the menu opens — the
     * generated `startup-prof.txt` came out at 31,233 rules against
     * `baseline-prof.txt`'s 34,673. Ninety percent. Not byte-identical the way
     * the downstream case in [StartupProfileGenerator] was, but the same mistake
     * with a different number on it, and it would have shipped.
     *
     * The first frame is the right stopping point because it is what "launch"
     * means. Everything after it is the journey profile's job.
     */
    fun UiDevice.awaitFirstFrame() {
        check(wait(Until.hasObject(anyOnScreenText), FIRST_SCREEN_TIMEOUT_MS) == true) {
            "The app never rendered a first frame. " + describeScreen()
        }
    }

    fun UiDevice.reachMenu() {
        awaitFirstFrame()

        repeat(JOURNEY_MAX_STEPS) {
            waitForIdle()
            if (onMenu()) return

            if (wait(Until.hasObject(anyCardTitle), SETTLE_TIMEOUT_MS) == true) {
                playTheTutorial()
            } else if (hasObject(ciText(CONTINUE_OFFER))) {
                tap(CONTINUE_DECLINE, STEP_TIMEOUT_MS)
            } else {
                tapDescription(PAUSE, STEP_TIMEOUT_MS)
                wait(Until.hasObject(ciText(STATS)), SETTLE_TIMEOUT_MS)
            }
        }
        error("Never reached the menu. " + describeScreen())
    }

    /**
     * One beat of the scripted run.
     *
     * Only ever called with a coach mark on screen, which is what makes the
     * three cases unambiguous: the skip when it is being offered, the card's own
     * button when the beat waits for one, and otherwise a drop, because a beat
     * that is not waiting for a tap is waiting for a landing.
     */
    private fun UiDevice.playTheTutorial() {
        when {
            hasObject(ciText(TUTORIAL_SKIP)) -> tap(TUTORIAL_SKIP, STEP_TIMEOUT_MS)
            hasObject(anyCardConfirm) -> tapMatching(anyCardConfirm, STEP_TIMEOUT_MS)
            else -> dropTheBlock()
        }
    }

    /**
     * Stats and Settings, each pushed and popped.
     *
     * Deeper than the startup path on purpose: this covers the first run of
     * navigation, two ViewModels and the Room reads behind Stats, which is where
     * the app spends its time after launch and which the startup profile must
     * NOT carry.
     *
     * ## Achievements is deliberately not here
     *
     * It is the one screen a player can only reach through Settings, and it sits
     * in the fourth of eight sections, so reaching it means scrolling a list.
     * Two runs were spent on that: the swipe worked, the row was found, the tap
     * reported success and the screen never changed — a tap that arrests a fling
     * rather than clicking through it, on a row whose clickable is a different
     * semantics node from the text that identifies it.
     *
     * The Daily Challenge used to stand in for it here — one tap from this same
     * menu, a `@Serializable` route, a Room read on the way in — and D27 deleted
     * it. The cost is named rather than hidden: the achievements grid has no
     * profile coverage and no R8 coverage, and `docs/todos.md` should carry it.
     */
    fun UiDevice.visitDetailScreens() {
        tapRequired(STATS)
        check(wait(Until.hasObject(anyStatsAnchor), SCREEN_TIMEOUT_MS) == true) {
            "Tapped $STATS but the stats screen never appeared. " + describeScreen()
        }
        pressBack()

        tapRequired(SETTINGS)
        check(wait(Until.hasObject(ciText(SETTINGS_SOUND_SECTION)), SCREEN_TIMEOUT_MS) == true) {
            "Tapped $SETTINGS but the settings screen never appeared. " + describeScreen()
        }
        pressBack()

        check(wait(Until.hasObject(ciText(STATS)), SCREEN_TIMEOUT_MS) == true && onMenu()) {
            "Never got back to the menu. " + describeScreen()
        }
    }

    /**
     * Whether one of the app's menus is up.
     *
     * Three overlays carry the same `MenuOptions` — start, paused and stacked
     * out — and which one a benchmark iteration lands on is not something the
     * journey gets to choose. Iteration one skips the tutorial into a live run
     * and pauses it. A later iteration resumes a saved run that is already near
     * the top, hard-drops twice and **stacks out**, which is how a run of this
     * cost a fifth attempt: the loop sat tapping a Pause button that is disabled
     * on a finished run, with Stats and Settings plainly on screen the whole
     * time.
     *
     * So the predicate is the menu itself rather than any one overlay's
     * headline. Two labels, not one, because "Stats" and "Settings" each also
     * head the screen they open.
     */
    private fun UiDevice.onMenu(): Boolean =
        hasObject(ciText(STATS)) && hasObject(ciText(SETTINGS))

    /**
     * Whether a tutorial coach mark is the thing on screen.
     *
     * Matched on the eight card titles, because they are the only strings in the
     * app that appear nowhere else. The confirm labels are not: "Play" is also
     * the start overlay's primary button, and "OK" is a label any dialog could
     * grow.
     */
    private fun UiDevice.onCoachMark(): Boolean = hasObject(anyCardTitle)

    /**
     * Steers once, then hard-drops.
     *
     * The steer is not needed to finish a drop — since D21 one ▼ press ends it,
     * and `TutorialRunner.completeDrop` marks the steering beat overtaken. It is
     * here because drag and arrow steering is what a player does on every drop
     * of every run, and a profile that never steers omits it.
     *
     * Tapped rather than dragged: a drag is a real gesture on a real board with
     * clamped columns, and one that lands outside them is a no-op that looks
     * identical to a broken journey.
     */
    private fun UiDevice.dropTheBlock() {
        tapDescription(MOVE_LEFT, STEP_TIMEOUT_MS)
        check(tapDescription(HARD_DROP, STEP_TIMEOUT_MS)) {
            "Nothing on screen to advance: no menu, no card, no drop control. " + describeScreen()
        }
    }

    // Verbatim from `libraries/resources/.../strings.xml`. If one changes, these
    // tests are where it surfaces, which is the point.
    const val TUTORIAL_SKIP = "Skip tutorial"
    const val TUTORIAL_GOT_IT = "Got it"
    const val TUTORIAL_OK = "OK"
    const val TUTORIAL_BEGIN = "Play"
    const val CONTINUE_OFFER = "Keep going?"
    const val CONTINUE_DECLINE = "No thanks"
    const val STATS = "Stats"
    const val SETTINGS = "Settings"
    const val STATS_EMPTY = "No runs yet"
    const val STATS_LIFETIME = "Lifetime"
    const val SETTINGS_SOUND_SECTION = "Sound and feel"

    // Content descriptions, not labels: the control row draws glyphs and hangs
    // the words off `semantics`. `game_move_left` and `game_hard_drop`.
    const val MOVE_LEFT = "Move left"
    const val HARD_DROP = "Drop to the bottom"
    const val PAUSE = "Pause"

    const val FIRST_SCREEN_TIMEOUT_MS = 20_000L
    const val SCREEN_TIMEOUT_MS = 15_000L

    /**
     * How long each pass of [reachMenu] waits for the start overlay before
     * deciding it is still in the tutorial.
     *
     * Long enough for an overlay to fade in over the board, short enough that
     * paying it on every one of roughly five passes per iteration does not
     * dominate a run.
     */
    const val SETTLE_TIMEOUT_MS = 1_500L
    const val STEP_TIMEOUT_MS = 15_000L

    /**
     * Eleven scripted beats plus slack. The tutorial is six drops and the skip
     * arrives on the third, so a healthy run leaves this loop after four or five
     * passes; the headroom is for a cascade whose playback outlasts a step.
     */
    const val JOURNEY_MAX_STEPS = 14

    private const val TAP_ATTEMPTS = 3
    private const val RETRY_FIND_MS = 500L
    private const val MAX_REPORTED_LINES = 25

    /**
     * Any non-empty text at all.
     *
     * A minified build that fails on the DI graph shows a blank window rather
     * than a crash dialog, and "reached no screen I recognise" and "rendered
     * nothing" want different first questions asked of them.
     */
    val anyOnScreenText: BySelector get() = By.text(Pattern.compile(".+"))

    /** Any coach mark's confirm button. The label changes per beat. */
    val anyCardConfirm: BySelector get() = ciAnyOf(TUTORIAL_GOT_IT, TUTORIAL_OK, TUTORIAL_BEGIN)

    /**
     * Every coach mark title in `Tutorial.Script`, in script order.
     *
     * All eight, not a sample: a beat whose title is missing here reads as "not
     * a coach mark", and the loop would then try to play the board underneath a
     * card that has frozen it.
     */
    val anyCardTitle: BySelector
        get() = ciAnyOf(
            "Slide it over",
            "Drop it",
            "Four",
            "Again",
            "You will use",
            "Watch this one",
            "Last one",
            "Now for real",
        )

    /**
     * Either shape of the stats screen. A device that has finished a run shows
     * the lifetime block; one that has not shows the empty state, and a
     * generation run can be in either state depending on the iteration.
     */
    val anyStatsAnchor: BySelector get() = ciAnyOf(STATS_EMPTY, STATS_LIFETIME)

    /**
     * A selector for [text] that ignores case.
     *
     * Not a nicety. The design system's button typography renders its label in
     * upper case, and UiAutomator reads the *rendered* text — so `By.text("Send
     * Feedback")` matches nothing while the button plainly says SEND FEEDBACK.
     * The whole journey failed on this, and the only reason it took one run
     * rather than several is that [describeScreen] printed what was actually on
     * screen. Match case-insensitively so a typography change cannot silently
     * break the profile.
     */
    fun ciText(text: String): BySelector =
        By.text(Pattern.compile(Pattern.quote(text) + ".*", Pattern.CASE_INSENSITIVE))

    /** Selector matching any one of [options], ignoring case. */
    fun ciAnyOf(vararg options: String): BySelector = By.text(
        Pattern.compile(
            options.joinToString("|") { Pattern.quote(it) + ".*" },
            Pattern.CASE_INSENSITIVE,
        ),
    )

    /** Taps a text element, reporting whether it was there. */
    fun UiDevice.tap(text: String, timeoutMs: Long): Boolean =
        tapMatching(ciText(text), timeoutMs)

    /**
     * Taps by content description rather than label.
     *
     * The game's controls are `◀ ▼ ▶` with the words in `semantics`, so matching
     * on text would pin the journey to three glyphs a redesign could change
     * without changing what the control is. The description is the stable half
     * and it is the half a screen reader uses.
     */
    fun UiDevice.tapDescription(description: String, timeoutMs: Long): Boolean =
        tapMatching(By.desc(Pattern.compile(Pattern.quote(description), Pattern.CASE_INSENSITIVE)), timeoutMs)

    /**
     * Finds and taps, re-finding if the node goes stale in between.
     *
     * Every transition in this app is animated, and UiAutomator hands back a
     * handle to a node Compose may replace before the click lands — which throws
     * rather than missing. Only the first look pays the caller's timeout; a
     * retry means the node was there a frame ago, and re-paying the full wait on
     * every retry is what once turned a three-minute run into twenty-five.
     */
    fun UiDevice.tapMatching(selector: BySelector, timeoutMs: Long): Boolean {
        repeat(TAP_ATTEMPTS) { attempt ->
            waitForIdle()
            val budget = if (attempt == 0) timeoutMs else RETRY_FIND_MS
            val found = wait(Until.findObject(selector), budget) ?: return false
            try {
                found.click()
                waitForIdle()
                return true
            } catch (_: StaleObjectException) {
                // Recomposed between find and click. Go round again.
            }
        }
        return false
    }

    /** Taps something that must be there, failing the run loudly if it is not. */
    fun UiDevice.tapRequired(text: String, timeoutMs: Long = SCREEN_TIMEOUT_MS) {
        check(tap(text, timeoutMs)) {
            "Journey stalled: no \"$text\" after ${timeoutMs}ms. " + describeScreen()
        }
    }

    /**
     * Everything readable on screen when an assertion failed.
     *
     * Without it a failure says only "the thing I wanted was not there", which
     * cannot separate an R8 breakage from an error dialog, a spinner or a step
     * nobody knew existed. Those need different fixes, and guessing between them
     * costs a full emulator run each time.
     */
    fun UiDevice.describeScreen(): String {
        // `By.textContains("")` matches nothing, which is why this reported an
        // empty screen on every failure and told us less than no diagnostic at
        // all — it looked like evidence of a blank window. `.+` is the selector
        // that actually matches any non-empty text.
        val visible = findObjects(By.text(Pattern.compile(".+")))
            .mapNotNull { runCatchingStale { it.text?.trim() } }
            .filter { !it.isNullOrEmpty() }
            .distinct()
            .take(MAX_REPORTED_LINES)

        // Which app is actually in front. Distinguishes "our screen is wrong"
        // from "we are not even in the app any more" — a crash, a system
        // dialog, or a launch that never landed all look identical otherwise.
        val foreground = runCatchingStale { currentPackageName } ?: "unknown"

        // Deliberately ONE line. Gradle's console prints only the first line of
        // an assertion message, so a pretty multi-line dump is invisible exactly
        // when it is needed.
        val screen = if (visible.isEmpty()) {
            "<no readable text — blank, loading, or a native-canvas-only screen>"
        } else {
            visible.joinToString(" | ")
        }
        return "[foreground=$foreground] screen: $screen"
    }

    /** UiAutomator throws if a node vanishes mid-read; a diagnostic must not. */
    private fun <T> runCatchingStale(block: () -> T): T? =
        try {
            block()
        } catch (_: StaleObjectException) {
            null
        }
}
