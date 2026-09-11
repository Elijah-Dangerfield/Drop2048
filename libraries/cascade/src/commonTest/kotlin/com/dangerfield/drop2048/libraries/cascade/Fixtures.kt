package com.dangerfield.drop2048.libraries.cascade

/**
 * Board fixtures for the engine tests.
 *
 * A board is written as a picture, one token per cell:
 * `.` empty, a number for a value tier, `W` Wildcard, `B` Bomb, `S` Stone.
 * Fewer rows than the board has are bottom-aligned, because almost every
 * interesting case sits at the bottom of the stack and writing five rows of
 * dots above it buries the thing under test.
 */
internal fun boardOf(
    picture: String,
    cols: Int = EngineConfig.DEFAULT_COLS,
    rows: Int = EngineConfig.DEFAULT_ROWS,
    align: Align = Align.BOTTOM,
): Board {
    val lines = picture.trimIndent().lines().filter { it.isNotBlank() }
    require(lines.size <= rows) { "picture has ${lines.size} rows, board has $rows" }
    val drawn = lines.map { line ->
        val tokens = line.trim().split(WHITESPACE)
        require(tokens.size == cols) { "row '$line' has ${tokens.size} cells, board has $cols" }
        tokens.map(::tokenToBlock)
    }
    val blank = List(rows - lines.size) { List<Block?>(cols) { null } }
    val all = if (align == Align.BOTTOM) blank + drawn else drawn + blank
    return Board(cols, rows, all.flatten())
}

internal enum class Align { TOP, BOTTOM }

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

internal fun value(points: Int): Block = NumberBlock(
    requireNotNull(BlockValue.ofPoints(points)) { "$points is not a value tier" }
)

internal fun tier(points: Int): BlockValue =
    requireNotNull(BlockValue.ofPoints(points)) { "$points is not a value tier" }

/** A state with a known board and a known falling block, for driving [Cascade.apply]. */
internal fun stateOf(
    board: Board,
    falling: FallingBlock? = null,
    config: EngineConfig = EngineConfig.Default,
    level: Int = 1,
    score: Long = 0,
    blocksDropped: Int = 0,
    seed: Long = 1,
): GameState = GameState(
    config = config,
    board = board,
    falling = falling,
    score = score,
    level = level,
    blocksDropped = blocksDropped,
    drawsMade = EngineConfig.DEFAULT_SUPPRESSED_DRAWS,
    rng = Rng(seed),
)

/** Runs the resolver directly, the way a lock does, without the scoring wrapper. */
internal fun resolve(
    board: Board,
    from: Cell,
    lastDirection: Direction? = null,
    config: EngineConfig = EngineConfig.Default,
): Resolution = Resolver.resolve(board, listOf(Seed(from, lastDirection)), config)

/**
 * Drops [block] into [col] from row 0 and locks it, returning the whole
 * transition.
 *
 * [Input.Lock] locks at the block's *landing* cell, so this places it at the
 * bottom of the column without a test having to tick it down eight times. Since
 * decision D21 it is also literally what the player's ▼ does, **including** the
 * hard drop bonus — a drop from row 0 skips rows, so the transcripts these
 * produce carry a `HardDropBonus` step.
 */
internal fun drop(
    state: GameState,
    block: Block,
    col: Int,
    lastDirection: Direction? = null,
): Transition = Cascade.apply(
    state.copy(falling = FallingBlock(block, Cell(col, 0), lastDirection)),
    Input.Lock,
)
