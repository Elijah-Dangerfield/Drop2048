package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.debug.DebugOverrides
import com.dangerfield.drop2048.features.debug.NoDebugController
import com.dangerfield.drop2048.features.debug.PresetBoard
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.blockOf
import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.gameconfig.BlocksPerLevel
import com.dangerfield.drop2048.libraries.gameconfig.BoardRows
import com.dangerfield.drop2048.libraries.gameconfig.BombFirstLevel
import com.dangerfield.drop2048.libraries.gameconfig.BombPerMille
import com.dangerfield.drop2048.libraries.gameconfig.NudgeRows
import com.dangerfield.drop2048.libraries.gameconfig.RemoteEngineConfig
import com.dangerfield.drop2048.libraries.gameconfig.SoftDropMsPerRow
import com.dangerfield.drop2048.libraries.gameconfig.SpawnCapDivisor
import com.dangerfield.drop2048.libraries.gameconfig.SpawnTableValue
import com.dangerfield.drop2048.libraries.gameconfig.SpeedCurveMsPerRow
import com.dangerfield.drop2048.libraries.gameconfig.SpeedFloorMs
import com.dangerfield.drop2048.libraries.gameconfig.SpeedTailStepMs
import com.dangerfield.drop2048.libraries.gameconfig.StoneFirstLevel
import com.dangerfield.drop2048.libraries.gameconfig.StonePerMille
import com.dangerfield.drop2048.libraries.gameconfig.WildcardFirstLevel
import com.dangerfield.drop2048.libraries.gameconfig.WildcardPerMille
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The second hazard of SPEC 19, and the shape of the answer.
 *
 * D18 pins the Daily to `EngineConfig.Default` so that everyone who plays a day
 * plays the same game. A debug menu is the obvious way to break that, and the
 * guard is not a check — it is that [dailyRun] never reads the overrides at all,
 * and that the overrides are not an `EngineConfig` in the first place, so there
 * is nothing about a debug run that could travel inside a `GameState` (D5) into
 * a shared seed.
 *
 * Both halves are asserted, because either alone would keep passing while the
 * other rotted: the Endless test says the overrides work, and the Daily test
 * says they are ignored. A guard that ignored everything would pass the second
 * and fail the first.
 */
class DebugRunFactoryTest {

    private val config = EngineConfig.Default

    @Test
    fun `a forced seed starts the endless run on it`() {
        val factory = RealRunFactory(defaultEngineConfig(), FakeDebugController(DebugOverrides(seed = 4242)))

        assertEquals(4242, factory.newRun().seed)
    }

    @Test
    fun `a preset board starts the endless run on it`() {
        val factory = RealRunFactory(
            defaultEngineConfig(),
            FakeDebugController(DebugOverrides(preset = PresetBoard.StoneHeavy)),
        )

        val expected = PresetBoard.StoneHeavy.boardFor(config.cols, config.rows)
        assertEquals(expected.occupiedCells.size, factory.newRun().state.board.occupiedCells.size)
    }

    @Test
    fun `a start level moves the blocks dropped with it`() {
        val factory = RealRunFactory(defaultEngineConfig(), FakeDebugController(DebugOverrides(startLevel = 12)))

        val state = factory.newRun().state
        assertEquals(12, state.level)
        assertEquals(11 * config.blocksPerLevel, state.blocksDropped)
    }

    /** The one that matters. */
    @Test
    fun `the daily ignores every override`() {
        val overridden = RealRunFactory(
            defaultEngineConfig(),
            FakeDebugController(
                DebugOverrides(
                    seed = 999,
                    startLevel = 20,
                    preset = PresetBoard.NearlyFull,
                    forcedBlocks = listOf(blockOf(BlockValue.V1024)),
                ),
            ),
        ).dailyRun(DailySeed)
        val untouched = RealRunFactory(defaultEngineConfig(), NoDebugController).dailyRun(DailySeed)

        assertEquals(untouched.seed, overridden.seed)
        assertEquals(untouched.state.level, overridden.state.level)
        assertEquals(untouched.state.board, overridden.state.board)
        assertEquals(untouched.state.falling, overridden.state.falling)
        assertEquals(untouched.state.config, overridden.state.config)
    }

    /**
     * The positive control for the test above: the same overrides visibly change
     * an Endless run, so "the Daily did not move" means the guard held rather
     * than that the overrides do nothing.
     */
    @Test
    fun `the same overrides do change an endless run`() {
        val controller = FakeDebugController(
            DebugOverrides(
                seed = 999,
                startLevel = 20,
                preset = PresetBoard.NearlyFull,
                forcedBlocks = listOf(blockOf(BlockValue.V1024)),
            ),
        )
        val overridden = RealRunFactory(defaultEngineConfig(), controller).newRun()
        val plain = Cascade.newGame(seed = 999, config = config)

        assertNotEquals(plain.board, overridden.state.board)
        assertNotEquals(plain.level, overridden.state.level)
        assertEquals(blockOf(BlockValue.V1024), overridden.state.falling?.block)
    }

    /**
     * A run started while the menu has been opened is marked, whether or not
     * anything was actually forced. That is the session rule, and it is what
     * `DebugSessionTest` then hangs the four write guards off.
     */
    @Test
    fun `a run in a debug session is marked even with nothing forced`() {
        val factory = RealRunFactory(defaultEngineConfig(), FakeDebugController(DebugOverrides.None))

        assertTrue(factory.newRun().debug)
        assertTrue(factory.dailyRun(DailySeed).debug)
    }

    @Test
    fun `a run outside a debug session is not marked`() {
        val factory = RealRunFactory(defaultEngineConfig(), NoDebugController)

        assertTrue(!factory.newRun().debug)
        assertTrue(!factory.dailyRun(DailySeed).debug)
    }

    private companion object {
        const val DailySeed = 20_260_909L
    }
}

/**
 * The compiled-in defaults, assembled the way the app assembles them.
 *
 * An empty config map rather than a stub `RemoteEngineConfig`, because SPEC 10's
 * rule is that every key falls back to its compiled-in default, so this is also
 * the shape a device with no server reachable actually gets.
 */
private fun defaultEngineConfig() = RemoteEngineConfig(
    boardRows = BoardRows(EmptyConfigMap),
    blocksPerLevel = BlocksPerLevel(EmptyConfigMap),
    spawnCapDivisor = SpawnCapDivisor(EmptyConfigMap),
    spawnTable = SpawnTableValue(EmptyConfigMap),
    speedCurve = SpeedCurveMsPerRow(EmptyConfigMap),
    speedFloorMs = SpeedFloorMs(EmptyConfigMap),
    speedTailStepMs = SpeedTailStepMs(EmptyConfigMap),
    softDropMsPerRow = SoftDropMsPerRow(EmptyConfigMap),
    nudgeRows = NudgeRows(EmptyConfigMap),
    wildcardPerMille = WildcardPerMille(EmptyConfigMap),
    wildcardFirstLevel = WildcardFirstLevel(EmptyConfigMap),
    bombPerMille = BombPerMille(EmptyConfigMap),
    bombFirstLevel = BombFirstLevel(EmptyConfigMap),
    stonePerMille = StonePerMille(EmptyConfigMap),
    stoneFirstLevel = StoneFirstLevel(EmptyConfigMap),
)

private object EmptyConfigMap : AppConfigMap() {
    override val map: Map<String, *> = emptyMap<String, Any?>()
}
