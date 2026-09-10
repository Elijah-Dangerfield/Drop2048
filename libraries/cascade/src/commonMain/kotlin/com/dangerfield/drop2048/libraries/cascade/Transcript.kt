package com.dangerfield.drop2048.libraries.cascade

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One block travelling from one cell to another during a gravity settle. */
@Serializable
data class BlockMove(val from: Cell, val to: Cell)

/** Whether a merge was two matching values or a Wildcard taking a neighbour's. */
@Serializable
enum class MergeKind { VALUE, WILDCARD }

/**
 * One entry in a [Transcript].
 *
 * [step] is the cascade step the entry belongs to, starting at 1. Awards that
 * are not part of a cascade (survival, level up) carry step 0, which is also
 * what the audio pitch in SPEC 9 keys off — step 0 is "no pitch offset".
 *
 * There is no input-driven step left. `HardDropBonus` was the only one, and
 * decision D11 removed both the input and the award; see [Scoring] for why the
 * ▼ nudge did not inherit it.
 */
@Serializable
sealed interface ResolutionStep {
    val step: Int
    val points: Int

    /**
     * Two blocks combined. [into] is the **partner's** cell in every orientation
     * (SPEC 4.3) — a vertical merge's lower cell is the partner's cell, so the
     * two orientations are one rule. It therefore always equals [partner]; it
     * stays a separate field so the UI can animate "initiator travels to into"
     * without having to know the position rule, and so a future rule change is a
     * change to one value rather than to what [partner] means.
     */
    @Serializable
    @SerialName("merge")
    data class Merge(
        override val step: Int,
        val initiator: Cell,
        val partner: Cell,
        val into: Cell,
        val result: BlockValue,
        val kind: MergeKind,
        override val points: Int,
    ) : ResolutionStep

    /**
     * A Bomb destroying itself and its orthogonal neighbours. [destroyed]
     * excludes the bomb's own cell, so a bomb with no neighbours scores nothing
     * (SPEC 18.4).
     */
    @Serializable
    @SerialName("detonation")
    data class Detonation(
        override val step: Int,
        val bomb: Cell,
        val destroyed: List<Cell>,
        override val points: Int,
    ) : ResolutionStep

    /**
     * A 2048 was created in [row], so the whole row clears — Stones included
     * (SPEC 18.5).
     *
     * [stones] is how many of [cleared] held a Stone. It is counted here rather
     * than derived downstream because the only board that could answer the
     * question is the one this step is about to destroy: a burst three steps
     * into a cascade sits on a board no caller holds, and reconstructing it
     * means replaying the resolution outside the resolver. The count is not
     * scored — the burst bonus is per block regardless of what the block was —
     * and exists because SPEC 15 asks for a badge for clearing a row with three
     * of them.
     */
    @Serializable
    @SerialName("burst")
    data class Burst(
        override val step: Int,
        val row: Int,
        val cleared: List<Cell>,
        override val points: Int,
        val stones: Int = 0,
    ) : ResolutionStep

    /** Everything that fell after a merge, burst or detonation. Scores nothing. */
    @Serializable
    @SerialName("gravity")
    data class Gravity(
        override val step: Int,
        val moves: List<BlockMove>,
    ) : ResolutionStep {
        override val points: Int get() = 0
    }

    @Serializable
    @SerialName("survival")
    data class Survival(
        val level: Int,
        override val points: Int,
    ) : ResolutionStep {
        override val step: Int get() = 0
    }

    @Serializable
    @SerialName("levelUp")
    data class LevelUp(
        val level: Int,
        override val points: Int,
    ) : ResolutionStep {
        override val step: Int get() = 0
    }

    @Serializable
    @SerialName("boardCleared")
    data class BoardCleared(
        override val step: Int,
        override val points: Int,
    ) : ResolutionStep
}

/**
 * What `RESOLVE` returns instead of a board (SPEC 4.2).
 *
 * The engine is finished by the time the first block moves on screen; the UI
 * replays these steps in order. That is what makes "input during resolution is
 * ignored" free rather than a special case, and what makes the cascade-step
 * audio pitch a `steps.map { it.step }`.
 *
 * Every point the transition awarded is in here, cascade or not, so
 * `next.score == previous.score + transcript.points` always holds. Nothing
 * scores through a second channel.
 */
@Serializable
data class Transcript(val steps: List<ResolutionStep> = emptyList()) {

    val points: Int get() = steps.sumOf { it.points }

    /** The deepest cascade step reached. 0 when nothing cascaded. */
    val depth: Int get() = steps.maxOfOrNull { it.step } ?: 0

    val merges: List<ResolutionStep.Merge> get() = steps.filterIsInstance<ResolutionStep.Merge>()

    val bursts: List<ResolutionStep.Burst> get() = steps.filterIsInstance<ResolutionStep.Burst>()

    val isEmpty: Boolean get() = steps.isEmpty()

    companion object {
        val Empty = Transcript()
    }
}
