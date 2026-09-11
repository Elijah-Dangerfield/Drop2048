package com.dangerfield.drop2048.features.game.impl

/**
 * **The two numbers C8 exists to produce** (SPEC 17, L41).
 *
 * `tools/balance` models the player as two constants — `decisionMillis`, the
 * beat between a block appearing and the first steer, and `tapMillis`, the gap
 * between one column step and the next. Swept from 150ms to 500ms,
 * `decisionMillis` moves Greedy's median level by five and its 1024 rate by a
 * factor of three: more than the drop clock, the spawn table and every
 * speed-curve change in the project put together. Both numbers are guesses.
 * Every clocked balance figure this project has published is conditional on
 * them. This class is what turns them into measurements.
 *
 * ### It measures exactly what the harness models, and nothing adjacent
 *
 * `DropClock.play` issues one `Input.MoveLeft`/`MoveRight` per `tapMillis`
 * after an initial `decisionMillis` wait. So the live instrument counts
 * **accepted engine column steps**, not gestures and not screen touches. A
 * drag that carries the block three columns is three steps here exactly as it
 * is three taps there, and a drag that is refused by an occupied column
 * contributes nothing to either — which is what keeps the live histogram
 * comparable with a harness sweep rather than merely adjacent to it.
 *
 * ### A drop the player never steered is censored, not zero
 *
 * The modelled player always reaches its target, so "never steered" does not
 * exist offline. Live it is common: a block that spawns in a column the player
 * is happy with takes no input at all. Recording that as a 0ms decision would
 * make the population look three times faster than it is, and dropping it
 * silently would bias the other way by throwing out every easy board. So an
 * unsteered drop contributes to [unsteeredDrops] and to nothing else, and both
 * counts ship — an analyst can compute the steered-only median and the
 * censoring rate that qualifies it.
 *
 * ### A drop that spanned a pause is discarded
 *
 * [suspend] drops the drop in flight. The elapsed millis across a pause, a
 * backgrounding or a cascade the player watched are wall clock, not thinking
 * time, and one overnight pause would sit in the tail of the histogram
 * forever. Discarding is cheap: a run has tens to hundreds of drops and loses
 * at most one per interruption.
 *
 * ### It is not a firehose
 *
 * Nothing here emits. The ViewModel reads [lastDrop] on the every-tenth-drop
 * sample (so the distribution arrives with the level it was measured at) and
 * [summary] once at run end (so a three-drop run still contributes a number).
 * A 200-drop run produces twenty sample records and one summary, against the
 * two thousand a per-drop event would have been.
 *
 * Times are epoch millis from the ViewModel's injected clock, the same source
 * `RunTally.playedMs` already uses.
 */
internal class DecisionTimer {

    private var dropStartedAt: Long? = null
    private var lastStepAt: Long? = null
    private var firstStepMillis: Long? = null
    private var firstGapMillis: Long? = null
    private var stepsThisDrop: Int = 0

    private val steerMillis = Histogram()
    private val tapGapMillis = Histogram()

    var lastDrop: DropTiming? = null
        private set

    private var steeredDrops = 0
    private var unsteeredDrops = 0
    private var steps = 0

    /** A block exists and the clock is running. Resets the per-drop state. */
    fun dropBegan(atMs: Long) {
        dropStartedAt = atMs
        lastStepAt = null
        firstStepMillis = null
        firstGapMillis = null
        stepsThisDrop = 0
    }

    /**
     * One accepted column step. Called per engine step rather than per
     * gesture, because a step is what the harness costs at `tapMillis`.
     */
    fun columnStep(atMs: Long) {
        val began = dropStartedAt ?: return
        val previous = lastStepAt
        if (previous == null) {
            firstStepMillis = (atMs - began).coerceAtLeast(0)
        } else {
            val gap = (atMs - previous).coerceAtLeast(0)
            if (firstGapMillis == null) firstGapMillis = gap
            tapGapMillis.add(gap)
        }
        lastStepAt = atMs
        stepsThisDrop++
    }

    /**
     * The drop is over. Folds it into the run totals and publishes it as
     * [lastDrop] for the next per-drop sample to read.
     */
    fun dropEnded() {
        if (dropStartedAt == null) return
        val first = firstStepMillis
        if (first == null) {
            unsteeredDrops++
        } else {
            steeredDrops++
            steerMillis.add(first)
        }
        steps += stepsThisDrop
        lastDrop = DropTiming(
            firstStepMillis = first,
            firstGapMillis = firstGapMillis,
            steps = stepsThisDrop,
        )
        dropStartedAt = null
        lastStepAt = null
        firstStepMillis = null
        firstGapMillis = null
        stepsThisDrop = 0
    }

    /**
     * The drop in flight is no longer a measurement: a pause, a backgrounding,
     * a restart, or a tutorial taking the board over.
     */
    fun suspend() {
        dropStartedAt = null
        lastStepAt = null
        firstStepMillis = null
        firstGapMillis = null
        stepsThisDrop = 0
        lastDrop = null
    }

    /** Everything the run has measured, for `run.end`. */
    fun summary(): DecisionSummary = DecisionSummary(
        steerP50 = steerMillis.quantile(HALF),
        steerP90 = steerMillis.quantile(NINE_TENTHS),
        tapGapP50 = tapGapMillis.quantile(HALF),
        steeredDrops = steeredDrops,
        unsteeredDrops = unsteeredDrops,
        steps = steps,
    )

    private companion object {
        const val HALF = 0.5
        const val NINE_TENTHS = 0.9
    }
}

/**
 * One drop's timings, as the every-tenth-drop sample reports them.
 *
 * [firstStepMillis] is null when the player never steered, and
 * [firstGapMillis] — the gap between the first and second column step, which is
 * the harness's `tapMillis` — is null on a drop that took fewer than two. Null
 * rather than a sentinel because `logEvent` drops null attributes, so the
 * record simply carries no `steer_ms` and the query can count what is missing.
 */
internal data class DropTiming(
    val firstStepMillis: Long?,
    val firstGapMillis: Long?,
    val steps: Int,
)

/**
 * A run's worth of the two numbers.
 *
 * The quantiles are null until there is something to take a quantile of — a
 * run that ended in four unsteered drops reports the counts and omits the
 * times, because `logEvent` drops null attributes and an absent attribute is
 * honest where a zero would be a measurement nobody made.
 */
internal data class DecisionSummary(
    val steerP50: Long?,
    val steerP90: Long?,
    val tapGapP50: Long?,
    val steeredDrops: Int,
    val unsteeredDrops: Int,
    val steps: Int,
)

/**
 * Fixed-width millisecond buckets, because a run's samples have to be
 * summarised without keeping them.
 *
 * A list of every measurement would be simplest and is the wrong shape here:
 * the debug menu's invincibility can run a board for thousands of drops, and
 * an unbounded list inside a ViewModel that also holds the board is how a
 * telemetry counter becomes an OOM. Sixty buckets is 240 bytes whatever the
 * run does.
 *
 * [BUCKET_MILLIS] at 50ms is well inside the resolution the question needs:
 * L41's sweep moved `decisionMillis` in 100ms steps and the answer it wants is
 * "is the real number nearer 150 or nearer 500". Anything past [CEILING] is a
 * player who put the phone down mid-drop, and it lands in the overflow bucket
 * rather than dragging a mean around.
 */
private class Histogram {
    private val counts = IntArray(BUCKETS)
    private var total = 0

    fun add(millis: Long) {
        if (millis < 0) return
        val bucket = (millis / BUCKET_MILLIS).toInt().coerceAtMost(BUCKETS - 1)
        counts[bucket]++
        total++
    }

    /**
     * The midpoint of the bucket the [fraction] quantile falls in, or null if
     * nothing was ever recorded. The overflow bucket reports [CEILING] rather
     * than a midpoint it has no upper bound for.
     */
    fun quantile(fraction: Double): Long? {
        if (total == 0) return null
        val target = (total * fraction).toInt().coerceAtMost(total - 1)
        var seen = 0
        counts.forEachIndexed { bucket, count ->
            seen += count
            if (seen > target) {
                return if (bucket == BUCKETS - 1) CEILING else bucket * BUCKET_MILLIS + BUCKET_MILLIS / 2
            }
        }
        return CEILING
    }

    private companion object {
        const val BUCKET_MILLIS = 50L
        const val CEILING = 3_000L
        const val BUCKETS = (CEILING / BUCKET_MILLIS).toInt() + 1
    }
}
