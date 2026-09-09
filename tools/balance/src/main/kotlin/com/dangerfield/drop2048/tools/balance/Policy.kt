package com.dangerfield.drop2048.tools.balance

import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.GameState

/**
 * A scripted player. SPEC 4.4 makes these deliberately dumb: the question is not
 * whether a perfect player can win, it is whether the board clogs.
 *
 * None of them avoid death or stack for a burst. A policy that played well would
 * answer a question nobody is asking, and would hide the clog the spawn table
 * causes behind its own skill.
 *
 * `kotlin.random.Random` is spelled out at every use because [Policy.Random] is
 * one of the policies and shadows the import inside this file.
 */
sealed interface Policy {

    val name: String

    /**
     * Whether this policy reads something the player cannot see.
     *
     * Exactly one does — [Lookahead1] — and C1e ruled that it keeps doing so and
     * says so in every report line it prints. The alternative to a declared cheat
     * is not an honest ceiling, it is no ceiling: SPEC 4.4 wants an upper bound,
     * [Greedy] is a competent player rather than a bound, and a lookahead that
     * did not cheat would have to search the *distribution* of next draws, which
     * is a different and far more expensive policy answering a question nobody
     * asked.
     *
     * It is a flag on the instrument rather than a paragraph in a doc because the
     * failure mode is someone quoting a lookahead number as a prediction of play,
     * and that happens at the moment they read the output.
     *
     * **It is abstract, and it has to stay abstract.** Giving it a `get() = false`
     * default makes this a JVM default method, which makes initialising
     * [Random] initialise [Policy] itself, which builds [Companion] and evaluates
     * [All] while the object it is reading is still half-constructed. [All] then
     * holds a null and every run through it dies on a non-null parameter check.
     * Measured: two tests, no compiler warning.
     */
    val cheats: Boolean

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

    /** Uniform column. SPEC 4.4's floor: if this reaches level 6 the game is too easy. */
    data object Random : Policy {
        override val name = "random"

        override val cheats = false

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

        override val cheats = false

        override fun column(
            state: GameState,
            random: kotlin.random.Random,
            config: EngineConfig,
            allowed: (Int) -> Boolean,
        ): Int = pick(state, random, config, allowed) { Harness.drop(state, it).transcript.merges.size }
    }

    /**
     * Greedy, plus the merges the *next* block could make from the board this
     * drop leaves behind.
     *
     * **This policy now cheats, and that is deliberate but load-bearing.** It was
     * written when SPEC 5.4 showed the player the next two blocks, so its
     * lookahead was information a real player had. Decision D11 cut the preview,
     * and it also moved the draw to spawn time — so the block this reads does not
     * exist until the drop it is planning around has already landed.
     *
     * It is kept unchanged because its job never was to model a player: it is
     * SPEC 4.4's *ceiling*, the answer to "does the board clog even against
     * something better than anyone will play". A ceiling is allowed to cheat as
     * long as everyone knows it does. **C1e kept it and declared it**: [cheats]
     * is true, and every report line it prints carries that word. Its clocked
     * median is two levels above [Greedy]'s and its 2048 rate is four times
     * [Greedy]'s, which is exactly how much information nobody has is worth, and
     * exactly why it must not be quoted as a prediction of play.
     *
     * It stays as dumb as [Greedy] in every other respect — in particular it does
     * not avoid a placement that ends the run, because the moment a policy starts
     * avoiding death it stops measuring whether the board clogs.
     */
    data object Lookahead1 : Policy {
        override val name = "lookahead1"

        override val cheats = true

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
