package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.debug.DebugController
import com.dangerfield.drop2048.features.settings.ControlScheme
import com.dangerfield.drop2048.features.debug.NoDebugController
import com.dangerfield.drop2048.features.debug.NoDiagnostics
import com.dangerfield.drop2048.libraries.ads.AdGate
import com.dangerfield.drop2048.libraries.ads.AdPlacement
import com.dangerfield.drop2048.libraries.ads.BannerAds
import com.dangerfield.drop2048.libraries.ads.InMemoryRunActivity
import com.dangerfield.drop2048.libraries.ads.InterstitialGate
import com.dangerfield.drop2048.libraries.ads.RewardOutcome
import com.dangerfield.drop2048.libraries.ads.RunActivity
import com.dangerfield.drop2048.libraries.billing.PaywallCoordinator
import com.dangerfield.drop2048.libraries.billing.PaywallRequest
import com.dangerfield.drop2048.libraries.billing.PaywallTrigger
import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.gameconfig.RewardedContinuesPerRun
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
import com.dangerfield.drop2048.libraries.achievements.Achievement
import com.dangerfield.drop2048.libraries.achievements.AchievementState
import com.dangerfield.drop2048.libraries.achievements.AchievementsRepository
import com.dangerfield.drop2048.libraries.achievements.RunOutcome
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.drop2048.AppLifecycle
import com.dangerfield.drop2048.libraries.drop2048.AppLifecycleObserver
import com.dangerfield.drop2048.libraries.progress.ProgressRepository
import com.dangerfield.drop2048.libraries.progress.RunRecord
import com.dangerfield.drop2048.libraries.progress.RunStats
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboard
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboards
import com.dangerfield.drop2048.libraries.progress.statsFrom
import com.dangerfield.drop2048.libraries.ui.system.Cue
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
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
    val achievements: FakeAchievementsRepository,
    val leaderboards: FakeLeaderboards,
    val lifecycle: FakeAppLifecycle,
    val clock: MutableClock,
    private val debug: DebugController,
    val ads: FakeAdGate,
    val interstitials: FakeInterstitialGate,
    val runActivity: RunActivity,
    val paywall: FakePaywallCoordinator,
    val banners: FakeBannerAds,
    private val continuesPerRun: Int,
) {
    val cues = mutableListOf<Cue>()
    val effects = mutableListOf<GameEffect>()

    lateinit var viewModel: GameViewModel
        private set

    val state: GameUiState get() = viewModel.state

    private fun launch(collectorScope: CoroutineScope) {
        viewModel = GameViewModel(
            runFactory = object : RunFactory {
                override fun newRun() = StartedRun(
                    state = start,
                    seed = SCENARIO_SEED,
                    debug = debug.isDebugSession.value,
                )
            },
            appCache = cache,
            savedRunStore = savedRuns,
            progress = progress,
            achievements = achievements,
            leaderboards = leaderboards,
            clock = clock,
            appLifecycle = lifecycle,
            debug = debug,
            diagnostics = NoDiagnostics,
            adGate = ads,
            interstitials = interstitials,
            runActivity = runActivity,
            paywall = paywall,
            banners = banners,
            continuesPerRun = RewardedContinuesPerRun(
                object : AppConfigMap() {
                    override val map: Map<String, *> = mapOf(
                        "ads" to mapOf("rewarded" to mapOf("continuesPerRun" to continuesPerRun)),
                    )
                },
            ),
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
     * Put the falling block down, the way a player does since decision D21: one
     * hard drop, whether that is ▼ or a downward flick.
     *
     * It advances **no** drop ticks and waits out no lock delay, because a hard
     * drop needs neither. That matters for the same reason L31 does: `tick()`
     * moves a whole drop interval, and a test that reaches for it to land a block
     * ends up asserting about the block after the one it meant.
     *
     * What it does advance is the travel: the block is published at its landing
     * cell and held for [HardDropTravelMillis] so the tile falls rather than
     * teleporting, and the lock is on the other side of that.
     */
    fun land() {
        act(GameAction.HardDrop)
        // Only while the block still has somewhere to fall. A block that spawns
        // with no room locks on the press, and advancing past that would eat the
        // first frames of the resolution the caller is about to assert on.
        if (viewModel.state.phase == GamePhase.Playing) advance(HardDropTravelMillis)
    }

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

    /**
     * Refuse SPEC 12's rewarded continue, which since C10 sits between the last
     * cascade and the results sheet.
     *
     * Every test that is about a run *ending* has to go through it, and saying so
     * in one call is better than the two alternatives: turning the offer off in
     * the fixture, which would leave forty tests asserting about a game
     * configuration nobody ships, or folding the decline into
     * [waitOutResolution], which would hide the one screen this chunk added from
     * every test that walks past it.
     */
    fun declineContinue() {
        if (viewModel.state.phase == GamePhase.ContinueOffer) act(GameAction.ContinueDecline)
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

    /**
     * The column the engine will put the block *after* this scenario's opening
     * one into.
     *
     * Since the owner's 2026-09-20 ruling the entry column is a uniform draw off
     * the run's own RNG, so a test about a buffered move — which is a
     * displacement, not a destination — has to ask where the block arrived
     * rather than assume the middle. It is asked of the same seeded start state
     * the scenario was built from and by running the engine, so it is the draw
     * the run will actually make rather than a second copy of the rule that
     * could agree with a bug.
     *
     * Only meaningful before the opening block has locked, which is the only
     * place any of its callers use it.
     */
    val nextSpawnColumn: Int
        get() = requireNotNull(Cascade.apply(start, Input.Lock).state.falling) {
            "the opening lock ended the run, so there is no next block"
        }.cell.col

    /** Backgrounding, which SPEC 8.4 auto-pauses and SPEC 18.9 snapshots. */
    fun background() {
        lifecycle.background()
        scope.runCurrent()
    }

    companion object {
        const val LockDelayMillis = 150L

        /** `Motion.HardDropMillis`, plus enough to be past it. */
        const val HardDropTravelMillis = 91L

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
            level: Int = 1,
            blocksDropped: Int = 0,
            best: Long = 0,
            config: EngineConfig = EngineConfig.Default,
            resume: SavedRun? = null,
            /**
             * Whether to press Play before handing over.
             *
             * A fresh run now waits on the start overlay ([GamePhase.Ready]), so
             * every test about *playing* has to get past it. It is one line here
             * rather than one line in forty tests, and it is a parameter rather
             * than unconditional so the overlay itself can be tested.
             */
            pressPlay: Boolean = true,
            /**
             * Whether this device has never been taught the game, which is what
             * puts `GameViewModel` into SPEC 13's guided run.
             *
             * Defaults to false — an already-onboarded device — because every
             * other scenario in this file is about a real run, and a fresh
             * `AppData` says `hasUserOnboarded = false`. Without this the
             * tutorial would open in front of forty tests that have nothing to
             * do with it.
             */
            teach: Boolean = false,
            /**
             * What this device has stored for SPEC 6's control scheme.
             *
             * Null is a device that has never opened settings, which is the
             * scheme the player is actually shipped. Naming one is how a test
             * asks what the game looks like to somebody who turned the arrow row
             * back on — the tutorial teaches a different control there, and
             * under `Buttons` the board refuses drags outright.
             */
            scheme: ControlScheme? = null,
            /** What the run that ends in this scenario is told it unlocked (SPEC 15). */
            achievements: FakeAchievementsRepository = FakeAchievementsRepository(),
            /**
             * Whether this launch has opened the debug menu (SPEC 19).
             *
             * `NoDebugController` — never opened — for every scenario that is
             * about the game, which is all of them but one.
             */
            debug: DebugController = NoDebugController,
            /** What the rewarded continue's ad network answers (SPEC 12). */
            ads: FakeAdGate = FakeAdGate(),
            /** Whether an interstitial is available when the results are dismissed. */
            interstitials: FakeInterstitialGate = FakeInterstitialGate(),
            /** Whether the once-per-session upsell card is still there. */
            paywall: FakePaywallCoordinator = FakePaywallCoordinator(),
            /** Whether `ads.enabled`, `ads.banner.enabled` and not-Pro all hold (D28). */
            banners: FakeBannerAds = FakeBannerAds(),
            /** `ads.rewarded.continuesPerRun`. SPEC 12's hard cap is 2. */
            continuesPerRun: Int = 2,
            body: GameScenario.() -> T,
        ): T {
            val board = boardOf(picture, config.cols, config.rows)
            val start = GameState(
                config = config,
                board = board,
                falling = falling?.let { FallingBlock(it, fallingAt) },
                rng = Rng(SCENARIO_SEED),
                level = level,
                blocksDropped = blocksDropped,
                drawsMade = config.specialSuppressedDraws,
            )
            val scenario = GameScenario(
                scope = this,
                start = start,
                cache = FakeAppCache(
                    AppData(hasUserOnboarded = !teach, controlScheme = scheme?.name),
                ),
                progress = FakeProgressRepository(best = best),
                savedRuns = FakeSavedRunStore(resume),
                achievements = achievements,
                leaderboards = FakeLeaderboards(),
                lifecycle = FakeAppLifecycle(),
                clock = MutableClock(),
                debug = debug,
                ads = ads,
                interstitials = interstitials,
                runActivity = InMemoryRunActivity(),
                paywall = paywall,
                banners = banners,
                continuesPerRun = continuesPerRun,
            )
            scenario.launch(backgroundScope)
            if (pressPlay && scenario.state.phase == GamePhase.Ready) {
                scenario.act(GameAction.Start)
            }
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

    /** What the process would find on disk. */
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

/**
 * Records what reached the platform, and nothing else.
 *
 * The whole point of the double is [submissions]: SPEC 15 says Sodogku shipped
 * `Leaderboards.submit` with no production caller, and the only way to notice
 * that is a test on the *game* asserting the platform was touched at the end of
 * a run. A double that returned a value would let a call site branch on it,
 * which the real interface refuses on purpose.
 */
internal class FakeLeaderboards : Leaderboards {

    val submissions = mutableListOf<Pair<Leaderboard, Long>>()
    val dashboards = mutableListOf<Leaderboard?>()

    /** Every set declared to the platform, in order. The ViewModel never calls this. */
    val reportedUnlocks = mutableListOf<Set<String>>()

    override val isOfferable = MutableStateFlow(false)

    override fun submit(board: Leaderboard, value: Long) {
        submissions += board to value
    }

    override fun reportUnlocked(achievementNames: Set<String>) {
        reportedUnlocks += achievementNames
    }

    override fun openDashboard(board: Leaderboard?) {
        dashboards += board
    }
}

/**
 * The achievement log, in memory. [recorded] is what the run reported, which is
 * the half that has to carry the transcript-derived facts; [unlocks] is what the
 * repository hands back, so a scenario can drive the unlock toast without
 * building a history that earns a badge.
 */
internal class FakeAchievementsRepository(
    private val unlocks: List<Achievement> = emptyList(),
) : AchievementsRepository {

    val recorded = mutableListOf<RunOutcome>()

    override fun observe(): Flow<AchievementState> = MutableStateFlow(AchievementState.Empty)

    override suspend fun state(): AchievementState = AchievementState.Empty

    override suspend fun record(outcome: RunOutcome): List<Achievement> {
        recorded += outcome
        return unlocks
    }

    override suspend fun reset() = Unit
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

/**
 * The rewarded gate, as something a scenario can set an answer on.
 *
 * It records [requests] rather than only returning, because half of what SPEC 12
 * asks for is about ads that are *not* asked for — a third continue must never
 * reach this.
 */
internal class FakeAdGate(
    var outcome: RewardOutcome = RewardOutcome.Rewarded,
) : AdGate {
    val requests = mutableListOf<AdPlacement>()
    val preloads = mutableListOf<AdPlacement>()

    override suspend fun showRewarded(placement: AdPlacement): RewardOutcome {
        requests += placement
        return outcome
    }

    override fun preload(placement: AdPlacement) {
        preloads += placement
    }
}

/**
 * The interstitial gate, recording the three things the game says to it.
 *
 * [shows] is the assertion that matters: SPEC 12 allows exactly one moment for
 * an interstitial, and every test about the governing principle is a test that
 * this list is empty.
 */
internal class FakeInterstitialGate(
    var ready: Boolean = true,
) : InterstitialGate {
    var runsFinished = 0
        private set
    var rewardedNotices = 0
        private set
    var shows = 0
        private set

    override fun noteRunFinished() {
        runsFinished++
    }

    override suspend fun showIfReady(): Boolean {
        shows++
        return ready
    }

    override fun noteRewardedShown() {
        rewardedNotices++
    }

    override fun preload() = Unit
}

internal class FakePaywallCoordinator(
    var cardAvailable: Boolean = true,
    var offerable: Boolean = true,
) : PaywallCoordinator {
    val offers = mutableListOf<PaywallTrigger>()
    var cardClaims = 0
        private set

    /** Nothing collects it here: the navigator that does lives in another module. */
    override val requests: Flow<PaywallRequest> = emptyFlow()

    override fun requestOffer(trigger: PaywallTrigger): Boolean {
        offers += trigger
        return offerable
    }

    override fun mayOffer(trigger: PaywallTrigger): Boolean = offerable

    override suspend fun claimStackedOutCard(): Boolean {
        cardClaims++
        return cardAvailable
    }
}

/**
 * The banner's policy half (D28), as something a scenario can switch off.
 *
 * [fills] is what the screen's slot would report, and the game only ever does
 * one thing with it, so recording it is the whole assertion: a banner that
 * arrived is an ad this player has seen.
 */
internal class FakeBannerAds(
    var allowed: Boolean = true,
) : BannerAds {
    var fills = 0
        private set

    override fun isAllowed(): Boolean = allowed

    override fun noteFilled() {
        fills++
    }
}
