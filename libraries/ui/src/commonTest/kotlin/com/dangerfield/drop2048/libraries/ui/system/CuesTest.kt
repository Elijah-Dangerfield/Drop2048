package com.dangerfield.drop2048.libraries.ui.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource
import kotlin.time.TimeSource

/**
 * The two halves of a [Cue] arriving together, the one cue that has to be held
 * back, and who gets the vibrator when two of them want it at once.
 *
 * There is no sample bank in this repository and there is no way to feel a
 * haptic in a test, so what is actually assertable is the seam: that one
 * `play` reaches both engines, that the pitch offset and the strength that
 * arrive are the right ones, that a burst of movement does not arrive as a
 * burst of clicks, and that two overlapping calls are never issued to a device
 * that can only do one. Recording fakes rather than the silent defaults, because
 * `Cues.Silent` proves nothing — it is what every other test in the repo is
 * already running against.
 *
 * Every clock here is a `TestTimeSource`. The arbitration is measured in
 * milliseconds and a real one cannot be advanced, so a test on a real clock
 * would be testing how fast the machine running it happens to be.
 *
 * **Not covered here, and nowhere else either:** whether an Android vibrator
 * still logs `cancelled_superseded`. That takes a device and `dumpsys
 * vibrator_manager`. What these tests own is the layer above it — that [Cues]
 * never asks for the overlap in the first place — which is where the decision
 * lives and the only place it can be pinned in CI.
 */
class CuesTest {

    @Test
    fun aMerge_reachesBothEnginesOnOneCall() {
        val sounds = RecordingSoundPlayer()
        val haptics = RecordingHapticEngine()
        val cues = Cues(haptics, sounds, HapticsSetting.Strong)

        cues.play(Cue.Merge(step = 4))

        assertEquals(listOf(Sound.Merge to 3), sounds.played)
        assertEquals(listOf(HapticIntensity.Medium to 1f), haptics.played)
    }

    /**
     * SPEC 21's ascending run, as arithmetic. A six-step cascade is six merges at
     * six climbing pitches, and the step number is one-based while the offset is
     * a count of semitones above the sample, so they are off by one on purpose.
     */
    @Test
    fun aCascade_climbsOneSemitonePerStep() {
        val sounds = RecordingSoundPlayer()
        val cues = Cues(RecordingHapticEngine(), sounds, HapticsSetting.Strong)

        (1..6).forEach { step -> cues.play(Cue.Merge(step = step)) }

        assertEquals(listOf(0, 1, 2, 3, 4, 5), sounds.played.map { it.second })
    }

    /**
     * The cap, and the reason it is twelve. Both platforms pitch by playback rate
     * and both stop at 2.0x, so a thirteenth step would be clamped by whichever
     * engine got it rather than by a decision anybody made.
     */
    @Test
    fun theClimb_stopsAtAnOctave() {
        val sounds = RecordingSoundPlayer()
        val cues = Cues(RecordingHapticEngine(), sounds, HapticsSetting.Strong)

        cues.play(Cue.Merge(step = 40))

        assertEquals(Cue.MaxPitchSteps, sounds.played.single().second)
        assertEquals(2f, SoundBank.rate(Cue.MaxPitchSteps))
    }

    @Test
    fun aBigMerge_isADifferentSampleAndAHeavierBuzz() {
        val sounds = RecordingSoundPlayer()
        val haptics = RecordingHapticEngine()
        val cues = Cues(haptics, sounds, HapticsSetting.Strong)

        cues.play(Cue.Merge(step = 2, big = true))

        assertEquals(Sound.MergeBig, sounds.played.single().first)
        assertEquals(HapticIntensity.Heavy, haptics.played.single().first)
    }

    @Test
    fun theHapticsSetting_scalesEveryBuzzAndOffSilencesThem() {
        val light = RecordingHapticEngine()
        Cues(light, RecordingSoundPlayer(), HapticsSetting.Light).play(Cue.Burst)
        assertEquals(0.5f, light.played.single().second)

        val off = RecordingHapticEngine()
        Cues(off, RecordingSoundPlayer(), HapticsSetting.Off).play(Cue.Burst)
        assertEquals(0f, off.played.single().second, "Off is a strength, not a skipped call")
    }

    /**
     * The machine gun, which is what the throttle exists for.
     *
     * `Cue.Move` fires once per engine column step and a drag steps a column
     * every frame or two, so ten of them can land inside a fifth of a second.
     * Ten clicks in 200ms is not steering feedback, it is a fault noise.
     */
    @Test
    fun aFastDrag_doesNotMachineGunTheMoveClick() {
        val sounds = RecordingSoundPlayer()
        val time = TestTimeSource()
        val cues = Cues(RecordingHapticEngine(), sounds, HapticsSetting.Strong, time)

        repeat(10) {
            cues.play(Cue.Move)
            time += 20.milliseconds
        }

        assertEquals(4, sounds.played.size, "one per 50ms window across 200ms, not ten")
    }

    /**
     * Not a debounce. The first click of a drag is the one that tells the player
     * the board took the gesture, so it always plays; only its followers wait.
     */
    @Test
    fun theFirstClick_alwaysPlays() {
        val sounds = RecordingSoundPlayer()
        val cues = Cues(RecordingHapticEngine(), sounds, HapticsSetting.Strong, TestTimeSource())

        cues.play(Cue.Move)

        assertEquals(1, sounds.played.size)
    }

    @Test
    fun aDeliberateTap_isNeverSwallowed() {
        val sounds = RecordingSoundPlayer()
        val time = TestTimeSource()
        val cues = Cues(RecordingHapticEngine(), sounds, HapticsSetting.Strong, time)

        repeat(5) {
            cues.play(Cue.Move)
            time += HumanTapInterval
        }

        assertEquals(5, sounds.played.size, "nobody taps a button faster than this")
    }

    /**
     * The throttle is scoped to the clicks, and getting that wrong would be worse
     * than not having it: a merge dropped for arriving too soon after a move is a
     * missing note in the one run SPEC 21 says to protect.
     */
    @Test
    fun theThrottle_neverTouchesACascade() {
        val sounds = RecordingSoundPlayer()
        val time = TestTimeSource()
        val cues = Cues(RecordingHapticEngine(), sounds, HapticsSetting.Strong, time)

        cues.play(Cue.Move)
        repeat(6) { step ->
            cues.play(Cue.Merge(step = step + 1))
            time += 1.milliseconds
        }
        cues.play(Cue.Burst)

        assertEquals(8, sounds.played.size, "every merge and the burst survive")
        assertTrue(sounds.played.map { it.first }.contains(Sound.Burst))
    }

    /**
     * The `dumpsys` line this exists for, and the ruling on it: **the move is
     * given up on purpose.**
     *
     * `cancelled_superseded` against a `Cue.Move` a millisecond before a
     * `Cue.Merge` is not a bug, it is the only sane answer. The merge is the board
     * replying to the steer, and one millisecond into a twelve is not something a
     * hand can feel, so the vibrator is better spent on the reply. Both still
     * reach the engine — the arbitration decides who holds it, not who is allowed
     * to ask.
     */
    @Test
    fun aMoveIsGivenUpToTheMergeBehindIt() {
        val sounds = RecordingSoundPlayer()
        val time = TestTimeSource()
        val haptics = RecordingHapticEngine(time)
        val cues = Cues(haptics, sounds, HapticsSetting.Strong, time)

        cues.play(Cue.Move)
        time += 1.milliseconds
        cues.play(Cue.Merge(step = 1))

        assertEquals(
            listOf(HapticIntensity.Light, HapticIntensity.Medium),
            haptics.played.map { it.first },
            "the merge takes the vibrator off the move, which is the intended loss",
        )
        assertEquals(2, sounds.played.size, "neither half is ever lost to the other")
    }

    /**
     * The same collision the other way round, which is the half that cost
     * something.
     *
     * A steer buffered during a resolution is replayed the instant the last frame
     * ends (`finishResolution`), so a `LEVEL UP` or a chained merge could be
     * cut off by a click for an input the player made a second ago. Now the click
     * is refused and the merge is felt whole.
     */
    @Test
    fun aMergeIsNeverCutByTheSteerBehindIt() {
        val sounds = RecordingSoundPlayer()
        val time = TestTimeSource()
        val haptics = RecordingHapticEngine(time)
        val cues = Cues(haptics, sounds, HapticsSetting.Strong, time)

        cues.play(Cue.Merge(step = 3))
        time += 1.milliseconds
        cues.play(Cue.Move)

        assertEquals(listOf(HapticIntensity.Medium), haptics.played.map { it.first })
        assertEquals(
            listOf(Sound.Merge, Sound.Move),
            sounds.played.map { it.first },
            "only the buzz is arbitrated; samples mix",
        )
    }

    /**
     * SPEC 21's row burst, which is the effect with the most to lose.
     *
     * Its waveform runs 390ms and the frame that holds it runs 420 — 168 with
     * reduce motion on. So the next cascade step used to land in the middle of the
     * decaying roll and take it away, which is most of the burst. A merge is worth
     * less than a burst, so it now keeps its hands off.
     */
    @Test
    fun aRowBurstIsNeverCutShortByTheCascadeAroundIt() {
        val sounds = RecordingSoundPlayer()
        val time = TestTimeSource()
        val haptics = RecordingHapticEngine(time)
        val cues = Cues(haptics, sounds, HapticsSetting.Strong, time)

        cues.play(Cue.Burst)
        time += ReducedRowBurstHold
        cues.play(Cue.Merge(step = 4))

        assertEquals(listOf(HapticIntensity.Burst), haptics.played.map { it.first })
        assertEquals(2, sounds.played.size, "the merge is still heard, just not felt")
    }

    /** Once the roll has actually finished, nothing is being protected. */
    @Test
    fun aBurstThatHasFinished_holdsNothingBack() {
        val time = TestTimeSource()
        val haptics = RecordingHapticEngine(time)
        val cues = Cues(haptics, RecordingSoundPlayer(), HapticsSetting.Strong, time)

        cues.play(Cue.Burst)
        time += HapticIntensity.Burst.feltFor
        cues.play(Cue.Merge(step = 4))

        assertEquals(
            listOf(HapticIntensity.Burst, HapticIntensity.Medium),
            haptics.played.map { it.first },
        )
    }

    /**
     * Stacking out is the one thing allowed to interrupt a burst, because the
     * burst is now describing a board that has just killed the player.
     */
    @Test
    fun stackingOutTakesTheVibratorOffEverything() {
        val time = TestTimeSource()
        val haptics = RecordingHapticEngine(time)
        val cues = Cues(haptics, RecordingSoundPlayer(), HapticsSetting.Strong, time)

        cues.play(Cue.Burst)
        time += 10.milliseconds
        cues.play(Cue.StackedOut)

        assertEquals(
            listOf(HapticIntensity.Burst, HapticIntensity.DoubleSharp),
            haptics.played.map { it.first },
        )
    }

    /**
     * The seam that fires three cues in one dispatch and nobody had counted.
     *
     * `GameViewModel.lock` sends `Cue.Lock`, then the danger transition the same
     * lock caused, then starts playback — whose first frame can be a merge with a
     * hold of zero in front of it. Three haptics in the same millisecond. The
     * landing tap is felt, the danger warning of identical weight does not
     * cancel it to say the same thing again, and the merge replaces both because
     * it is what actually happened.
     */
    @Test
    fun aLockDroppingStraightIntoAMerge_arrivesAsOneThing() {
        val sounds = RecordingSoundPlayer()
        val time = TestTimeSource()
        val haptics = RecordingHapticEngine(time)
        val cues = Cues(haptics, sounds, HapticsSetting.Strong, time)

        cues.play(Cue.Lock)
        cues.play(Cue.DangerEnter)
        cues.play(Cue.Merge(step = 1))

        assertEquals(
            listOf(HapticIntensity.Light, HapticIntensity.Medium),
            haptics.played.map { it.first },
        )
        assertEquals(3, sounds.played.size)
    }

    /**
     * The assertion that would have caught the original `dumpsys` line, at the
     * seam below [Cues] where it actually happened.
     *
     * A whole drop's worth of cues at the tightest pace the game can play them —
     * reduce motion on, which scales every hold to 40% — replayed against a
     * controlled clock, and then every consecutive pair of calls that reached the
     * engine is checked against what the vibrator will do with them. Two
     * overlapping requests are only allowed where the second one outranks the
     * first, which is the definition of the arbitration rather than a restatement
     * of it: any future cue, hold or reordering that lets a lesser haptic land on
     * a greater one fails here without anybody having to own a device.
     */
    @Test
    fun noHapticIsEverCutShortByALesserOne() {
        val time = TestTimeSource()
        val haptics = RecordingHapticEngine(time)
        val cues = Cues(haptics, RecordingSoundPlayer(), HapticsSetting.Strong, time)

        ReducedMotionDrop.forEach { (cue, hold) ->
            cues.play(cue)
            time += hold
        }

        haptics.played.indices.drop(1).forEach { index ->
            val previous = haptics.played[index - 1].first
            val current = haptics.played[index].first
            val gap = haptics.at[index] - haptics.at[index - 1]
            assertTrue(
                gap >= previous.feltFor || current.rank > previous.rank,
                "$current landed $gap into $previous, which is still being felt",
            )
        }
    }

    /**
     * A queue was the other candidate fix and this is why it is not the one.
     *
     * Every haptic the drop admits is issued at the instant its frame asked for
     * it, so the felt half of the cascade stays on the same clock as the drawn
     * half however long the cascade runs. A queue would push each held-back buzz
     * onto the tail of the one in front of it, and a row burst alone is 390ms of
     * tail — by the end of a long chain the vibrations would be describing a board
     * that is already gone.
     */
    @Test
    fun aLongCascadeNeverDriftsBehindTheAnimation() {
        val time = TestTimeSource()
        val haptics = RecordingHapticEngine(time)
        val cues = Cues(haptics, RecordingSoundPlayer(), HapticsSetting.Strong, time)

        var offset = Duration.ZERO
        val asked = mutableListOf<Duration>()
        ReducedMotionDrop.forEach { (cue, hold) ->
            val before = haptics.played.size
            cues.play(cue)
            if (haptics.played.size > before) asked += offset
            time += hold
            offset += hold
        }

        assertEquals(asked, haptics.at, "every buzz went out on the frame that asked for it")
        assertTrue(asked.size > 1, "the schedule has to actually admit a run of them")
    }
}

/** Long enough apart that no human hand could ask for two clicks any closer. */
private val HumanTapInterval: Duration = 120.milliseconds

/** `Motion.RowBurstMillis` with reduce motion's 0.4 on it, which is the tight case. */
private val ReducedRowBurstHold: Duration = 168.milliseconds

/**
 * One drop's cues and the holds between them, at the pace `GameViewModel` plays
 * them with reduce motion switched on.
 *
 * A lock into a merge with no beat between (the hard drop bonus frame holds for
 * zero), three chained merges with gravity settling under each, the row burst the
 * last one caused, a level up, and the steer the player buffered during all of it
 * replaying the moment the resolution ends. The holds are `Motion`'s own numbers
 * scaled by 0.4; frames that carry no cue are folded into the hold in front of
 * them, because a cue is what this is measuring.
 */
private val ReducedMotionDrop: List<Pair<Cue, Duration>> = listOf(
    Cue.Lock to Duration.ZERO,
    Cue.Merge(step = 1) to 84.milliseconds + 72.milliseconds,
    Cue.Merge(step = 2) to 120.milliseconds + 72.milliseconds,
    Cue.Merge(step = 3) to 120.milliseconds,
    Cue.Burst to ReducedRowBurstHold + 72.milliseconds,
    Cue.LevelUp to 24.milliseconds,
    Cue.Move to Duration.ZERO,
)

private class RecordingSoundPlayer : SoundPlayer {
    val played = mutableListOf<Pair<Sound, Int>>()
    override fun play(sound: Sound, pitchSteps: Int) {
        played += sound to pitchSteps
    }
}

/**
 * Records *when* as well as what, because a haptic that arrives is only correct
 * if it arrives while the vibrator is free. Defaults to its own clock so the
 * tests that do not care about time need arrange nothing.
 */
private class RecordingHapticEngine(timeSource: TimeSource = TestTimeSource()) : HapticEngine {
    private val start = timeSource.markNow()
    val played = mutableListOf<Pair<HapticIntensity, Float>>()
    val at = mutableListOf<Duration>()

    override fun play(intensity: HapticIntensity, strength: Float) {
        played += intensity to strength
        at += start.elapsedNow()
    }
}
