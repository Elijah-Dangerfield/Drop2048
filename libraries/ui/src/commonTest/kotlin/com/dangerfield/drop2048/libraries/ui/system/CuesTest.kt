package com.dangerfield.drop2048.libraries.ui.system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TestTimeSource

/**
 * The two halves of a [Cue] arriving together, and the one cue that has to be
 * held back.
 *
 * There is no sample bank in this repository and there is no way to feel a
 * haptic in a test, so what is actually assertable is the seam: that one
 * `play` reaches both engines, that the pitch offset and the strength that
 * arrive are the right ones, and that a burst of movement does not arrive as a
 * burst of clicks. Recording fakes rather than the silent defaults, because
 * `Cues.Silent` proves nothing — it is what every other test in the repo is
 * already running against.
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
            cues.play(Cue.Nudge)
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
}

/** Long enough apart that no human hand could ask for two clicks any closer. */
private val HumanTapInterval: Duration = 120.milliseconds

private class RecordingSoundPlayer : SoundPlayer {
    val played = mutableListOf<Pair<Sound, Int>>()
    override fun play(sound: Sound, pitchSteps: Int) {
        played += sound to pitchSteps
    }
}

private class RecordingHapticEngine : HapticEngine {
    val played = mutableListOf<Pair<HapticIntensity, Float>>()
    override fun play(intensity: HapticIntensity, strength: Float) {
        played += intensity to strength
    }
}
