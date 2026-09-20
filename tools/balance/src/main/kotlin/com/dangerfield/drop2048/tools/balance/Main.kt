package com.dangerfield.drop2048.tools.balance

import com.dangerfield.drop2048.libraries.cascade.autoplay.Policy
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.SpecialRate
import java.util.stream.Collectors
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

/**
 * SPEC 4.4's balance harness.
 *
 * ```
 * ./gradlew :tools:balance:run
 * ./gradlew :tools:balance:run --args="--runs 10000 --table spec"
 * ./gradlew :tools:balance:run --args="--policy greedy --table lowfloor --seed 99"
 * ./gradlew :tools:balance:run --args="--clock average --curve fast500"
 * ./gradlew :tools:balance:run --args="--clock average --blocks-per-level 12"
 * ./gradlew :tools:balance:run --args="--clock average --decision-millis 350"
 * ./gradlew :tools:balance:run --args="--clock average --specials-from 4,6,9"
 * ```
 *
 * `--clock off` is C1a's clock-free harness and reports a ceiling. Any other
 * value is a [PlayerProfile] and puts SPEC 5.5's drop timer and SPEC 6's
 * controls in the loop.
 *
 * `--specials-from` is SPEC 10's `special.firstLevel`, one level per special in
 * the order `SpecialRate.Default` declares them — Wildcard, Bomb, Stone. It is
 * here because the owner asked on 2026-09-20 whether the three arrive too late,
 * and the honest answer needed a sweep rather than an opinion. Unlike
 * `--decision-millis` it *is* an engine input: it travels inside `EngineConfig`
 * and therefore inside `GameState`, so a run played with it is a run under a
 * different config and its numbers are not comparable to a default one's by
 * anything except this harness.
 *
 * `--decision-millis` and `--tap-millis` are the two dials of the player model.
 * Neither touches anything the engine can see, so neither can move the
 * determinism digest. They are flags rather than edits because a swept number
 * that has to be typed into a source file gets swept once and then left wrong.
 *
 * `--nudge-rows` retired with the nudge (decision D21). There is no dial on the
 * drop control any more: ▼ goes to the bottom and that is the whole of it.
 *
 * Runs are independent and are farmed out across cores, which is the only reason
 * 30,000 of them finish inside a minute. Each run seeds both the engine and the
 * policy's tie-breaking coin from its own index, so a single run is reproducible
 * on its own with `--runs 1 --seed n` regardless of how many threads played it.
 */
fun main(args: Array<String>) {
    val runs = args.value("--runs")?.toIntOrNull() ?: DEFAULT_RUNS
    val baseSeed = args.value("--seed")?.toLongOrNull() ?: DEFAULT_SEED
    val tableName = args.value("--table") ?: "default"
    val policyName = args.value("--policy")
    val clockName = args.value("--clock") ?: "off"
    val curveName = args.value("--curve") ?: "default"
    val blocksPerLevel = args.value("--blocks-per-level")?.toIntOrNull()
        ?: EngineConfig.DEFAULT_BLOCKS_PER_LEVEL
    val decisionMillis = args.value("--decision-millis")?.toIntOrNull()
    val tapMillis = args.value("--tap-millis")?.toIntOrNull()
    val specialsFrom = args.value("--specials-from")?.let(::specialLevels)

    val table = Tables.Named[tableName] ?: fail(
        "unknown --table $tableName, expected one of ${Tables.Named.keys.joinToString()}"
    )
    val policies = policyName?.let {
        listOf(Policy.byName(it) ?: fail("unknown --policy $it, expected one of ${Policy.All.joinToString { p -> p.name }}"))
    } ?: Policy.All

    val curve = Curves.Named[curveName] ?: fail(
        "unknown --curve $curveName, expected one of ${Curves.Named.keys.joinToString()}"
    )
    val profile = if (clockName.equals("off", ignoreCase = true)) {
        null
    } else {
        val named = PlayerProfile.byName(clockName) ?: fail(
            "unknown --clock $clockName, expected off or one of ${PlayerProfile.All.joinToString { it.name }}"
        )
        named.copy(
            decisionMillis = decisionMillis ?: named.decisionMillis,
            tapMillis = tapMillis ?: named.tapMillis,
        )
    }

    val config = EngineConfig.Default.copy(
        spawnTable = table,
        speed = curve,
        blocksPerLevel = blocksPerLevel,
        specialRates = specialsFrom ?: EngineConfig.Default.specialRates,
    )

    policies.forEach { policy ->
        lateinit var outcomes: List<RunOutcome>
        val millis = measureTimeMillis {
            outcomes = (0 until runs).toList().parallelStream()
                .map { Harness.play(baseSeed + it, policy, config, profile) }
                .collect(Collectors.toList())
        }
        val label = buildString {
            append("$curveName/$blocksPerLevel@${profile?.name ?: "off"}")
            if (profile != null) append("(${profile.decisionMillis}/${profile.tapMillis})")
            append("/sp=${config.specialRates.joinToString("+") { it.fromLevel.toString() }}")
        }
        print(Report.render(policy, tableName, label, outcomes, millis))
        println()
    }
}

private const val DEFAULT_RUNS = 10_000
private const val DEFAULT_SEED = 1L

private fun specialLevels(raw: String): List<SpecialRate> {
    val levels = raw.split(",").map { part ->
        part.trim().toIntOrNull()?.takeIf { it >= 1 }
            ?: fail("--specials-from takes positive levels, got '$part'")
    }
    val defaults = SpecialRate.Default
    if (levels.size != defaults.size) {
        fail("--specials-from takes ${defaults.size} levels (${defaults.joinToString(",") { it.special.name.lowercase() }}), got ${levels.size}")
    }
    return defaults.mapIndexed { index, rate -> rate.copy(fromLevel = levels[index]) }
}

private fun Array<String>.value(flag: String): String? {
    val index = indexOf(flag)
    return if (index >= 0 && index + 1 < size) this[index + 1] else null
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
