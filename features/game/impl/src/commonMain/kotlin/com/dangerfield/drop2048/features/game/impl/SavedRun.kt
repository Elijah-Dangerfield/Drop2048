package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.cascade.Transcript
import com.dangerfield.drop2048.libraries.progress.GameMode
import kotlinx.serialization.Serializable

/**
 * Everything about a live run that the engine does not hold.
 *
 * The engine counts drops and carries the level (SPEC 4.1) and deliberately has
 * no clock, so duration, the highest tier *reached* (decision D7) and the
 * cascade tallies SPEC 11 records are accumulated out here as the run happens.
 * They have to survive a resume for the same reason the board does: a run
 * resumed from disk that reports zero merges would write a false `run_record`.
 *
 * [playedMs] is time the run was actually being played, closed off at every
 * pause. See [RunRecord.durationMs][com.dangerfield.drop2048.libraries.progress.RunRecord.durationMs].
 */
@Serializable
data class RunTally(
    val merges: Int = 0,
    val bursts: Int = 0,
    val longestCascade: Int = 0,
    /** Points value of the highest tier reached, 0 before the first merge. */
    val highestTier: Int = 0,
    val playedMs: Long = 0,
)

/**
 * A resolution caught in the act (SPEC 18.9).
 *
 * The transcript is what the engine returned; [beforeBoard] and [scoreBefore]
 * are what the screen was showing when it started. Those three re-derive every
 * frame through `framesFor`, which is why nothing here stores the frames
 * themselves — they are a pure function of the transcript and already tested as
 * one.
 *
 * [frameIndex] is how far playback had got. It is the same field the ViewModel
 * uses to resume from a pause, so backgrounding mid-cascade and pausing
 * mid-cascade are one code path rather than two that can disagree.
 */
@Serializable
data class SavedResolution(
    val beforeBoard: Board,
    val scoreBefore: Long,
    val transcript: Transcript,
    val frameIndex: Int,
)

/**
 * The in-progress run as it goes to disk (SPEC 11).
 *
 * [seed] and [mode] are carried rather than recovered because they cannot be
 * recovered: the RNG state inside [state] has already advanced past the seed,
 * and `run_record` wants the seed the run started from so the run can be
 * replayed byte for byte.
 */
@Serializable
data class SavedRun(
    val state: GameState,
    val tally: RunTally,
    val seed: Long,
    val mode: GameMode,
    val resolution: SavedResolution? = null,
)
