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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The second hazard of SPEC 19: that a debug override reaches a run that is
 * supposed to be honest.
 *
 * The guard is structural rather than a check — the overrides are not an
 * `EngineConfig`, so there is nothing about a debug run that could travel inside
 * a `GameState` (D5) into a replay. What is asserted here is the other half:
 * that the overrides do what they say on the run they are meant for, and that a
 * run started with the menu open is marked whatever was forced.
 *
 * There was a third half until D27, when the Daily Challenge was removed:
 * `dailyRun` ignored every override, and this file held the positive control
 * that proved the overrides were not simply inert.
 */
class DebugRunFactoryTest {

    private val config = EngineConfig.Default

    @Test
    fun `a forced seed starts the run on it`() {
        val factory = RealRunFactory(defaultEngineConfig(), FakeDebugController(DebugOverrides(seed = 4242)))

        assertEquals(4242, factory.newRun().seed)
    }

    @Test
    fun `a preset board starts the run on it`() {
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

    /** Every override at once, visibly changing the run it is given to. */
    @Test
    fun `the overrides change a run`() {
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
     * `DebugSessionTest` then hangs the three write guards off.
     */
    @Test
    fun `a run in a debug session is marked even with nothing forced`() {
        val factory = RealRunFactory(defaultEngineConfig(), FakeDebugController(DebugOverrides.None))

        assertTrue(factory.newRun().debug)
    }

    @Test
    fun `a run outside a debug session is not marked`() {
        val factory = RealRunFactory(defaultEngineConfig(), NoDebugController)

        assertTrue(!factory.newRun().debug)
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
