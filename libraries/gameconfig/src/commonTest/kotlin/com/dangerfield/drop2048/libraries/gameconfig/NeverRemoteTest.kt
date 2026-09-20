package com.dangerfield.drop2048.libraries.gameconfig

import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.Scoring
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SPEC 10's never-remote list, guarded from the outside.
 *
 * The merge priority order, the resolution algorithm and the burst rule are not
 * data at all — they are code in `:libraries:cascade` with no config path to
 * reach them, so there is nothing here to assert about them. The scoring
 * formulas *are* data: `EngineConfig.scoring`, the cascade caps and the cap
 * floor are fields, and a future key pointed at one of them would compile
 * silently.
 *
 * So this feeds the assembler a map that names every plausible path for those
 * fields and asserts the result is unchanged. Changing scoring mid-flight
 * silently invalidates every high score on the board, and the point of this test
 * is that the invalidation would be silent.
 */
class NeverRemoteTest {

    private val backDoorAttempts = mapOf(
        "scoring.burstBase" to 1,
        "scoring.burstPerBlock" to 1,
        "scoring.bombPerBlock" to 1,
        "scoring.levelUpPerLevel" to 1,
        "scoring.survivalPerLevel" to 1,
        "scoring.boardCleared" to 1,
        "score.burstBase" to 1,
        "score.survivalPerLevel" to 1,
        "cascade.stepCap" to 1,
        "cascade.multiplierCap" to 99,
        "cascade.cascadeStepCap" to 1,
        "cascade.cascadeMultiplierCap" to 99,
        "board.cols" to 9,
        "spawn.cap.floor" to 64,
        "spawn.capFloor" to 64,
        "continue.rowsCleared" to 8,
        "level.continueRowsCleared" to 8,
    )

    @Test
    fun `no remote path reaches the scoring formulas`() {
        val config = remoteEngineConfig(TestAppConfigMap(backDoorAttempts)).current()
        assertEquals(Scoring(), config.scoring)
    }

    @Test
    fun `no remote path reaches the cascade caps or the column count or the cap floor or the continue reward`() {
        val config = remoteEngineConfig(TestAppConfigMap(backDoorAttempts)).current()

        assertEquals(EngineConfig.DEFAULT_STEP_CAP, config.cascadeStepCap)
        assertEquals(EngineConfig.DEFAULT_MULTIPLIER_CAP, config.cascadeMultiplierCap)
        assertEquals(EngineConfig.DEFAULT_COLS, config.cols)
        assertEquals(EngineConfig.DEFAULT_CAP_FLOOR, config.spawnCapFloor)
        assertEquals(EngineConfig.DEFAULT_CONTINUE_ROWS, config.continueRowsCleared)
        assertEquals(EngineConfig.DEFAULT_SUPPRESSED_DRAWS, config.specialSuppressedDraws)
    }

    @Test
    fun `a config carrying every wired override still scores the same way`() {
        val config = remoteEngineConfig(
            TestAppConfigMap(
                backDoorAttempts + mapOf(
                    "board.rows" to 7,
                    "level.blocksPerLevel" to 25,
                    "speed.curve" to listOf(400, 380),
                )
            )
        ).current()

        assertEquals(Scoring(), config.scoring)
        assertEquals(7, config.rows)
    }
}
