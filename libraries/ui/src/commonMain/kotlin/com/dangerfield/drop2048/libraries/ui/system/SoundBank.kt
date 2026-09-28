package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.runtime.Composable
import kotlin.math.pow

/**
 * Where each platform looks for a sample, and what "pitched up per cascade step"
 * arithmetically means.
 *
 * ### The samples do not exist yet, and this is the shape they slot into
 *
 * There is no sample bank in this repository. Authoring one is the owner's, and
 * SPEC 9 is the brief. Everything else is built: [rememberPlatformSoundPlayer]
 * has a real engine behind it on both platforms, [Cues] routes every [Cue]
 * through it, and the cue sequence a run produces is pinned by tests. Dropping
 * files in is the only remaining step, and no code changes with them.
 *
 * **One file per [Sound], named `<key>.ogg`, in a folder called `audio`:**
 *
 * - Android: `libraries/ui/src/androidMain/assets/audio/`. A library module's
 *   assets are merged into the APK, so nothing in `:apps:compose` has to know.
 * - iOS: added to the Xcode project's resources in `apps/ios/`, either as a blue
 *   folder reference named `audio` or dropped in flat. Both are looked for, so
 *   the owner does not have to know which kind of drag Xcode did.
 *
 * A sample that is missing is not an error. The engine logs the set it could not
 * find, once, and that [Sound] stays silent while every other one plays — which
 * is what makes a half-finished bank playable rather than a build failure.
 *
 * ### Why the format differs per platform
 *
 * This used to say the two platforms shared Ogg Vorbis, "the one lossy format
 * Android's `SoundPool` and iOS's `AVAudioPlayer` both decode without a
 * wrapper". The second half of that is false, and the app shipped mute on iOS
 * because of it: **there is no Vorbis decoder in the iOS system libraries**, so
 * `AVAudioPlayer` refused all fifteen samples and every cue fell back to
 * nothing. Playing Vorbis on iOS means bundling libogg and libvorbis and
 * feeding PCM to `AVAudioEngine` yourself.
 *
 * The trap is that **macOS decodes Vorbis fine**, so a `swift` snippet on a
 * laptop loads these files and proves nothing about the device. Verify audio
 * formats against iOS, not against the machine you are sitting at.
 *
 * So Android keeps Ogg, which `SoundPool` decodes and which is a tenth the
 * size, and iOS gets WAV, which it is guaranteed to decode and which the
 * generator already produces on the way to the encoder. Keep them short and
 * keep them mono: `SoundPool` decodes into memory and a stereo pad is the one
 * way to make a bank of clicks expensive.
 */
/**
 * The sample container this platform can actually decode.
 *
 * Not a cosmetic difference and not safe to unify: iOS has no Vorbis decoder,
 * so an `ogg` here makes the app silent with nothing in the UI to say so. See
 * [SoundBank] for the full reasoning.
 */
expect val SoundFileExtension: String

object SoundBank {

    /** The folder both platforms look in, under whichever resource root they have. */
    const val Directory = "audio"

    /** `merge` becomes `merge.ogg` on Android and `merge.wav` on iOS. */
    fun fileName(sound: Sound): String = "${sound.key}.$SoundFileExtension"

    /**
     * The playback rate that raises a sample by [pitchSteps] semitones.
     *
     * Equal temperament, so a step is the twelfth root of two and twelve steps is
     * exactly `2.0` — an octave, at double speed. **The sample is resampled
     * rather than pitch-shifted**, so a merge at step six is both higher and
     * shorter, and that is the intended sound rather than a compromise: it is
     * what every arcade cascade has done since the eighties, and a
     * duration-preserving shift makes a six-step run drag.
     *
     * The clamp is [Cue.MaxPitchSteps] because both platforms cap playback rate
     * at 2.0x. Doing it here means the two engines cannot disagree about what
     * step thirteen sounds like.
     */
    fun rate(pitchSteps: Int): Float =
        RateBase.pow(pitchSteps.coerceIn(0, Cue.MaxPitchSteps) / SemitonesPerOctave)

    private const val RateBase = 2f
    private const val SemitonesPerOctave = 12f
}

/**
 * The real sample bank for this platform, or [SoundPlayer.Silent] where there is
 * none.
 *
 * [enabled] is SPEC 9's effects switch, held on `PlayerSettings.soundEnabled`.
 * Turning it off routes to silence rather than tearing the bank down, so flipping
 * it back on does not cost the player the first few sounds while the samples
 * reload.
 */
@Composable
expect fun rememberPlatformSoundPlayer(enabled: Boolean): SoundPlayer
