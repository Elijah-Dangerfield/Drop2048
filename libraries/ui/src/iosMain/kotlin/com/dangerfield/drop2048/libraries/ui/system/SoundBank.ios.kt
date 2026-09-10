package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logging.KLog
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSURL

@Composable
actual fun rememberPlatformSoundPlayer(enabled: Boolean): SoundPlayer {
    val player = remember { BundledSoundPlayer() }
    return if (enabled) player else SoundPlayer.Silent
}

/**
 * A small pool of `AVAudioPlayer`s per sample, pitched by playback rate.
 *
 * ### Why a pool rather than one player per sound
 *
 * An `AVAudioPlayer` plays one thing at a time. Re-triggering it while it is
 * already sounding restarts it, which for a cascade means every merge cuts off
 * the one before it and the ascending run SPEC 21 is about arrives as a single
 * clipped note. So each sample gets [Voices] players over the same file and they
 * are used round-robin, which is what lets step four still be ringing when step
 * five starts.
 *
 * ### Why rate rather than `AVAudioUnitTimePitch`
 *
 * Setting `rate` resamples: a merge at step six comes out higher **and** shorter.
 * That is deliberate and it matches Android's `SoundPool`, so the two platforms
 * produce the same run rather than two different interpretations of SPEC 9. The
 * duration-preserving alternative needs an `AVAudioEngine` graph per voice and
 * makes a deep chain drag, because every note stays as long as the first.
 *
 * ### The session category
 *
 * `Ambient`, so the game honours the ring/silent switch and does not stop
 * whatever the player was already listening to. A puzzle game that kills
 * somebody's podcast to play a click is a game they turn the sound off in.
 */
@OptIn(ExperimentalForeignApi::class)
private class BundledSoundPlayer : SoundPlayer {

    private val voices: Map<Sound, SoundVoices> = load()

    private fun load(): Map<Sound, SoundVoices> {
        Catching {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryAmbient, null)
            session.setActive(true, null)
        }

        val loaded = mutableMapOf<Sound, SoundVoices>()
        val missing = mutableListOf<String>()
        Sound.entries.forEach { sound ->
            val voices = urlFor(sound)?.let { url -> SoundVoices.over(url) }
            if (voices == null) missing += SoundBank.fileName(sound) else loaded[sound] = voices
        }
        if (missing.isNotEmpty()) {
            KLog.withTag(Tag).w("No sample for ${missing.size} of ${Sound.entries.size} sounds: $missing")
        }
        return loaded
    }

    /**
     * The subdirectory first, then the bundle root.
     *
     * Dragging a folder into an Xcode project offers "create groups" and "create
     * folder references", and the two put the same files in different places. The
     * owner is authoring samples, not learning Xcode's resource model, so both
     * are looked for.
     */
    private fun urlFor(sound: Sound): NSURL? = Catching {
        NSBundle.mainBundle.URLForResource(
            name = sound.key,
            withExtension = SoundBank.Extension,
            subdirectory = SoundBank.Directory,
        ) ?: NSBundle.mainBundle.URLForResource(
            name = sound.key,
            withExtension = SoundBank.Extension,
        )
    }.getOrNull()

    override fun play(sound: Sound, pitchSteps: Int) {
        val player = voices[sound]?.next() ?: return
        Catching {
            player.rate = SoundBank.rate(pitchSteps)
            player.currentTime = 0.0
            player.play()
        }
    }

    private companion object {
        const val Tag = "Sound"
    }
}

/**
 * The same file, opened [Voices] times, handed out in turn.
 *
 * Three rather than one is what stops a cascade clipping itself; three rather
 * than twelve is because the merges are `Motion.CascadeStepMillis` apart and the
 * samples are clicks, so the fourth voice would never be reached.
 */
private class SoundVoices(private val players: List<AVAudioPlayer>) {

    private var next = 0

    fun next(): AVAudioPlayer = players[next].also { next = (next + 1) % players.size }

    companion object {
        const val Voices = 3

        @OptIn(ExperimentalForeignApi::class)
        fun over(url: NSURL): SoundVoices? {
            val players = (0 until Voices).mapNotNull {
                Catching {
                    AVAudioPlayer(contentsOfURL = url, error = null).apply {
                        enableRate = true
                        prepareToPlay()
                    }
                }.getOrNull()
            }
            return players.takeIf { it.isNotEmpty() }?.let { SoundVoices(it) }
        }
    }
}
