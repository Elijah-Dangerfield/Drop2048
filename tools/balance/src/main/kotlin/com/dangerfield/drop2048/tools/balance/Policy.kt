package com.dangerfield.drop2048.tools.balance

import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.GameState

/**
 * A scripted player. SPEC 4.4 makes these deliberately dumb: the question is not
 * whether a perfect player can win, it is whether the board clogs.
 *
 * None of them avoid death, stack for a burst, or use hold. A policy that played
 * well would answer a question nobody is asking, and would hide the clog the
 * spawn table causes behind its own skill.
 *
 * `kotlin.random.Random` is spelled out at every use because [Policy.Random] is
 * one of the policies and shadows the import inside this file.
 */
sealed interface Policy {

    val name: String

    /**
     * The column this policy wants, restricted to the ones [allowed] admits.
     *
     * The filter is what a clock costs a policy. Unclocked, every column is
     * allowed and the answer is the ceiling C1a measured; with [DropClock] in the
     * loop the filter is "columns the thumb can still reach", and the policy
     * takes the best of what is left rather than the best there is.
     */
    fun column(
        state: GameState,
        random: kotlin.random.Random,
        config: EngineConfig,
        allowed: (Int) -> Boolean = { true },
    ): Int

    companion object {
        val All: List<Policy> = listOf(Random, Greedy, Lookahead1)

        fun byName(name: String): Policy? = All.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    /** Uniform column, always hard drop. SPEC 4.4's floor: if this reaches level 6 the game is too easy. */
    data object Random : Policy {
        override val name = "random"

        override fun column(
            state: GameState,
            random: kotlin.random.Random,
            config: EngineConfig,
            allowed: (Int) -> Boolean,
        ): Int {
            val columns = (0 until config.cols).filter(allowed)
            if (columns.isEmpty()) return state.falling?.cell?.col ?: config.spawnColumn
            return columns[random.nextInt(columns.size)]
        }
    }

    /** Takes the immediate merge if one exists, else the emptiest column. */
    data object Greedy : Policy {
        override val name = "greedy"

        override fun column(
            state: GameState,
            random: kotlin.random.Random,
            config: EngineConfig,
            allowed: (Int) -> Boolean,
        ): Int = pick(state, random, config, allowed) { Harness.drop(state, it).transcript.merges.size }
    }

    /**
     * Greedy, plus the merges the *next* previewed block could make from the
     * board this drop leaves behind.
     *
     * SPEC 5.4 calls this policy the reason the two-block preview is
     * non-optional. It stays as dumb as [Greedy] in every other respect — in
     * particular it does not avoid a placement that ends the run, because the
     * moment a policy starts avoiding death it stops measuring whether the board
     * clogs.
     */
    data object Lookahead1 : Policy {
        override val name = "lookahead1"

        override fun column(
            state: GameState,
            random: kotlin.random.Random,
            config: EngineConfig,
            allowed: (Int) -> Boolean,
        ): Int = pick(state, random, config, allowed) { col ->
                val after = Harness.drop(state, col)
                val next = after.state
                val followUp = if (next.isOver || next.falling == null) {
                    0
                } else {
                    (0 until config.cols).maxOf { Harness.drop(next, it).transcript.merges.size }
                }
                after.transcript.merges.size + followUp
            }
    }
}

/**
 * Best merge count wins; the emptiest column breaks the tie, and a coin breaks
 * that.
 *
 * The coin matters more than it looks. Breaking ties by column index instead
 * pins every policy against the left wall, which builds a board shape no player
 * would ever build and quietly flatters the clutter number.
 */
private inline fun pick(
    state: GameState,
    random: kotlin.random.Random,
    config: EngineConfig,
    allowed: (Int) -> Boolean,
    merges: (Int) -> Int,
): Int {
    var bestMerges = -1
    var bestRoom = -1
    var chosen = state.falling?.cell?.col ?: config.spawnColumn
    var ties = 0
    for (col in 0 until config.cols) {
        if (!allowed(col)) continue
        val count = merges(col)
        val room = state.board.landingRow(col)
        when {
            count > bestMerges || (count == bestMerges && room > bestRoom) -> {
                bestMerges = count
                bestRoom = room
                chosen = col
                ties = 1
            }

            count == bestMerges && room == bestRoom -> {
                ties++
                if (random.nextInt(ties) == 0) chosen = col
            }
        }
    }
    return chosen
}
