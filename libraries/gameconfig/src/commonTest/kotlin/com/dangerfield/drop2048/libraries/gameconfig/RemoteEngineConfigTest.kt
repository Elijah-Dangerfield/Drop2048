package com.dangerfield.drop2048.libraries.gameconfig

import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.SpecialRate
import com.dangerfield.drop2048.libraries.cascade.SpeedCurve
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteEngineConfigTest {

    @Test
    fun `with no remote values at all the config is the compiled-in default`() {
        assertEquals(EngineConfig.Default, remoteEngineConfig(TestAppConfigMap.Empty).current())
    }

    @Test
    fun `every gameplay key overrides its compiled-in default`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(
                mapOf(
                    "board.rows" to 7,
                    "level.blocksPerLevel" to 15,
                    "spawn.cap.divisor" to 8,
                    "speed.curve" to listOf(600, 550, 500),
                    "speed.floorMs" to 100,
                    "speed.tailStepMs" to 3,
                    "speed.softDropMsPerRow" to 35,
                    "speed.nudgeRows" to 3,
                    "special.wildcard.perMille" to 40,
                    "special.wildcard.firstLevel" to 4,
                    "special.bomb.perMille" to 20,
                    "special.bomb.firstLevel" to 9,
                    "special.stone.perMille" to 60,
                    "special.stone.firstLevel" to 11,
                )
            )
        ).current()

        assertEquals(7, config.rows)
        assertEquals(15, config.blocksPerLevel)
        assertEquals(8, config.spawnCapDivisor)
        assertEquals(listOf(600, 550, 500), config.speed.msPerRow)
        assertEquals(100, config.speed.floorMs)
        assertEquals(3, config.speed.tailStepMs)
        assertEquals(35, config.speed.softDropMsPerRow)
        assertEquals(3, config.nudgeRows)
        assertEquals(
            listOf(
                SpecialRate(Special.WILDCARD, fromLevel = 4, perMille = 40),
                SpecialRate(Special.BOMB, fromLevel = 9, perMille = 20),
                SpecialRate(Special.STONE, fromLevel = 11, perMille = 60),
            ),
            config.specialRates,
        )
    }

    @Test
    fun `a remote spawn table replaces the shipped one`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(
                mapOf(
                    "spawn.table" to listOf(
                        mapOf(
                            "fromLevel" to 1,
                            "weights" to listOf(
                                mapOf("value" to "V2", "percent" to 50),
                                mapOf("value" to "V4", "percent" to 50),
                            ),
                        )
                    ).let { bands -> mapOf("bands" to bands) }
                )
            )
        ).current()

        assertEquals(1, config.spawnTable.bands.size)
        assertEquals(
            listOf(BlockValue.V2, BlockValue.V4),
            config.spawnTable.weightsFor(level = 1).map { it.value },
        )
    }

    @Test
    fun `the special order is the shipped one even when the server reorders the rates`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(
                mapOf(
                    "special.stone.perMille" to 10,
                    "special.wildcard.perMille" to 90,
                )
            )
        ).current()

        assertEquals(
            SpecialRate.Default.map { it.special },
            config.specialRates.map { it.special },
        )
    }

    @Test
    fun `a remote row count leaves the five columns and the centre spawn alone`() {
        val config = remoteEngineConfig(TestAppConfigMap(mapOf("board.rows" to 6))).current()
        assertEquals(EngineConfig.DEFAULT_COLS, config.cols)
        assertEquals(EngineConfig.DEFAULT_COLS / 2, config.spawnColumn)
    }

    @Test
    fun `a curve entry of zero would divide the drop timer by nothing so the whole curve is refused`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(mapOf("speed.curve" to listOf(500, 0, 400)))
        ).current()
        assertEquals(SpeedCurve.DEFAULT_MS_PER_ROW, config.speed.msPerRow)
    }

    @Test
    fun `an out-of-range key falls back alone and leaves its neighbours remote`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(
                mapOf(
                    "level.blocksPerLevel" to 0,
                    "board.rows" to 7,
                )
            )
        ).current()

        assertEquals(EngineConfig.DEFAULT_BLOCKS_PER_LEVEL, config.blocksPerLevel)
        assertEquals(7, config.rows)
    }

    @Test
    fun `speed floor stays positive so the drop timer can never spin`() {
        val config = remoteEngineConfig(TestAppConfigMap(mapOf("speed.floorMs" to -1))).current()
        assertTrue(config.speed.floorMs > 0)
        assertEquals(SpeedCurve.DEFAULT_FLOOR, config.speed.floorMs)
    }
}
