package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.debug.NoDebugController
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.cascade.Input
import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.gameconfig.BlocksPerLevel
import com.dangerfield.drop2048.libraries.gameconfig.BombFirstLevel
import com.dangerfield.drop2048.libraries.gameconfig.BombPerMille
import com.dangerfield.drop2048.libraries.gameconfig.BoardRows
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
import com.dangerfield.drop2048.libraries.progress.GameMode
import com.dangerfield.drop2048.libraries.progress.daily.dailySeedFor
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The ruling C6 exists to enforce: **a Daily run pins the compiled-in
 * `EngineConfig` and never reads remote config.**
 *
 * SPEC 14's promise is that everyone played the same game. C7's rule — a fetched
 * config is sampled at the start of a run and never changes under one — keeps a
 * single run coherent and is not enough for that promise: two players starting
 * the same seed ten minutes apart, one either side of a config push, would each
 * get an internally coherent run and a different board. The seed would match, the
 * scores would not be comparable, and nothing anywhere would say so.
 *
 * So the enforcement is a *sequence* comparison rather than an equality check on
 * a config object. Comparing configs would pass if a later refactor read remote
 * config and happened to get the same numbers back; comparing the blocks two
 * devices actually see is the claim the mode makes.
 *
 * **What this makes immovable.** From the first recorded Daily score,
 * [EngineConfig.Default] is a leaderboard-visible constant: moving a field of it
 * in a release splits that day's board between app versions. The pinned
 * determinism digest in `:libraries:cascade` and `level.blocksPerLevel` are in
 * exactly the same position. See `docs/decisions.md`.
 */
class DailyConfigPinningTest {

    /**
     * Two devices, same UTC day, byte-identical block sequences — with one of
     * them holding a fetched config that moves nearly every gameplay key.
     *
     * This is the whole chunk in one assertion. It fails the moment
     * `dailyRun` starts consulting [RemoteEngineConfig].
     */
    @Test
    fun twoDevicesOnTheSameDayPlayTheSameBoardUnderDifferentFetchedConfigs() {
        val day = LocalDate(2026, 9, 9)
        val seed = dailySeedFor(day)

        val untouched = RealRunFactory(remoteEngineConfig(TestConfigMap()), NoDebugController).dailyRun(seed)
        val pushed = RealRunFactory(remoteEngineConfig(TestConfigMap(EveryKeyMoved)), NoDebugController).dailyRun(seed)

        assertEquals(blockSequence(untouched.state), blockSequence(pushed.state))
        assertEquals(GameMode.DAILY, untouched.mode)
        assertEquals(GameMode.DAILY, pushed.mode)
    }

    /**
     * The positive control (L35). A rejection test proves nothing unless the same
     * config really would have changed the run — without this, a `dailyRun` that
     * returned an empty board would pass the test above.
     */
    @Test
    fun theSameFetchedConfigDoesChangeAnEndlessRun() {
        val untouched = RealRunFactory(remoteEngineConfig(TestConfigMap()), NoDebugController).newRunOn(Seed)
        val pushed = RealRunFactory(remoteEngineConfig(TestConfigMap(EveryKeyMoved)), NoDebugController).newRunOn(Seed)

        assertNotEquals(blockSequence(untouched), blockSequence(pushed))
    }

    @Test
    fun aDailyRunCarriesTheCompiledInConfigVerbatim() {
        val started = RealRunFactory(remoteEngineConfig(TestConfigMap(EveryKeyMoved)), NoDebugController)
            .dailyRun(dailySeedFor(LocalDate(2026, 9, 9)))

        assertEquals(EngineConfig.Default, started.state.config)
    }

    /**
     * The seed is the day's and nothing else's, so the run a device starts is the
     * one the ledger opened. Two dates must not collide onto one board.
     */
    @Test
    fun differentDaysAreDifferentBoards() {
        val factory = RealRunFactory(remoteEngineConfig(TestConfigMap()), NoDebugController)
        val today = factory.dailyRun(dailySeedFor(LocalDate(2026, 9, 9)))
        val tomorrow = factory.dailyRun(dailySeedFor(LocalDate(2026, 9, 10)))

        assertNotEquals(blockSequence(today.state), blockSequence(tomorrow.state))
    }

    /**
     * The blocks a player would actually see, in order.
     *
     * Driven by locking every block straight down, which is the shortest script
     * that exercises spawn draws, the spawn table, the special rates and the
     * level clock — every remote key that could reshape a day.
     */
    private fun blockSequence(start: GameState): List<String> {
        var state = start
        val blocks = mutableListOf<String>()
        repeat(DropsSampled) {
            blocks += state.falling?.block?.toString() ?: return@repeat
            state = Cascade.apply(state, Input.Lock).state
            if (state.isOver) return blocks
        }
        return blocks
    }

    private fun RunFactory.newRunOn(seed: Long): GameState =
        Cascade.newGame(seed = seed, config = newRun().state.config)

    private companion object {
        const val DropsSampled = 60
        const val Seed = 20260909L

        /**
         * Every gameplay key SPEC 10 makes remote, moved to a value that is legal
         * and different. `spawn.table` and `speed.curve` are the two with the most
         * reach; `board.rows` is the one whose effect is visible without playing.
         */
        val EveryKeyMoved = mapOf<String, Any?>(
            "board.rows" to 6,
            "level.blocksPerLevel" to 5,
            "spawn.cap.divisor" to 4,
            "speed.floorMs" to 200,
            "speed.tailStepMs" to 9,
            "special.wildcard.perMille" to 500,
            "special.wildcard.firstLevel" to 1,
            "special.bomb.perMille" to 200,
            "special.bomb.firstLevel" to 1,
            "special.stone.perMille" to 200,
            "special.stone.firstLevel" to 1,
        )
    }
}

/** A merged config snapshot built from dotted paths, as the server's tree arrives. */
private class TestConfigMap(overrides: Map<String, Any?> = emptyMap()) : AppConfigMap() {
    override val map: Map<String, *> = overrides.entries
        .fold(emptyMap<String, Any?>()) { acc, entry -> acc.put(entry.key.split('.'), entry.value) }

    private fun Map<String, Any?>.put(path: List<String>, value: Any?): Map<String, Any?> {
        val head = path.first()
        if (path.size == 1) return this + (head to value)
        @Suppress("UNCHECKED_CAST")
        val child = (this[head] as? Map<String, Any?>) ?: emptyMap()
        return this + (head to child.put(path.drop(1), value))
    }
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
