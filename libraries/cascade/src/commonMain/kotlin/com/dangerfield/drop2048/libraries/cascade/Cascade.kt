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
        var rng = Rng(seed)
        var draws = 0
        var lastSpecial = false
        val drawn = mutableListOf<Block>()
        repeat(config.previewSize + 1) {
            val draw = Spawn.draw(rng, level = 1, board, draws, lastSpecial, config)
            rng = draw.rng
            lastSpecial = draw.wasSpecial
            draws++
            drawn += draw.block
        }
        return GameState(
            config = config,
            board = board,
            falling = FallingBlock(drawn.first(), Cell(config.spawnColumn, 0)),
            preview = drawn.drop(1),
            rng = rng,
            drawsMade = draws,
            lastDrawWasSpecial = lastSpecial,
        )
    }

    fun apply(state: GameState, input: Input): Transition {
        if (state.isOver) return rejected(state, RejectionReason.RUN_OVER)
        val falling = state.falling ?: return rejected(state, RejectionReason.NO_FALLING_BLOCK)

        return when (input) {
            Input.Tick -> tick(state, falling)
            Input.MoveLeft -> move(state, falling, Direction.LEFT)
            Input.MoveRight -> move(state, falling, Direction.RIGHT)
            Input.Hold -> hold(state, falling)
            Input.Lock -> lock(state, falling, prefix = emptyList())
            Input.HardDrop -> hardDrop(state, falling)
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
                holdUsedThisDrop = false,
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
     * SPEC 5.4. One swap per drop, so hold cannot be used to stall. Legal on the
     * very first drop, where holding an empty slot just pulls the next block
     * (SPEC 18.11).
     */
    private fun hold(state: GameState, falling: FallingBlock): Transition {
        if (!state.config.holdEnabled) return rejected(state, RejectionReason.HOLD_DISABLED)
        if (state.holdUsedThisDrop) return rejected(state, RejectionReason.HOLD_ALREADY_USED)

        val stashed = state.hold
        val entering: GameState = if (stashed == null) {
            val pulled = pullFromPreview(state)
            pulled.state.copy(
                hold = falling.block,
                falling = FallingBlock(pulled.block, Cell(state.config.spawnColumn, 0)),
            )
        } else {
            state.copy(
                hold = falling.block,
                falling = FallingBlock(stashed, Cell(state.config.spawnColumn, 0)),
            )
        }
        return Transition(entering.copy(holdUsedThisDrop = true))
    }

    private fun hardDrop(state: GameState, falling: FallingBlock): Transition {
        val landing = state.board.landingRow(falling.cell.col, falling.cell.row)
        val skipped = landing - falling.cell.row
        val bonus = state.config.scoring.hardDropPerRow * skipped
        val prefix = if (skipped > 0) {
            listOf(ResolutionStep.HardDropBonus(rowsSkipped = skipped, points = bonus))
        } else {
            emptyList()
        }
        return lock(state, falling.copy(cell = Cell(falling.cell.col, landing)), prefix)
    }

    /**
     * Place the block, resolve, score, advance the level, then check for stacked
     * out and spawn the next block.
     *
     * The block always locks at its landing cell. A hard drop into a full column
     * therefore locks in row 0, resolution runs, and the run ends only if row 0
     * is still occupied afterwards — no special case (SPEC 6, SPEC 18.12).
     */
    private fun lock(
        state: GameState,
        falling: FallingBlock,
        prefix: List<ResolutionStep>,
    ): Transition {
        val config = state.config
        val cell = Cell(falling.cell.col, state.board.landingRow(falling.cell.col, falling.cell.row))
        val placed = state.board.with(cell, falling.block)
        val resolution = Resolver.resolve(placed, listOf(Seed(cell, falling.lastDirection)), config)

        val steps = mutableListOf<ResolutionStep>()
        steps += prefix
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
            holdUsedThisDrop = false,
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
     */
    private fun spawnNext(state: GameState): Spawned {
        val pulled = pullFromPreview(state)
        val cell = Cell(state.config.spawnColumn, 0)
        if (pulled.state.board[cell] != null) {
            return Spawned(
                state = pulled.state.copy(
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
            state = pulled.state.copy(falling = FallingBlock(pulled.block, cell)),
            events = emptyList(),
        )
    }

    private class Pulled(val state: GameState, val block: Block)

    /** Takes the head of the preview and draws one more to refill it. */
    private fun pullFromPreview(state: GameState): Pulled {
        val draw = Spawn.draw(
            rng = state.rng,
            level = state.level,
            board = state.board,
            drawIndex = state.drawsMade,
            lastWasSpecial = state.lastDrawWasSpecial,
            config = state.config,
        )
        val queue = state.preview + draw.block
        return Pulled(
            state = state.copy(
                preview = queue.drop(1),
                rng = draw.rng,
                drawsMade = state.drawsMade + 1,
                lastDrawWasSpecial = draw.wasSpecial,
            ),
            block = queue.first(),
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
