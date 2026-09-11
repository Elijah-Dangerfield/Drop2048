package com.dangerfield.drop2048.tools.balance

import com.dangerfield.drop2048.libraries.cascade.autoplay.Policy
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
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
 * ```
 *
 * `--clock off` is C1a's clock-free harness and reports a ceiling. Any other
 * value is a [PlayerProfile] and puts SPEC 5.5's drop timer and SPEC 6's
 * controls in the loop.
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
        }
        print(Report.render(policy, tableName, label, outcomes, millis))
        println()
    }
}

private const val DEFAULT_RUNS = 10_000
private const val DEFAULT_SEED = 1L

private fun Array<String>.value(flag: String): String? {
    val index = indexOf(flag)
    return if (index >= 0 && index + 1 < size) this[index + 1] else null
}

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}
