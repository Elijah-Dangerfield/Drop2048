package com.dangerfield.drop2048.libraries.ui.system

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logging.KLog

@Composable
actual fun rememberPlatformSoundPlayer(enabled: Boolean): SoundPlayer {
    if (LocalInspectionMode.current) return SoundPlayer.Silent
    val context = LocalContext.current.applicationContext
    val player = remember(context) { SoundPoolPlayer(context) }
    DisposableEffect(player) { onDispose { player.release() } }
    return if (enabled) player else SoundPlayer.Silent
}

/**
 * `SoundPool`, which is the right tool and not the obvious one.
 *
 * `MediaPlayer` is what most examples reach for and it is wrong here: it decodes
 * on demand, so the first merge of a cascade arrives tens of milliseconds late,
 * and it plays one thing at a time, so the second merge cuts the first off.
 * `SoundPool` decodes every sample into memory once at launch, overlaps them, and
 * — the reason SPEC 21 cares — takes a **playback rate** per stream, which is
 * exactly how the cascade's ascending run is produced.
 *
 * Loading is asynchronous even though the decode is eager. A sample played in the
 * first fraction of a second after launch can therefore be dropped, which is a
 * real limitation and an invisible one: the player is on the start overlay at
 * that point, and the only cue there is a UI tap.
 *
 * A missing sample leaves its [Sound] out of the map and everything else playing.
 * The whole missing set is logged once rather than per play, so a half-authored
 * bank produces one readable line in logcat instead of a cue-rate flood.
 */
private class SoundPoolPlayer(context: Context) : SoundPlayer {

    private val pool = SoundPool.Builder()
        .setMaxStreams(MaxStreams)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val samples: Map<Sound, Int> = load(context)

    private fun load(context: Context): Map<Sound, Int> {
        val loaded = mutableMapOf<Sound, Int>()
        val missing = mutableListOf<String>()
        Sound.entries.forEach { sound ->
            val name = "${SoundBank.Directory}/${SoundBank.fileName(sound)}"
            val id = Catching {
                context.assets.openFd(name).use { pool.load(it, LoadPriority) }
            }.getOrNull()
            if (id == null || id == FailedToLoad) missing += name else loaded[sound] = id
        }
        if (missing.isNotEmpty()) {
            KLog.withTag(Tag).w("No sample for ${missing.size} of ${Sound.entries.size} sounds: $missing")
        }
        return loaded
    }

    override fun play(sound: Sound, pitchSteps: Int) {
        val id = samples[sound] ?: return
        Catching {
            pool.play(id, Volume, Volume, StreamPriority, NoLoop, SoundBank.rate(pitchSteps))
        }
    }

    fun release() {
        Catching { pool.release() }
    }

    private companion object {
        const val Tag = "Sound"

        /**
         * Enough for a deep cascade to overlap itself without any step being
         * stolen. A six-step chain at `Motion.CascadeStepMillis` puts six merges
         * inside about a second, and the burst that can follow them is the
         * longest sample in the game.
         */
        const val MaxStreams = 12

        const val LoadPriority = 1
        const val StreamPriority = 1
        const val NoLoop = 0
        const val Volume = 1f

        /** What `SoundPool.load` returns when it could not read the sample. */
        const val FailedToLoad = 0
    }
}
