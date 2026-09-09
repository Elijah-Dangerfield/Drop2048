package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.ResolutionStep
import com.dangerfield.drop2048.libraries.cascade.Transcript
import com.dangerfield.drop2048.libraries.ui.system.Cue
import com.dangerfield.drop2048.libraries.ui.system.Motion

/**
 * One drawable moment of a resolution: the board as it stands *after* [step],
 * the running score at that moment, how long it should be held, and what it
 * sounds like.
 *
 * The engine is finished before any of this is on screen (SPEC 4.2). What the
 * transcript does not hand back is the board *between* steps — it returns the
 * final one — so the frames are re-derived here by replaying each step onto the
 * board as it stood before the lock. Every step type says exactly which cells it
 * touched, so the replay is total rather than a reconstruction: there is no
 * inference and nothing to get subtly wrong.
 *
 * That re-derivation is the reason this is a pure function in its own file. It
 * is the only part of the playback with any arithmetic in it, and it is testable
 * without a ViewModel, a clock or a renderer.
 */
data class PlaybackFrame(
    val board: Board,
    val cascadeStep: Int,
    val score: Long,
    val holdMillis: Int,
    val cue: Cue?,
    val chained: Boolean,
    /** The cell the chaining merge landed in, so the callout floats off it. */
    val chainAt: Cell?,
)

/**
 * The frames a [transcript] plays as, starting from the board [before] the
 * resolution ran (the locked block already placed) and the score before it
 * scored.
 *
 * Steps that award points without moving a block — the hard drop bonus, survival
 * and level up (SPEC 4.2) — still advance the score, because a score that jumps
 * once at the end of a six-step cascade throws away the feedback SPEC 7 exists
 * to deliver. Only the ones that also have something to *say* get a cue.
 */
fun framesFor(before: Board, scoreBefore: Long, transcript: Transcript): List<PlaybackFrame> {
    var board = before
    var score = scoreBefore
    val frames = mutableListOf<PlaybackFrame>()
    transcript.steps.forEach { step ->
        board = board.after(step)
        score += step.points
        frames += PlaybackFrame(
            board = board,
            cascadeStep = step.step,
            score = score,
            holdMillis = step.holdMillis(),
            cue = step.cue(),
            chained = step is ResolutionStep.Merge && step.step >= ChainedFrom,
            chainAt = (step as? ResolutionStep.Merge)?.into?.takeIf { step.step >= ChainedFrom },
        )
    }
    return frames
}

private fun Board.after(step: ResolutionStep): Board = when (step) {
    is ResolutionStep.Merge -> withAll(
        mapOf(
            step.initiator to null,
            step.partner to null,
            step.into to NumberBlock(step.result),
        )
    )

    is ResolutionStep.Detonation -> withAll((step.destroyed + step.bomb).associateWith { null })

    is ResolutionStep.Burst -> withAll(step.cleared.associateWith { null })

    is ResolutionStep.Gravity -> settled(step)

    is ResolutionStep.HardDropBonus,
    is ResolutionStep.Survival,
    is ResolutionStep.LevelUp,
    is ResolutionStep.BoardCleared,
    -> this
}

/**
 * Every block gravity moved, lifted before any of them lands.
 *
 * Applying the moves one at a time would let a block land in a cell another
 * block has not vacated yet, and a column falling two rows at once would erase
 * itself. The engine emits the moves as a set for that reason, so they are
 * applied as one.
 */
private fun Board.settled(step: ResolutionStep.Gravity): Board {
    val lifted: Map<Cell, Block?> = step.moves.associate { it.from to this[it.from] }
    val cleared: Map<Cell, Block?> = step.moves.associate { it.from to null }
    val landed: Map<Cell, Block?> = step.moves.associate { it.to to lifted.getValue(it.from) }
    return withAll(cleared + landed)
}

private fun ResolutionStep.holdMillis(): Int = when (this) {
    is ResolutionStep.Burst -> Motion.BurstMillis
    is ResolutionStep.HardDropBonus,
    is ResolutionStep.Survival,
    is ResolutionStep.LevelUp,
    -> ScoreOnlyMillis

    else -> Motion.CascadeStepMillis
}

/**
 * There is no `Sound.BoardCleared`, and SPEC 7 asks for one. It plays silent
 * here rather than borrowing the level-up sample, because a wrong sound is
 * harder to notice than a missing one and C3a is where the bank gets authored.
 */
private fun ResolutionStep.cue(): Cue? = when (this) {
    is ResolutionStep.Merge -> Cue.Merge(step = step, big = result.points >= BigMergeFrom)
    is ResolutionStep.Detonation -> Cue.Bomb
    is ResolutionStep.Burst -> Cue.Burst
    is ResolutionStep.LevelUp -> Cue.LevelUp
    is ResolutionStep.Gravity,
    is ResolutionStep.HardDropBonus,
    is ResolutionStep.Survival,
    is ResolutionStep.BoardCleared,
    -> null
}

/** SPEC 9's "merge into 256+" — heavier sample, heavier buzz. */
private const val BigMergeFrom = 256

/** SPEC 8.2 calls out `CHAIN x2` upward, so step 1 is a merge and not a chain. */
private const val ChainedFrom = 2

/**
 * Long enough for the score roll to be seen starting, short enough that a level
 * up does not feel like the board stopped. These frames move nothing.
 */
private const val ScoreOnlyMillis = 60
