package com.dangerfield.drop2048.libraries.achievements

import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.cascade.Input
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.Rng
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.SpecialBlock
import com.dangerfield.drop2048.libraries.cascade.Transition
import kotlin.math.abs
import kotlin.random.Random

/**
 * The shipped engine, played and posed, so `AchievementReachabilityTest` can ask
 * it what it is actually capable of rather than take a target's word for it.
 *
 * Two instruments, because the catalog asks two kinds of question.
 *
 * [playGreedily] is `tools/balance`'s greedy policy, cut down to what fits in a
 * common test: take the column with the most immediate merges, break ties on
 * room, break that on a coin. It answers "how far does a competent run get" for
 * score, level and drop count, where the honest answer is a measurement and not
 * an argument.
 *
 * [boardOf] and [dropInto] answer the other kind. A ten-step cascade turns up
 * four times in three-quarters of a million drops, so no number of played runs
 * a test can afford will witness one; posing a board that produces it is an
 * existence proof, it is deterministic, and it runs in microseconds. It is also
 * the only way to ask about a board a random run may never build — three Stones
 * resting in the row a 2048 lands in, for instance.
 *
 * The fixtures are a local copy of `:libraries:cascade`'s, which are `internal`
 * to that module's own test source. Duplicating twenty lines was preferred to
 * publishing a test-fixtures artifact for one consumer.
 */
internal object EngineWitness {

    /**
     * A board written as a picture, one token per cell: `.` empty, a number for
     * a value tier, `W` Wildcard, `B` Bomb, `S` Stone. Fewer rows than the board
     * has are bottom-aligned, because every interesting case sits at the bottom
     * of the stack.
     *
     * Pictures must be **gravity-stable** — resolution settles the board at the
     * end of every step, so a floating block falls on step one and turns every
     * later step into something else.
     */
    fun boardOf(picture: String, config: EngineConfig = EngineConfig.Default): Board {
        val lines = picture.trimIndent().lines().filter { it.isNotBlank() }
        require(lines.size <= config.rows) { "picture has ${lines.size} rows, board has ${config.rows}" }
        val drawn = lines.map { line ->
            val tokens = line.trim().split(WHITESPACE)
            require(tokens.size == config.cols) {
                "row '$line' has ${tokens.size} cells, board has ${config.cols}"
            }
            tokens.map(::tokenToBlock)
        }
        val blank = List(config.rows - lines.size) { List<Block?>(config.cols) { null } }
        return Board(config.cols, config.rows, (blank + drawn).flatten())
    }

    fun stateOf(
        board: Board,
        level: Int = 1,
        blocksDropped: Int = 0,
        config: EngineConfig = EngineConfig.Default,
    ): GameState = GameState(
        config = config,
        board = board,
        falling = null,
        level = level,
        blocksDropped = blocksDropped,
        drawsMade = EngineConfig.DEFAULT_SUPPRESSED_DRAWS,
        rng = Rng(1),
    )

    /**
     * Locks [block] into [col]. `Input.Lock` places at the block's *landing*
     * cell, so this drops it to the bottom of the column without ticking it down
     * eight times.
     */
    fun dropInto(state: GameState, block: Block, col: Int): Transition = Cascade.apply(
        state.copy(falling = FallingBlock(block, Cell(col, 0))),
        Input.Lock,
    )

    fun value(points: Int): Block = NumberBlock(
        requireNotNull(BlockValue.ofPoints(points)) { "$points is not a value tier" }
    )

    fun special(kind: Special): Block = SpecialBlock(kind)

    /**
     * One whole run under the greedy policy, reduced to the numbers the catalog
     * asks about.
     *
     * Deliberately as dumb as the harness's: it does not avoid a placement that
     * ends the run, because a policy that played to survive would answer a
     * question nobody asked and would take far longer to die.
     */
    fun playGreedily(seed: Long, config: EngineConfig = EngineConfig.Default): PlayedRun {
        val random = Random(seed)
        var state = Cascade.newGame(seed, config)
        var facts = RunFacts.Empty
        var merges = 0
        var bursts = 0
        var deepest = 0
        var highest = 0

        while (!state.isOver && state.blocksDropped < MAX_DROPS) {
            val col = bestColumn(state, random, config)
            val transition = lockInto(state, col)
            val transcript = transition.transcript
            merges += transcript.merges.size
            bursts += transcript.bursts.size
            deepest = maxOf(deepest, transcript.depth)
            highest = listOfNotNull(
                transcript.merges.maxOfOrNull { it.result.points },
                transition.state.board.highestValue()?.points,
                highest,
            ).max()
            facts = facts.fold(transition)
            state = transition.state
        }

        return PlayedRun(
            score = state.score,
            level = state.level,
            blocksDropped = state.blocksDropped,
            merges = merges,
            bursts = bursts,
            longestCascade = deepest,
            highestTier = highest,
            facts = facts,
        )
    }

    private fun bestColumn(state: GameState, random: Random, config: EngineConfig): Int {
        var bestMerges = -1
        var bestRoom = -1
        var chosen = state.falling?.cell?.col ?: config.spawnColumn
        var ties = 0
        for (col in 0 until config.cols) {
            val count = lockInto(state, col).transcript.merges.size
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

    /**
     * Steers the falling block to [col] and locks it, the way a player would.
     *
     * The block is *moved* rather than teleported because a sideways move sets
     * `lastDirection`, which is priority slot 2 in SPEC 4.3's merge order — a
     * placement that skipped it would resolve differently from the same
     * placement in the real game.
     */
    private fun lockInto(state: GameState, col: Int): Transition {
        val from = state.falling?.cell?.col ?: return Cascade.apply(state, Input.Lock)
        val step = if (col > from) Input.MoveRight else Input.MoveLeft
        var current = state
        repeat(abs(col - from)) { current = Cascade.apply(current, step).state }
        return Cascade.apply(current, Input.Lock)
    }

    private val WHITESPACE = Regex("\\s+")

    private fun tokenToBlock(token: String): Block? = when (token) {
        "." -> null
        "W" -> SpecialBlock(Special.WILDCARD)
        "B" -> SpecialBlock(Special.BOMB)
        "S" -> SpecialBlock(Special.STONE)
        else -> NumberBlock(
            requireNotNull(BlockValue.ofPoints(token.toInt())) { "$token is not a value tier" }
        )
    }

    /** Matches `tools/balance`. A run that reaches it has not died and is reported as-is. */
    private const val MAX_DROPS = 3_000
}

/** What one greedy run produced. */
internal data class PlayedRun(
    val score: Long,
    val level: Int,
    val blocksDropped: Int,
    val merges: Int,
    val bursts: Int,
    val longestCascade: Int,
    val highestTier: Int,
    val facts: RunFacts,
)
