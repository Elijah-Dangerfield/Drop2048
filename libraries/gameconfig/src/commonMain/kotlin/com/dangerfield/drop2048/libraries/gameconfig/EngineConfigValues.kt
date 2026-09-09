package com.dangerfield.drop2048.libraries.gameconfig

import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.SpawnTable
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.SpecialRate
import com.dangerfield.drop2048.libraries.cascade.SpeedCurve
import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.config.IntConfigValue
import com.dangerfield.drop2048.libraries.config.JsonConfigValue
import com.dangerfield.drop2048.libraries.config.QaConfigValue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * SPEC 10's gameplay keys, one [com.dangerfield.drop2048.libraries.config.ConfiguredValue]
 * per key.
 *
 * Every default here is read off [EngineConfig]'s own companion rather than
 * retyped, so the compiled-in fallback and the number the engine ships with are
 * the same constant. A key the server never mentions resolves to that constant
 * and the game plays exactly as an offline binary does.
 *
 * These are *inputs* to [RemoteEngineConfig], which is the only thing that
 * assembles them into an [EngineConfig] and the only place range checks live.
 * Nothing in this file reaches the engine on its own.
 *
 * SPEC 10's never-remote list — the merge priority order, the resolution
 * algorithm, the burst rule and the scoring formulas — has no entry here and
 * must never gain one. `EngineConfig.scoring`, `cascadeStepCap`,
 * `cascadeMultiplierCap`, `spawnCapFloor`, `continueRowsCleared` and `cols` are
 * deliberately absent; `RemoteEngineConfigNeverRemoteTest` fails if one appears.
 */

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BoardRows(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Board rows"
    override val description = "Settles SPEC 3's 7-vs-8 question with live data. Takes effect on the next run."
    override val path = "board.rows"
    override val default = EngineConfig.DEFAULT_ROWS
}

/**
 * The clock (SPEC 5.5), and the one key on this list that is **not** safe to
 * turn live.
 *
 * D9 measured it: shortening it to 15 or 12 shortens runs by about 11%, thins
 * the tail past 1024, and — unlike the speed curve — feeds level advancement, so
 * it **moves the pinned determinism digest**. Every Daily Challenge score and
 * every seed-attached bug report recorded under the old value replays
 * differently under the new one, which is the expensive kind of wrong: the run
 * still replays, it just replays as a different run.
 *
 * It is wired rather than excluded because D9 explicitly keeps it in reserve as
 * the knob to reach for if live data says runs feel long. The admin console
 * carries the warning (`dangerousWarning` in `:apps:admin`), so changing it is a
 * deliberate act rather than an incidental one.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BlocksPerLevel(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Blocks per level"
    override val description =
        "MOVES THE DETERMINISM DIGEST. Changing this invalidates every Daily Challenge score and " +
            "every seed-attached bug report recorded under the old value (D9). Prefer the speed curve."
    override val path = "level.blocksPerLevel"
    override val default = EngineConfig.DEFAULT_BLOCKS_PER_LEVEL
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class SpawnCapDivisor(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Spawn cap divisor"
    override val description =
        "SPEC 5.3 constraint B. The spawned value may not exceed highestOnBoard / this. Lower is more aggressive."
    override val path = "spawn.cap.divisor"
    override val default = EngineConfig.DEFAULT_CAP_DIVISOR
}

/**
 * SPEC 5.3's level table, and the highest-leverage number in the game.
 *
 * Safe to turn live in the sense that matters operationally: L19 measured that
 * it sets the *tier* ceiling and moves the median level by at most one across
 * tables that move the 1024 rate fivefold. It does move the digest, like any
 * engine input, which is why C6's Daily Challenge pins the config it was played
 * under inside the state (D5) instead of trusting the table to hold still.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class SpawnTableValue(appConfigMap: AppConfigMap) : JsonConfigValue<SpawnTable>(
    appConfigMap = appConfigMap,
    serializer = SpawnTable.serializer(),
) {
    override val name = "Spawn table"
    override val description = "SPEC 5.3 level bands. Each band's weights must sum to 100 or the whole table is ignored."
    override val path = "spawn.table"
    override val default = SpawnTable.Default
}

/**
 * SPEC 5.5's `ms per row` ladder, indexed by level. Levels past its end fall
 * away by [SpeedTailStepMs] down to [SpeedFloorMs].
 *
 * The freest knob on the list. L28 played three curves 10,000 runs each and got
 * outcome columns identical to the digit — median level, drop count, 1024 and
 * 2048 rates, clutter. Only the wall clock moved. It is a pacing dial and it
 * cannot be reached for to change difficulty.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class SpeedCurveMsPerRow(appConfigMap: AppConfigMap) : JsonConfigValue<List<Int>>(
    appConfigMap = appConfigMap,
    serializer = ListSerializer(Int.serializer()),
) {
    override val name = "Speed curve"
    override val description = "Milliseconds per row, one entry per level from 1. Pacing only (L28)."
    override val path = "speed.curve"
    override val default = SpeedCurve.DEFAULT_MS_PER_ROW
}

/**
 * Where the game stops being a puzzle (SPEC 5.5). Past roughly level 20 speed
 * stops being the pressure and the rising spawn floor carries the difficulty;
 * without a floor this becomes a reflex test, which is a different and worse
 * game.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class SpeedFloorMs(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Speed floor (ms)"
    override val description = "The fastest a row can ever fall. Load-bearing: SPEC 5.5 calls a missing floor a worse game."
    override val path = "speed.floorMs"
    override val default = SpeedCurve.DEFAULT_FLOOR
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class SpeedTailStepMs(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Speed tail step (ms)"
    override val description = "Milliseconds shaved per level past the end of the curve, until the floor."
    override val path = "speed.tailStepMs"
    override val default = SpeedCurve.DEFAULT_TAIL_STEP
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class SoftDropMsPerRow(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Soft drop (ms per row)"
    override val description = "Flat at every level (SPEC 5.5)."
    override val path = "speed.softDropMsPerRow"
    override val default = SpeedCurve.DEFAULT_SOFT_DROP
}

/**
 * How many rows one press of ▼ is worth (D11), and a genuinely free live knob.
 *
 * L40 measured it digest-free rather than reasoning about it: `Input.Lock`
 * places at the block's *landing* cell, so the vertical position a nudge left it
 * at is not an input to anything the engine records. A drop control decides when
 * a block locks and never where.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class NudgeRows(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Nudge rows"
    override val description = "Rows one ▼ press is worth. Digest-free (L40): swept 1-4 in C1e and left at 2."
    override val path = "speed.nudgeRows"
    override val default = EngineConfig.DEFAULT_NUDGE_ROWS
}

/**
 * SPEC 5.2's three specials, per type, as SPEC 10 asks.
 *
 * Rate and first level are remote; the **order** of [SpecialRate.Default] is
 * not. That order is load-bearing — the single spawn roll is compared against
 * the cumulative rate of the eligible specials in it — so exposing it would be a
 * back door into the resolution algorithm SPEC 10 puts out of reach.
 */
abstract class SpecialRatePerMille(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val description = "Rate in per-mille (30 = 3%). SPEC 5.2."
}

abstract class SpecialFirstLevel(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val description = "The level this special starts appearing at. SPEC 5.2."
}

private fun defaultRate(special: Special) =
    SpecialRate.Default.first { it.special == special }

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class WildcardPerMille(appConfigMap: AppConfigMap) : SpecialRatePerMille(appConfigMap) {
    override val name = "Wildcard rate"
    override val path = "special.wildcard.perMille"
    override val default = defaultRate(Special.WILDCARD).perMille
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class WildcardFirstLevel(appConfigMap: AppConfigMap) : SpecialFirstLevel(appConfigMap) {
    override val name = "Wildcard first level"
    override val path = "special.wildcard.firstLevel"
    override val default = defaultRate(Special.WILDCARD).fromLevel
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BombPerMille(appConfigMap: AppConfigMap) : SpecialRatePerMille(appConfigMap) {
    override val name = "Bomb rate"
    override val path = "special.bomb.perMille"
    override val default = defaultRate(Special.BOMB).perMille
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class BombFirstLevel(appConfigMap: AppConfigMap) : SpecialFirstLevel(appConfigMap) {
    override val name = "Bomb first level"
    override val path = "special.bomb.firstLevel"
    override val default = defaultRate(Special.BOMB).fromLevel
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class StonePerMille(appConfigMap: AppConfigMap) : SpecialRatePerMille(appConfigMap) {
    override val name = "Stone rate"
    override val path = "special.stone.perMille"
    override val default = defaultRate(Special.STONE).perMille
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class StoneFirstLevel(appConfigMap: AppConfigMap) : SpecialFirstLevel(appConfigMap) {
    override val name = "Stone first level"
    override val path = "special.stone.firstLevel"
    override val default = defaultRate(Special.STONE).fromLevel
}
