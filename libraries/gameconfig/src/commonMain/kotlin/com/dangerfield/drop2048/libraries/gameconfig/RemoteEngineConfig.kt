package com.dangerfield.drop2048.libraries.gameconfig

import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.SpecialRate
import com.dangerfield.drop2048.libraries.cascade.SpeedCurve
import com.dangerfield.drop2048.libraries.config.ConfiguredValue
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * SPEC 10's gameplay keys, assembled into the one value the engine reads.
 *
 * ### When a fetched config takes effect
 *
 * At the **start of the next run**, and never mid-run. [current] is a snapshot
 * read, called once by `RunFactory.newRun()`; nothing else in the app calls it.
 * Everything the screen needs while a run is alive — the speed curve, the level
 * bar's denominator, the nudge distance — is read off `GameState.config`, which
 * travels *inside* the state (D5) and is therefore frozen at the moment the run
 * started. A config refresh landing on the app's next foreground cannot reach a
 * run that is already in flight, and a run resumed from the saved blob comes
 * back on the numbers it was played under rather than today's.
 *
 * That is not incidental. D5 puts `EngineConfig` inside `GameState` so a seed
 * reproduces a run byte for byte, and the failure it exists to prevent is the
 * quiet one: a Daily Challenge or a seed-attached bug report that still replays,
 * just differently. Rules changing under a run in progress is the same failure
 * one scale smaller, and the same fix covers both.
 *
 * ### Malformed values fall back, they do not crash
 *
 * Each key is range-checked against its own compiled-in default, so one bad
 * number costs that number rather than the whole config. `EngineConfig`'s and
 * `SpeedCurve`'s `require` blocks are the backstop: whatever gets past the
 * per-key checks is assembled inside [Catching], and a throw there resolves to
 * [EngineConfig.Default]. The binary stays fully playable with the server
 * unreachable, serving garbage, or serving a schema this build has never seen.
 */
@Inject
@SingleIn(AppScope::class)
class RemoteEngineConfig(
    private val boardRows: BoardRows,
    private val blocksPerLevel: BlocksPerLevel,
    private val spawnCapDivisor: SpawnCapDivisor,
    private val spawnTable: SpawnTableValue,
    private val speedCurve: SpeedCurveMsPerRow,
    private val speedFloorMs: SpeedFloorMs,
    private val speedTailStepMs: SpeedTailStepMs,
    private val softDropMsPerRow: SoftDropMsPerRow,
    private val nudgeRows: NudgeRows,
    private val wildcardPerMille: WildcardPerMille,
    private val wildcardFirstLevel: WildcardFirstLevel,
    private val bombPerMille: BombPerMille,
    private val bombFirstLevel: BombFirstLevel,
    private val stonePerMille: StonePerMille,
    private val stoneFirstLevel: StoneFirstLevel,
) {

    /** The config a run started right now would be played under. */
    fun current(): EngineConfig = Catching { assemble() }
        .logOnFailure { "Remote engine config did not validate; falling back to the compiled defaults" }
        .getOrDefault(EngineConfig.Default)

    private fun assemble(): EngineConfig = EngineConfig(
        rows = boardRows.within(MIN_ROWS..MAX_ROWS),
        nudgeRows = nudgeRows.within(1..MAX_NUDGE_ROWS),
        blocksPerLevel = blocksPerLevel.within(1..MAX_BLOCKS_PER_LEVEL),
        spawnCapDivisor = spawnCapDivisor.within(1..MAX_CAP_DIVISOR),
        spawnTable = spawnTable.value,
        specialRates = specialRates(),
        speed = SpeedCurve(
            msPerRow = curve(),
            tailStepMs = speedTailStepMs.within(0..MAX_TAIL_STEP_MS),
            floorMs = speedFloorMs.within(1..MAX_MS_PER_ROW),
            softDropMsPerRow = softDropMsPerRow.within(1..MAX_MS_PER_ROW),
        ),
    )

    private fun curve(): List<Int> = speedCurve.value
        .takeIf { entries -> entries.isNotEmpty() && entries.all { it in 1..MAX_MS_PER_ROW } }
        ?: speedCurve.default

    /**
     * The order is [SpecialRate.Default]'s, not the server's. It decides how one
     * spawn roll is split across the eligible specials, which makes it part of
     * the resolution algorithm SPEC 10 keeps off the wire.
     */
    private fun specialRates(): List<SpecialRate> = SpecialRate.Default.map { fallback ->
        val perMille = when (fallback.special) {
            Special.WILDCARD -> wildcardPerMille
            Special.BOMB -> bombPerMille
            Special.STONE -> stonePerMille
        }
        val fromLevel = when (fallback.special) {
            Special.WILDCARD -> wildcardFirstLevel
            Special.BOMB -> bombFirstLevel
            Special.STONE -> stoneFirstLevel
        }
        SpecialRate(
            special = fallback.special,
            fromLevel = fromLevel.within(1..MAX_FIRST_LEVEL),
            perMille = perMille.within(0..SpecialRate.SCALE),
        )
    }

    /**
     * The remote number when it is sane, this value's own compiled-in default
     * when it is not. Per-key rather than per-config so a typo in one row of the
     * admin console cannot discard the other fourteen.
     */
    private fun ConfiguredValue<Int>.within(range: IntRange): Int =
        value.takeIf { it in range } ?: default

    private companion object {
        const val MIN_ROWS = 4
        const val MAX_ROWS = 16
        const val MAX_NUDGE_ROWS = 8
        const val MAX_BLOCKS_PER_LEVEL = 500
        const val MAX_CAP_DIVISOR = 1024
        const val MAX_MS_PER_ROW = 10_000
        const val MAX_TAIL_STEP_MS = 1_000
        const val MAX_FIRST_LEVEL = 1_000
    }
}
