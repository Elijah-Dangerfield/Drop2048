package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.Rng
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.SpecialBlock
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.drop2048.AppLifecycle
import com.dangerfield.drop2048.libraries.drop2048.AppLifecycleObserver
import com.dangerfield.drop2048.libraries.progress.GameMode
import com.dangerfield.drop2048.libraries.progress.ProgressRepository
import com.dangerfield.drop2048.libraries.progress.RunRecord
import com.dangerfield.drop2048.libraries.progress.RunStats
import com.dangerfield.drop2048.libraries.progress.statsFrom
import com.dangerfield.drop2048.libraries.ui.system.Cue
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/**
 * The scenario harness `docs/practices/testing.md` asks for, regrown here.
 *
 * A game test that wires its own `AppCache`, builds a `GameState` field by field
 * and then counts `delay`s reads as plumbing rather than as a scenario. This is
 * the plumbing, once: a board written as a **picture** (the shape
 * `:libraries:cascade`'s own `Fixtures.kt` established), verbs for the things a
 * player does, and time advanced in the units the game actually uses — one drop
 * tick, one lock delay.
 *
 * It is a pattern, not a framework. Copy and adapt it in the next feature that
 * earns one; do not generalise it into a shared module.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class GameScenario private constructor(
    private val scope: TestScope,
    private val start: GameState,
    val cache: FakeAppCache,
    val progress: FakeProgressRepository,
    val savedRuns: FakeSavedRunStore,
    val lifecycle: FakeAppLifecycle,
    val clock: MutableClock,
) {
    val cues = mutableListOf<Cue>()
    val effects = mutableListOf<GameEffect>()

    lateinit var viewModel: GameViewModel
        private set

    val state: GameUiState get() = viewModel.state

    private fun launch(collectorScope: CoroutineScope) {
        viewModel = GameViewModel(
            runFactory = object : RunFactory {
                override fun newRun() = StartedRun(state = start, seed = SCENARIO_SEED, mode = GameMode.ENDLESS)
            },
            appCache = cache,
            savedRunStore = savedRuns,
            progress = progress,
            clock = clock,
            appLifecycle = lifecycle,
        )
        collectorScope.launch {
            viewModel.eventFlow.collect { effect ->
                effects += effect
                if (effect is GameEffect.Play) cues += effect.cue
            }
        }
        scope.runCurrent()
    }

    /** One drop tick at the current level, plus whatever it set in motion. */
    fun tick(times: Int = 1) {
        repeat(times) {
            scope.advanceTimeBy(start.config.speed.msPerRow(viewModel.state.level).toLong() + 1)
            scope.runCurrent()
        }
    }

    /** Virtual time, in the units the test is reasoning about. */
    fun advance(millis: Long) {
        scope.advanceTimeBy(millis)
        scope.runCurrent()
    }

    /** Long enough for a pending lock delay to elapse. */
    fun waitOutLockDelay() = advance(LockDelayMillis + 1)

    /**
     * Advance until the resolution finishes, and **no further**.
     *
     * A flat "advance thirty seconds" would also run the drop timer for thirty
     * seconds, so half a dozen more blocks would land and the assertion after it
     * would be about a board nobody wrote. Stepping until the phase changes is
     * what keeps the test about the drop it is testing.
     */
    fun waitOutResolution() {
        var elapsed = 0L
        while (viewModel.state.phase == GamePhase.Resolving && elapsed < ResolutionBudgetMillis) {
            advance(ResolutionStepMillis)
            elapsed += ResolutionStepMillis
        }
    }

    fun act(vararg actions: GameAction) {
        actions.forEach { viewModel.takeAction(it) }
        scope.runCurrent()
    }

    fun assertState(assertion: (GameUiState) -> Unit) = assertion(viewModel.state)

    fun assertPhase(phase: GamePhase) = assertEquals(phase, viewModel.state.phase, "phase")

    /** Whether anything has come to rest in [row]. The signal that a lock happened. */
    fun landedIn(row: Int): Boolean = (0 until state.board.cols).any { state.board[Cell(it, row)] != null }

    fun assertFallingAt(col: Int, row: Int) {
        val falling = viewModel.state.falling
        assertTrue(falling != null, "expected a falling block")
        assertEquals(Cell(col, row), falling.cell, "falling cell")
    }

    /** Backgrounding, which SPEC 8.4 auto-pauses and SPEC 18.9 snapshots. */
    fun background() {
        lifecycle.background()
        scope.runCurrent()
    }

    companion object {
        const val LockDelayMillis = 150L

        /** Comfortably longer than any transcript a five-wide board can produce. */
        private const val ResolutionBudgetMillis = 10_000L

        private const val ResolutionStepMillis = 10L

        /**
         * Start a run and hand it to [body].
         *
         * [picture] is bottom-aligned, one token per cell: `.` empty, a number
         * for a value tier, `W` Wildcard, `B` Bomb, `S` Stone. Writing only the
         * rows that matter is the whole point — the interesting cases all sit at
         * the bottom of the stack.
         */
        @Suppress("LongParameterList")
        fun <T> TestScope.playing(
            picture: String = "",
            falling: Block? = NumberBlock(BlockValue.V2),
            fallingAt: Cell = Cell(2, 0),
            preview: List<Block> = listOf(NumberBlock(BlockValue.V2), NumberBlock(BlockValue.V4)),
            level: Int = 1,
            blocksDropped: Int = 0,
            best: Long = 0,
            config: EngineConfig = EngineConfig.Default,
            resume: SavedRun? = null,
            body: GameScenario.() -> T,
        ): T {
            val board = boardOf(picture, config.cols, config.rows)
            val start = GameState(
                config = config,
                board = board,
                falling = falling?.let { FallingBlock(it, fallingAt) },
                preview = preview,
                rng = Rng(SCENARIO_SEED),
                level = level,
                blocksDropped = blocksDropped,
                drawsMade = config.specialSuppressedDraws,
            )
            val scenario = GameScenario(
                scope = this,
                start = start,
                cache = FakeAppCache(),
                progress = FakeProgressRepository(best = best),
                savedRuns = FakeSavedRunStore(resume),
                lifecycle = FakeAppLifecycle(),
                clock = MutableClock(),
            )
            scenario.launch(backgroundScope)
            return scenario.body()
        }

        internal const val SCENARIO_SEED = 20_480L
    }
}

/**
 * A board written as a picture, bottom-aligned.
 *
 * Deliberately a copy of the engine's `Fixtures.kt` shape rather than a shared
 * utility: that one is `internal` to `:libraries:cascade`'s test source set, and
 * exporting it would put a test fixture in the engine's public surface to save
 * twenty lines.
 */
internal fun boardOf(picture: String, cols: Int, rows: Int): Board {
    val lines = picture.trimIndent().lines().filter { it.isNotBlank() }
    require(lines.size <= rows) { "picture has ${lines.size} rows, board has $rows" }
    val drawn = lines.map { line ->
        val tokens = line.trim().split(WHITESPACE)
        require(tokens.size == cols) { "row '$line' has ${tokens.size} cells, board has $cols" }
        tokens.map(::tokenToBlock)
    }
    val blank = List(rows - lines.size) { List<Block?>(cols) { null } }
    return Board(cols, rows, (blank + drawn).flatten())
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

internal fun blockOf(points: Int): Block = NumberBlock(
    requireNotNull(BlockValue.ofPoints(points)) { "$points is not a value tier" }
)

internal class FakeAppCache(initial: AppData = AppData()) : AppCache {
    private val stored = MutableStateFlow(initial)

    /** The current value without suspending, for assertions outside a coroutine. */
    val snapshot: AppData get() = stored.value

    override val updates: Flow<AppData> = stored
    override suspend fun get(): AppData = stored.value
    override suspend fun set(value: AppData) {
        stored.value = value
    }

    override suspend fun clear() {
        stored.value = AppData()
    }
}

/** The run history as it stands, read outside the test scheduler. */
internal fun GameScenario.recordedRuns(): List<RunRecord> = progress.recorded.toList()

/** What would be restored if the process died right now. */
internal fun GameScenario.savedRun(): SavedRun? = savedRuns.stored

internal class FakeProgressRepository(best: Long = 0) : ProgressRepository {
    val recorded = mutableListOf<RunRecord>()
    private val seededBest = best

    override suspend fun record(run: RunRecord) {
        recorded += run
    }

    override fun observeStats(): Flow<RunStats> = MutableStateFlow(statsFrom(recorded))

    override suspend fun bestScore(): Long = maxOf(seededBest, recorded.maxOfOrNull { it.score } ?: 0)
}

/**
 * The saved run, in memory. `stored` is read directly rather than through
 * [load] so a test can assert what the process would have found on disk without
 * having to be inside a coroutine.
 */
internal class FakeSavedRunStore(initial: SavedRun? = null) : SavedRunStore {
    var stored: SavedRun? = initial
        private set

    override suspend fun load(): SavedRun? = stored

    override suspend fun save(run: SavedRun) {
        stored = run
    }

    override suspend fun clear() {
        stored = null
    }
}

internal class FakeAppLifecycle : AppLifecycle {
    private val observers = mutableListOf<AppLifecycleObserver>()

    override fun addObserver(observer: AppLifecycleObserver) {
        observers += observer
    }

    override fun removeObserver(observer: AppLifecycleObserver) {
        observers -= observer
    }

    fun background() = observers.toList().forEach { it.onEnterBackground() }
}

/**
 * Wall time the test moves by hand.
 *
 * The virtual scheduler advances `delay`, not the clock, so a duration measured
 * against `Clock.System` in a test would be zero. Advancing this in the same
 * units keeps `run_record.durationMs` assertable.
 */
internal class MutableClock(private var millis: Long = 0) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(millis)

    fun advance(by: Long) {
        millis += by
    }
}
