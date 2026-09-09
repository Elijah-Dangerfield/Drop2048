package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.MergeKind
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
    /** What the board shouts on this frame, if anything (SPEC 8.2). */
    val callout: GameCallout?,
)

/**
 * The oversized line the board throws up over itself when something worth
 * noticing happens.
 *
 * The handoff draws three of these — `CHAIN ×N`, `ROW BUST!` and `LEVEL N` — and
 * SPEC 7 scores three more events that it never got told about: a bomb
 * detonation, a Wildcard resolving, and the board being cleared. **Every one of
 * those pays points, and a payout with no callout is a payout the player does not
 * connect to what they did.** The board-cleared bonus is the worst of the three:
 * it is the rarest thing in the game and, without this, the only evidence of it
 * is the score having moved more than expected.
 *
 * They are a sealed hierarchy rather than strings because `:features:game:impl`
 * decides *when*, `:libraries:resources` decides *what it says*, and the two
 * should not meet in a ViewModel.
 */
sealed interface GameCallout {

    /** SPEC 8.2, from step 2 upward. Step 1 is a merge, not a chain. */
    data class Chain(val step: Int) : GameCallout

    /** A 2048 taking its row with it. Outranks everything, including its own chain. */
    data object RowBust : GameCallout

    /** A Bomb and its four neighbours. */
    data object Detonation : GameCallout

    /** A Wildcard taking a neighbour's value doubled (SPEC 5.2). */
    data object Wildcard : GameCallout

    /** The rarest frame in the game (SPEC 7). */
    data object BoardCleared : GameCallout

    data class LevelUp(val level: Int) : GameCallout
}

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
            callout = step.callout(),
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
    is ResolutionStep.Burst -> Motion.RowBurstMillis
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
    is ResolutionStep.Survival,
    is ResolutionStep.BoardCleared,
    -> null
}

/**
 * What this step is worth saying out loud.
 *
 * One per frame, so the ordering question the prototype answers with an `if`
 * chain (`ROW BUST!` beats `CHAIN ×N`) does not arise: a burst and the merge that
 * caused it are two frames, the burst is the later one, and a later callout
 * simply replaces the one before it. That is the same precedence the design
 * wanted, arrived at by the structure rather than by a rule somebody has to
 * remember to keep.
 *
 * A plain step-1 merge says nothing. It already has a pop, a sound and a score
 * roll, and a callout on every single landing is a callout nobody reads.
 */
private fun ResolutionStep.callout(): GameCallout? = when (this) {
    is ResolutionStep.Merge -> when {
        kind == MergeKind.WILDCARD -> GameCallout.Wildcard
        step >= ChainedFrom -> GameCallout.Chain(step)
        else -> null
    }

    is ResolutionStep.Detonation -> GameCallout.Detonation
    is ResolutionStep.Burst -> GameCallout.RowBust
    is ResolutionStep.BoardCleared -> GameCallout.BoardCleared
    is ResolutionStep.LevelUp -> GameCallout.LevelUp(level)
    is ResolutionStep.Gravity,
    is ResolutionStep.Survival,
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
