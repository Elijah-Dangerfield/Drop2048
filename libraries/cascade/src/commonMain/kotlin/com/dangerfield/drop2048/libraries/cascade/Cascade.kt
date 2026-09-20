package com.dangerfield.drop2048.libraries.cascade

/**
 * The engine. `GameState x Input -> Transition` and nothing else (SPEC 4.1).
 *
 * No coroutines, no clock, no logging, no randomness that is not in the state.
 * The drop timer, the lock delay, animation, audio and haptics all live outside
 * this module. The engine advances one tick when it is told to and has no idea
 * time exists.
 */
object Cascade {

    /**
     * A fresh run. The seed is the entire run's identity: SPEC 14's Daily
     * Challenge is one of these shared worldwide, and a bug report is this plus
     * a list of [Input]s.
     */
    fun newGame(seed: Long, config: EngineConfig = EngineConfig.Default): GameState {
        val board = Board.empty(config.cols, config.rows)
        val draw = Spawn.draw(Rng(seed), level = 1, board, drawIndex = 0, lastWasSpecial = false, config = config)
        return GameState(
            config = config,
            board = board,
            falling = FallingBlock(draw.block, Cell(draw.column, 0)),
            rng = draw.rng,
            drawsMade = 1,
            lastDrawWasSpecial = draw.wasSpecial,
        )
    }

    fun apply(state: GameState, input: Input): Transition {
        if (state.isOver) return rejected(state, RejectionReason.RUN_OVER)
        val falling = state.falling ?: return rejected(state, RejectionReason.NO_FALLING_BLOCK)

        return when (input) {
            Input.Tick -> tick(state, falling)
            Input.MoveLeft -> move(state, falling, Direction.LEFT)
            Input.MoveRight -> move(state, falling, Direction.RIGHT)
            Input.Lock -> lock(state, falling)
        }
    }

    /** Replays a whole input sequence. The balance harness and the replay debug menu both use this. */
    fun play(state: GameState, inputs: List<Input>): GameState =
        inputs.fold(state) { current, input -> apply(current, input).state }

    /**
     * SPEC 12 and 18.10's rewarded continue: clear the top three rows, drop the
     * level by one, preserve the score. Resetting the drop timer to the start of
     * its interval is the ViewModel's half of the deal.
     *
     * The level is stored rather than derived from `blocksDropped` precisely so
     * this can hold: a derived level would snap straight back on the next drop.
     */
    fun continueRun(state: GameState): Transition {
        val cleared = (0 until minOf(state.config.continueRowsCleared, state.board.rows))
            .flatMap { state.board.rowCells(it) }
            .associateWith { null }
        val board = state.board.withAll(cleared)
        val spawned = spawnNext(
            state.copy(
                board = board,
                level = maxOf(1, state.level - 1),
                status = RunStatus.PLAYING,
                deathCause = null,
            )
        )
        val danger = board.isRowOccupied(DANGER_ROW)
        return Transition(
            state = spawned.state.copy(inDanger = danger),
            transcript = Transcript.Empty,
            events = spawned.events + dangerEvents(state.inDanger, danger),
        )
    }

    private fun tick(state: GameState, falling: FallingBlock): Transition {
        val below = falling.cell + Direction.DOWN
        if (!state.board.isEmpty(below)) return Transition(state)
        return Transition(state.copy(falling = falling.copy(cell = below)))
    }

    private fun move(state: GameState, falling: FallingBlock, direction: Direction): Transition {
        val target = falling.cell + direction
        if (!state.board.isEmpty(target)) return rejected(state, RejectionReason.MOVE_BLOCKED)
        return Transition(state.copy(falling = falling.copy(cell = target, lastDirection = direction)))
    }

    /**
     * Place the block, resolve, score, advance the level, then check for stacked
     * out and spawn the next block.
     *
     * The block always locks at its **landing** cell rather than the cell it
     * occupies, so a lock from mid-air falls the rest of the way first. A lock in
     * a full column therefore lands in row 0, resolution runs, and the run ends
     * only if row 0 is still occupied afterwards — no special case (SPEC 6,
     * SPEC 18.12).
     *
     * The rows the block skipped on the way down are what SPEC 7's hard drop
     * bonus pays for (decision D21). A lock the player did not hurry skips none
     * of them and the step is not emitted at all, so the bonus cannot pay for the
     * lock delay expiring — it only ever pays for ▼.
     */
    private fun lock(state: GameState, falling: FallingBlock): Transition {
        val config = state.config
        val landingRow = state.board.landingRow(falling.cell.col, falling.cell.row)
        val cell = Cell(falling.cell.col, landingRow)
        val placed = state.board.with(cell, falling.block)
        val resolution = Resolver.resolve(placed, listOf(Seed(cell, falling.lastDirection)), config)

        val steps = mutableListOf<ResolutionStep>()
        val rowsSkipped = landingRow - falling.cell.row
        if (rowsSkipped > 0) {
            steps += ResolutionStep.HardDropBonus(
                rows = rowsSkipped,
                points = config.scoring.hardDropPerRow * rowsSkipped,
            )
        }
        steps += resolution.steps

        val boardCleared = resolution.board.isClear
        if (boardCleared) {
            steps += ResolutionStep.BoardCleared(
                step = resolution.stepsTaken,
                points = config.scoring.boardCleared,
            )
        }

        val blocksDropped = state.blocksDropped + 1
        steps += ResolutionStep.Survival(
            level = state.level,
            points = config.scoring.survivalPerLevel * state.level,
        )

        val levelUp = blocksDropped % config.blocksPerLevel == 0
        val level = if (levelUp) state.level + 1 else state.level
        if (levelUp) {
            steps += ResolutionStep.LevelUp(level, config.scoring.levelUpPerLevel * level)
        }

        val transcript = Transcript(steps)
        val danger = resolution.board.isRowOccupied(DANGER_ROW)
        val settled = state.copy(
            board = resolution.board,
            falling = null,
            score = state.score + transcript.points,
            blocksDropped = blocksDropped,
            level = level,
            inDanger = danger,
        )

        val events = mutableListOf<GameEvent>()
        resolution.fault?.let { events += GameEvent.EngineFault(it) }
        if (boardCleared) events += GameEvent.BoardCleared
        if (levelUp) events += GameEvent.LevelReached(level)
        events += dangerEvents(state.inDanger, danger)

        if (resolution.board.isRowOccupied(TOP_ROW)) {
            return Transition(
                state = settled.copy(
                    status = RunStatus.STACKED_OUT,
                    deathCause = DeathCause.ROW_ZERO_OCCUPIED,
                ),
                transcript = transcript,
                events = events + GameEvent.StackedOut(DeathCause.ROW_ZERO_OCCUPIED),
            )
        }

        val spawned = spawnNext(settled)
        return Transition(spawned.state, transcript, events + spawned.events)
    }

    private class Spawned(val state: GameState, val events: List<GameEvent>)

    /**
     * SPEC 18.1: spawning into an occupied cell is impossible if the stacked-out
     * check is correct, so if it happens the run ends *and* the engine reports a
     * fault. Silently recovering here would hide the bug that caused it.
     *
     * The 2026-09-20 random spawn column does not weaken that. The guarantee was
     * never "the middle cell of row 0 is empty", it was "row 0 is empty", which
     * is what `isRowOccupied(TOP_ROW)` above and in [continueRun] enforce — so
     * every column is equally safe to spawn into and no column needs choosing
     * around an obstruction. Retrying the draw against the board would be the
     * tempting alternative and is the wrong one: it would make the fault
     * unreachable by construction and take the check's diagnostic value with it.
     */
    private fun spawnNext(state: GameState): Spawned {
        val draw = Spawn.draw(
            rng = state.rng,
            level = state.level,
            board = state.board,
            drawIndex = state.drawsMade,
            lastWasSpecial = state.lastDrawWasSpecial,
            config = state.config,
        )
        val drawn = state.copy(
            rng = draw.rng,
            drawsMade = state.drawsMade + 1,
            lastDrawWasSpecial = draw.wasSpecial,
        )
        val cell = Cell(draw.column, 0)
        if (drawn.board[cell] != null) {
            return Spawned(
                state = drawn.copy(
                    falling = null,
                    status = RunStatus.STACKED_OUT,
                    deathCause = DeathCause.SPAWN_BLOCKED,
                ),
                events = listOf(
                    GameEvent.EngineFault(Fault.SPAWN_INTO_OCCUPIED_CELL),
                    GameEvent.StackedOut(DeathCause.SPAWN_BLOCKED),
                ),
            )
        }
        return Spawned(
            state = drawn.copy(falling = FallingBlock(draw.block, cell)),
            events = emptyList(),
        )
    }

    private fun dangerEvents(was: Boolean, now: Boolean): List<GameEvent> = when {
        was == now -> emptyList()
        now -> listOf(GameEvent.DangerEntered)
        else -> listOf(GameEvent.DangerCleared)
    }

    private fun rejected(state: GameState, reason: RejectionReason) =
        Transition(state, Transcript.Empty, listOf(GameEvent.Rejected(reason)))

    private const val TOP_ROW = 0
    private const val DANGER_ROW = 1
}
