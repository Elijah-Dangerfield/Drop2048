package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.runtime.Immutable

/**
 * One thing the game can be felt and heard doing.
 *
 * Sound and haptic ship as one value, never as two calls that can drift apart
 * (SPEC 9, decision D3). Sodogku is the cautionary example: its `Feel` enum has
 * no audio side, its `Motion` object has no haptic side, and the two live in
 * different packages for no reason, which is most of why they never got paired.
 *
 * [pitchSteps] is the reason this is a value rather than an enum. SPEC 21 says
 * the ascending run of a long cascade is one of the two things worth keeping if
 * everything else is cut, and it only exists if the merge at step four *knows*
 * it is step four. [Merge] is the whole feature: one call, right buzz, right
 * sample, right pitch.
 */
@Immutable
data class Cue(
    val sound: Sound,
    val haptic: HapticIntensity,
    /** Semitones above the sample's natural pitch. Zero for everything but a cascade. */
    val pitchSteps: Int = 0,
) {
    companion object {

        /** A block arriving at the top of the board. Deliberately almost nothing. */
        val Spawn = Cue(Sound.Spawn, HapticIntensity.None)

        /** One column of sideways movement. Very short; a run of these is a texture, not a rhythm. */
        val Move = Cue(Sound.Move, HapticIntensity.Light)

        val HardDrop = Cue(Sound.HardDrop, HapticIntensity.Medium)

        val Lock = Cue(Sound.Lock, HapticIntensity.Light)

        /**
         * A merge at [step] of a cascade, one-based.
         *
         * The pitch climbs a step per level of the chain and then stops
         * climbing: past an octave it stops sounding like a run going somewhere
         * and starts sounding like a mistake, and a player deep enough into a
         * cascade to hit the cap is already being rewarded plenty.
         */
        fun Merge(step: Int, big: Boolean = false): Cue = Cue(
            sound = if (big) Sound.MergeBig else Sound.Merge,
            haptic = if (big) HapticIntensity.Heavy else HapticIntensity.Medium,
            pitchSteps = (step - 1).coerceIn(0, MaxPitchSteps),
        )

        /** The row burst. The longest sample in the game and the only sustained haptic. */
        val Burst = Cue(Sound.Burst, HapticIntensity.Burst)

        val Bomb = Cue(Sound.Bomb, HapticIntensity.Heavy)

        val DangerEnter = Cue(Sound.DangerEnter, HapticIntensity.Light)

        val DangerExit = Cue(Sound.DangerExit, HapticIntensity.None)

        val LevelUp = Cue(Sound.LevelUp, HapticIntensity.Heavy)

        /** Stacked out. A sharp double, so it does not read as one more merge. */
        val StackedOut = Cue(Sound.StackedOut, HapticIntensity.DoubleSharp)

        val UiTap = Cue(Sound.UiTap, HapticIntensity.Light)

        val UiBack = Cue(Sound.UiBack, HapticIntensity.Light)

        /** An octave. Past it a cascade stops climbing and starts squeaking. */
        const val MaxPitchSteps = 12
    }
}

/** The effects named in SPEC 9. One key per sample; the files arrive with the audio engine. */
enum class Sound {
    Spawn,
    Move,
    HardDrop,
    Lock,
    Merge,
    MergeBig,
    Burst,
    Bomb,
    DangerEnter,
    DangerExit,
    LevelUp,
    StackedOut,
    UiTap,
    UiBack,
}

/**
 * The five things the phone can be asked to feel like, plus silence.
 *
 * Five, not two. Compose Multiplatform exposes exactly two `HapticFeedbackType`
 * values, and Sodogku collapses four game events onto them — that is the ceiling
 * it hit, and SPEC 9 needs more than it. [Burst] in particular is a *pattern*
 * rather than a hit, and there is no way to express it in the shared API at all.
 * The platform layer behind this is [HapticEngine].
 */
enum class HapticIntensity {
    None,
    Light,
    Medium,
    Heavy,

    /** A sustained, decaying rumble. The row burst, and nothing else. */
    Burst,

    /** Two sharp hits in quick succession. Stacked out, and nothing else. */
    DoubleSharp,
}

/** The player's setting (SPEC 9). Scales every haptic; `Off` silences them entirely. */
enum class HapticsSetting {
    Off,
    Light,
    Strong,
}

/**
 * Where a [Cue]'s audio half goes.
 *
 * A seam rather than an implementation because C2 has no audio engine and should
 * not grow one: the design system's job is to know that a merge at step four is
 * four semitones up, not to own a sample bank. Defaults to [Silent], so every
 * preview, screenshot test and unit test is quiet without arranging to be.
 */
interface SoundPlayer {
    fun play(sound: Sound, pitchSteps: Int)

    companion object {
        val Silent = object : SoundPlayer {
            override fun play(sound: Sound, pitchSteps: Int) = Unit
        }
    }
}
