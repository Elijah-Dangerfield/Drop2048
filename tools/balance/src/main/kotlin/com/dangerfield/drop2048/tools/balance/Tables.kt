package com.dangerfield.drop2048.tools.balance

import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.SpawnBand
import com.dangerfield.drop2048.libraries.cascade.SpawnTable
import com.dangerfield.drop2048.libraries.cascade.SpawnWeight

/**
 * Named spawn tables the harness can be pointed at with `--table`.
 *
 * [SpecOriginal] is a frozen copy of the table SPEC 5.3 shipped C1 with, written
 * out here rather than read from [SpawnTable.Default], which it currently equals.
 * The day someone does move the engine's default, the baseline it was measured
 * against still has to be re-runnable, or the before-and-after numbers in
 * `BUILD-PLAN.md`'s C1a outcome become claims nobody can check.
 *
 * The other three are the alternatives C1a measured and did not adopt. They stay
 * so the next person to propose a retune argues with a number rather than with
 * the last person's taste.
 */
object Tables {

    val SpecOriginal = table(
        band(1, BlockValue.V2 to 65, BlockValue.V4 to 35),
        band(4, BlockValue.V2 to 35, BlockValue.V4 to 45, BlockValue.V8 to 20),
        band(7, BlockValue.V2 to 10, BlockValue.V4 to 45, BlockValue.V8 to 35, BlockValue.V16 to 10),
        band(10, BlockValue.V4 to 30, BlockValue.V8 to 40, BlockValue.V16 to 25, BlockValue.V32 to 5),
        band(13, BlockValue.V4 to 15, BlockValue.V8 to 35, BlockValue.V16 to 35, BlockValue.V32 to 15),
        band(16, BlockValue.V8 to 25, BlockValue.V16 to 35, BlockValue.V32 to 30, BlockValue.V64 to 10),
        band(19, BlockValue.V8 to 15, BlockValue.V16 to 30, BlockValue.V32 to 35, BlockValue.V64 to 20),
    )

    /**
     * [SpecOriginal] with the low tiers held open for three more bands, as the
     * gentlest ramp worth measuring.
     *
     * Measured and rejected. It moves Greedy's median level by nothing and cuts
     * the share of runs passing 1024 from 30% to 7%, which is the evidence that
     * the spawn table sets the tier ceiling while the board geometry sets the
     * level. Re-run it before proposing a softer ramp again.
     */
    val LowFloor = table(
        band(1, BlockValue.V2 to 65, BlockValue.V4 to 35),
        band(4, BlockValue.V2 to 45, BlockValue.V4 to 45, BlockValue.V8 to 10),
        band(7, BlockValue.V2 to 30, BlockValue.V4 to 45, BlockValue.V8 to 20, BlockValue.V16 to 5),
        band(10, BlockValue.V2 to 20, BlockValue.V4 to 40, BlockValue.V8 to 30, BlockValue.V16 to 10),
        band(13, BlockValue.V2 to 10, BlockValue.V4 to 35, BlockValue.V8 to 35, BlockValue.V16 to 20),
        band(16, BlockValue.V4 to 30, BlockValue.V8 to 35, BlockValue.V16 to 25, BlockValue.V32 to 10),
        band(19, BlockValue.V4 to 20, BlockValue.V8 to 35, BlockValue.V16 to 30, BlockValue.V32 to 15),
    )

    /**
     * Halfway between [SpecOriginal] and [LowFloor]: the ramp still climbs, one
     * band slower. Measured and rejected for the same reason as [LowFloor].
     */
    val Slowed = table(
        band(1, BlockValue.V2 to 65, BlockValue.V4 to 35),
        band(4, BlockValue.V2 to 45, BlockValue.V4 to 45, BlockValue.V8 to 10),
        band(7, BlockValue.V2 to 25, BlockValue.V4 to 45, BlockValue.V8 to 25, BlockValue.V16 to 5),
        band(10, BlockValue.V2 to 12, BlockValue.V4 to 38, BlockValue.V8 to 35, BlockValue.V16 to 15),
        band(13, BlockValue.V2 to 5, BlockValue.V4 to 30, BlockValue.V8 to 35, BlockValue.V16 to 25, BlockValue.V32 to 5),
        band(16, BlockValue.V4 to 20, BlockValue.V8 to 35, BlockValue.V16 to 30, BlockValue.V32 to 15),
        band(19, BlockValue.V4 to 10, BlockValue.V8 to 30, BlockValue.V16 to 35, BlockValue.V32 to 20, BlockValue.V64 to 5),
    )

    /**
     * [SpecOriginal] with every band pulled one tier higher, as the other bracket
     * on the ramp.
     *
     * Measured and not adopted, which is a weaker verdict than the two above: it
     * buys a slightly richer tail past 1024 for a slightly shorter run and is a
     * real alternative rather than a worse one. It is kept as the thing to reach
     * for first if live data says the tail is too thin.
     */
    val Steep = table(
        band(1, BlockValue.V2 to 55, BlockValue.V4 to 45),
        band(4, BlockValue.V2 to 25, BlockValue.V4 to 45, BlockValue.V8 to 30),
        band(7, BlockValue.V4 to 40, BlockValue.V8 to 40, BlockValue.V16 to 20),
        band(10, BlockValue.V4 to 20, BlockValue.V8 to 40, BlockValue.V16 to 30, BlockValue.V32 to 10),
        band(13, BlockValue.V4 to 10, BlockValue.V8 to 30, BlockValue.V16 to 35, BlockValue.V32 to 20, BlockValue.V64 to 5),
        band(16, BlockValue.V8 to 20, BlockValue.V16 to 30, BlockValue.V32 to 35, BlockValue.V64 to 15),
        band(19, BlockValue.V8 to 10, BlockValue.V16 to 25, BlockValue.V32 to 40, BlockValue.V64 to 25),
    )

    val Named: Map<String, SpawnTable> = mapOf(
        "default" to SpawnTable.Default,
        "spec" to SpecOriginal,
        "slowed" to Slowed,
        "lowfloor" to LowFloor,
        "steep" to Steep,
    )

    private fun table(vararg bands: SpawnBand) = SpawnTable(bands.toList())

    private fun band(fromLevel: Int, vararg weights: Pair<BlockValue, Int>) =
        SpawnBand(fromLevel, weights.map { SpawnWeight(it.first, it.second) })
}
