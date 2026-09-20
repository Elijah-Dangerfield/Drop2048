package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.debug.NoDebugController
import com.dangerfield.drop2048.features.debug.NoDiagnostics
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.ads.InMemoryRunActivity
import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.gameconfig.RewardedContinuesPerRun
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.gameconfig.BlocksPerLevel
import com.dangerfield.drop2048.libraries.gameconfig.BoardRows
import com.dangerfield.drop2048.libraries.gameconfig.BombFirstLevel
import com.dangerfield.drop2048.libraries.gameconfig.BombPerMille
import com.dangerfield.drop2048.libraries.gameconfig.RemoteEngineConfig
import com.dangerfield.drop2048.libraries.gameconfig.SpawnCapDivisor
import com.dangerfield.drop2048.libraries.gameconfig.SpawnTableValue
import com.dangerfield.drop2048.libraries.gameconfig.SpeedCurveMsPerRow
import com.dangerfield.drop2048.libraries.gameconfig.SpeedFloorMs
import com.dangerfield.drop2048.libraries.gameconfig.SpeedTailStepMs
import com.dangerfield.drop2048.libraries.gameconfig.StoneFirstLevel
import com.dangerfield.drop2048.libraries.gameconfig.StonePerMille
import com.dangerfield.drop2048.libraries.gameconfig.WildcardFirstLevel
import com.dangerfield.drop2048.libraries.gameconfig.WildcardPerMille
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * The seam C7 has to get right: **a fetched config takes effect at the start of
 * the next run and never during one.**
 *
 * `OfflineFirstAppConfigRepository` refreshes on every app foreground past its
 * throttle, and the merged map it publishes is live — a `ConfiguredValue` read
 * now and an hour from now can differ inside one process. If the game read those
 * values while a run was alive, the board could gain a row, the level bar could
 * change denominator and the drop timer could change interval, all mid-drop, and
 * the player would have no way to describe what happened.
 *
 * The mechanism that stops it is D5: `EngineConfig` is a field on `GameState`,
 * `RunFactory` is the only reader of [RemoteEngineConfig], and everything the
 * ViewModel needs during a run comes off `state.config`. These tests move the
 * remote map underneath a live run and assert nothing budges until Restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteConfigRunBoundaryTest : CoroutineTest() {

    @Test
    fun `a run started with the server unreachable plays the compiled-in defaults`() {
        val factory = RealRunFactory(remoteEngineConfig(MutableConfigMap()), NoDebugController)

        assertEquals(EngineConfig.Default, factory.newRun().state.config)
    }

    @Test
    fun `the factory reads the map at the moment a run starts and not before`() {
        val config = MutableConfigMap()
        val factory = RealRunFactory(remoteEngineConfig(config), NoDebugController)

        val before = factory.newRun().state.config
        config.overrides = mapOf("board.rows" to 6, "level.blocksPerLevel" to 5)
        val after = factory.newRun().state.config

        assertEquals(EngineConfig.DEFAULT_ROWS, before.rows)
        assertEquals(EngineConfig.DEFAULT_BLOCKS_PER_LEVEL, before.blocksPerLevel)
        assertEquals(6, after.rows)
        assertEquals(5, after.blocksPerLevel)
    }

    @Test
    fun `a state already handed to the engine is not reshaped by a later change`() {
        val config = MutableConfigMap()
        val factory = RealRunFactory(remoteEngineConfig(config), NoDebugController)

        val run = factory.newRun()
        config.overrides = mapOf("board.rows" to 6, "speed.curve" to listOf(1_000))

        assertEquals(EngineConfig.DEFAULT_ROWS, run.state.config.rows)
        assertEquals(
            EngineConfig.Default.speed.msPerRow(level = 1),
            run.state.config.speed.msPerRow(level = 1),
        )
    }

    @Test
    fun `a config change does not reach a run already on screen`() = runUnitTest {
        val config = MutableConfigMap()
        val viewModel = startedRun(config)

        assertEquals(EngineConfig.DEFAULT_ROWS, viewModel.state.board.rows)

        config.overrides = mapOf("board.rows" to 6)
        viewModel.takeAction(GameAction.MoveLeft)

        assertEquals(EngineConfig.DEFAULT_ROWS, viewModel.state.board.rows)
    }

    @Test
    fun `the next run picks the change up`() = runUnitTest {
        val config = MutableConfigMap()
        val viewModel = startedRun(config)

        config.overrides = mapOf("board.rows" to 6)
        viewModel.takeAction(GameAction.Restart)

        assertEquals(6, viewModel.state.board.rows)
    }

    @Test
    fun `a malformed value arriving mid-session costs that key and nothing else`() {
        val config = MutableConfigMap(
            mapOf("board.rows" to "six", "level.blocksPerLevel" to 25)
        )
        val started = RealRunFactory(remoteEngineConfig(config), NoDebugController).newRun()

        assertEquals(EngineConfig.DEFAULT_ROWS, started.state.config.rows)
        assertEquals(25, started.state.config.blocksPerLevel)
    }
}

private fun startedRun(config: AppConfigMap): GameViewModel {
    val viewModel = GameViewModel(
        runFactory = RealRunFactory(remoteEngineConfig(config), NoDebugController),
        appCache = FakeAppCache(AppData(hasUserOnboarded = true)),
        savedRunStore = FakeSavedRunStore(),
        progress = FakeProgressRepository(),
        achievements = FakeAchievementsRepository(),
        leaderboards = FakeLeaderboards(),
        clock = MutableClock(),
        appLifecycle = FakeAppLifecycle(),
        debug = NoDebugController,
        diagnostics = NoDiagnostics,
        adGate = FakeAdGate(),
        interstitials = FakeInterstitialGate(),
        runActivity = InMemoryRunActivity(),
        paywall = FakePaywallCoordinator(),
        banners = FakeBannerAds(),
        continuesPerRun = RewardedContinuesPerRun(config),
    )
    if (viewModel.state.phase == GamePhase.Ready) viewModel.takeAction(GameAction.Start)
    return viewModel
}

/** A merged config snapshot a test can move while the app holds a reference to it. */
private class MutableConfigMap(overrides: Map<String, Any?> = emptyMap()) : AppConfigMap() {

    var overrides: Map<String, Any?> = overrides
        set(value) {
            field = value
            nested = value.toNested()
        }

    private var nested: Map<String, Any?> = overrides.toNested()

    override val map: Map<String, *> get() = nested
}

private fun Map<String, Any?>.toNested(): Map<String, Any?> =
    entries.fold(emptyMap()) { acc, entry -> acc.put(entry.key.split('.'), entry.value) }

private fun Map<String, Any?>.put(path: List<String>, value: Any?): Map<String, Any?> {
    val head = path.first()
    if (path.size == 1) return this + (head to value)
    @Suppress("UNCHECKED_CAST")
    val child = (this[head] as? Map<String, Any?>) ?: emptyMap()
    return this + (head to child.put(path.drop(1), value))
}

private fun remoteEngineConfig(map: AppConfigMap) = RemoteEngineConfig(
    boardRows = BoardRows(map),
    blocksPerLevel = BlocksPerLevel(map),
    spawnCapDivisor = SpawnCapDivisor(map),
    spawnTable = SpawnTableValue(map),
    speedCurve = SpeedCurveMsPerRow(map),
    speedFloorMs = SpeedFloorMs(map),
    speedTailStepMs = SpeedTailStepMs(map),
    wildcardPerMille = WildcardPerMille(map),
    wildcardFirstLevel = WildcardFirstLevel(map),
    bombPerMille = BombPerMille(map),
    bombFirstLevel = BombFirstLevel(map),
    stonePerMille = StonePerMille(map),
    stoneFirstLevel = StoneFirstLevel(map),
)
