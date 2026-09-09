package com.dangerfield.drop2048.libraries.gameconfig

import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.SpawnTable
import com.dangerfield.drop2048.libraries.cascade.SpeedCurve
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The failure mode that ships.
 *
 * A remote value arrives from a database a human edits by hand, over a wire, into
 * a build that may be older than the schema. Every one of these cases used to be
 * a `require` in `EngineConfig`'s init block away from taking the app down on the
 * first tap of Play, which is a worse outcome than the config not applying.
 *
 * The rule these pin: **a malformed value costs that value, never the run and
 * never the process.**
 */
class MalformedRemoteValueTest {

    @Test
    fun `a number sent as garbage text falls back`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(
                mapOf(
                    "board.rows" to "eight",
                    "level.blocksPerLevel" to "twenty",
                    "speed.floorMs" to "fast",
                )
            )
        ).current()

        assertEquals(EngineConfig.Default, config)
    }

    @Test
    fun `a number sent as an object falls back`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(mapOf("board.rows" to mapOf("value" to 7)))
        ).current()

        assertEquals(EngineConfig.DEFAULT_ROWS, config.rows)
    }

    @Test
    fun `a scalar where the spawn table belongs falls back`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(mapOf("spawn.table" to "please make it easier"))
        ).current()

        assertEquals(SpawnTable.Default, config.spawnTable)
    }

    @Test
    fun `a spawn table whose band does not sum to a hundred falls back whole`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(
                mapOf(
                    "spawn.table" to mapOf(
                        "bands" to listOf(
                            mapOf(
                                "fromLevel" to 1,
                                "weights" to listOf(
                                    mapOf("value" to "V2", "percent" to 50),
                                    mapOf("value" to "V4", "percent" to 40),
                                ),
                            )
                        )
                    )
                )
            )
        ).current()

        assertEquals(SpawnTable.Default, config.spawnTable)
    }

    @Test
    fun `a spawn table naming a tier this build has never heard of falls back`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(
                mapOf(
                    "spawn.table" to mapOf(
                        "bands" to listOf(
                            mapOf(
                                "fromLevel" to 1,
                                "weights" to listOf(mapOf("value" to "V4096", "percent" to 100)),
                            )
                        )
                    )
                )
            )
        ).current()

        assertEquals(SpawnTable.Default, config.spawnTable)
    }

    @Test
    fun `an empty spawn table falls back rather than throwing on the first draw`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(mapOf("spawn.table" to mapOf("bands" to emptyList<Any>())))
        ).current()

        assertEquals(SpawnTable.Default, config.spawnTable)
    }

    @Test
    fun `an empty speed curve falls back`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(mapOf("speed.curve" to emptyList<Int>()))
        ).current()

        assertEquals(SpeedCurve.DEFAULT_MS_PER_ROW, config.speed.msPerRow)
    }

    @Test
    fun `a speed curve of strings falls back`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(mapOf("speed.curve" to listOf("500", "fast")))
        ).current()

        assertEquals(SpeedCurve.DEFAULT_MS_PER_ROW, config.speed.msPerRow)
    }

    @Test
    fun `a zero or negative value on any positive-only key falls back`() {
        val zeroed = mapOf(
            "board.rows" to 0,
            "level.blocksPerLevel" to -20,
            "spawn.cap.divisor" to 0,
            "speed.floorMs" to 0,
            "speed.softDropMsPerRow" to -40,
            "speed.nudgeRows" to 0,
            "special.wildcard.firstLevel" to 0,
            "special.bomb.firstLevel" to -8,
            "special.stone.firstLevel" to 0,
        )

        assertEquals(EngineConfig.Default, remoteEngineConfig(TestAppConfigMap(zeroed)).current())
    }

    @Test
    fun `a rate above the per-mille scale falls back`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(mapOf("special.wildcard.perMille" to 5_000))
        ).current()

        assertEquals(EngineConfig.Default.specialRates, config.specialRates)
    }

    @Test
    fun `every key set to garbage at once still yields a playable config`() {
        val garbage = mapOf(
            "board.rows" to "?",
            "level.blocksPerLevel" to "?",
            "spawn.cap.divisor" to "?",
            "spawn.table" to "?",
            "speed.curve" to "?",
            "speed.floorMs" to "?",
            "speed.tailStepMs" to "?",
            "speed.softDropMsPerRow" to "?",
            "speed.nudgeRows" to "?",
            "special.wildcard.perMille" to "?",
            "special.wildcard.firstLevel" to "?",
            "special.bomb.perMille" to "?",
            "special.bomb.firstLevel" to "?",
            "special.stone.perMille" to "?",
            "special.stone.firstLevel" to "?",
        )

        assertEquals(EngineConfig.Default, remoteEngineConfig(TestAppConfigMap(garbage)).current())
    }
}
