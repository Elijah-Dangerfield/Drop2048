package com.dangerfield.drop2048.tools.balance

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
 * ```
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

    val table = Tables.Named[tableName] ?: fail(
        "unknown --table $tableName, expected one of ${Tables.Named.keys.joinToString()}"
    )
    val policies = policyName?.let {
        listOf(Policy.byName(it) ?: fail("unknown --policy $it, expected one of ${Policy.All.joinToString { p -> p.name }}"))
    } ?: Policy.All

    val config = EngineConfig.Default.copy(spawnTable = table)

    policies.forEach { policy ->
        lateinit var outcomes: List<RunOutcome>
        val millis = measureTimeMillis {
            outcomes = (0 until runs).toList().parallelStream()
                .map { Harness.play(baseSeed + it, policy, config) }
                .collect(Collectors.toList())
        }
        print(Report.render(policy.name, tableName, outcomes, millis))
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
