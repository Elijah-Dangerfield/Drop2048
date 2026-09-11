package com.dangerfield.drop2048.libraries.achievements

import com.dangerfield.drop2048.libraries.cascade.MergeKind
import com.dangerfield.drop2048.libraries.cascade.ResolutionStep
import com.dangerfield.drop2048.libraries.cascade.Transition
import kotlinx.serialization.Serializable

/**
 * The four things SPEC 15 asks about a run that `run_record` does not already
 * carry, folded one lock at a time out of the transcript.
 *
 * **Every field here is read from the transcript**, not observed by the screen.
 * SPEC 4.2 makes the transcript the single channel for everything a transition
 * did, and scoring already flows down it; a badge counted anywhere else would be
 * a second channel that can drift from the first, which is exactly how a "clear
 * the board" counter comes to disagree with the board-cleared bonus that paid
 * out beside it.
 *
 * It is `@Serializable` because it rides in the saved run: a run resumed from
 * disk that reported zero board clears would lose a badge the player earned
 * before they backgrounded the app.
 *
 * [dangerDrops] is the only running field, and it is the current streak rather
 * than a total — SPEC 15 asks for "survive 10 drops in the danger state", which
 * is ten in a row and not ten scattered across a run. [longestDangerRun] is its
 * high-water mark and is the one an achievement reads.
 */
@Serializable
data class RunFacts(
    /** SPEC 7's board-cleared bonus, counted. The rarest frame in the game. */
    val boardsCleared: Int = 0,

    /** Row bursts that took at least [STONES_FOR_A_BADGE] Stones with them. */
    val stoneBursts: Int = 0,

    /** Wildcard merges that produced a 2048 — SPEC 5.2's screenshot moment. */
    val wildcardBursts: Int = 0,

    /** Consecutive drops survived while row 1 was occupied, right now. */
    val dangerDrops: Int = 0,

    /** The high-water mark of [dangerDrops]. */
    val longestDangerRun: Int = 0,
) {

    /**
     * Folds one locked drop in. Pure, and safe to call exactly once per lock —
     * [dangerDrops] counts drops, so calling it twice for one block would count
     * that block twice.
     *
     * A drop that ended the run did not survive it, so the danger streak closes
     * rather than counting the block that killed them.
     */
    fun fold(transition: Transition): RunFacts {
        var boards = boardsCleared
        var stones = stoneBursts
        var wilds = wildcardBursts

        transition.transcript.steps.forEach { step ->
            when (step) {
                is ResolutionStep.BoardCleared -> boards++
                is ResolutionStep.Burst -> if (step.stones >= STONES_FOR_A_BADGE) stones++
                is ResolutionStep.Merge ->
                    if (step.kind == MergeKind.WILDCARD && step.result.isTerminal) wilds++

                is ResolutionStep.Detonation,
                is ResolutionStep.Gravity,
                is ResolutionStep.HardDropBonus,
                is ResolutionStep.LevelUp,
                is ResolutionStep.Survival,
                -> Unit
            }
        }

        val inDanger = transition.state.inDanger && !transition.state.isOver
        val run = if (inDanger) dangerDrops + 1 else 0

        return copy(
            boardsCleared = boards,
            stoneBursts = stones,
            wildcardBursts = wilds,
            dangerDrops = run,
            longestDangerRun = maxOf(longestDangerRun, run),
        )
    }

    companion object {
        val Empty: RunFacts = RunFacts()

        /** SPEC 15's "burst a row with three Stones". */
        const val STONES_FOR_A_BADGE: Int = 3
    }
}
