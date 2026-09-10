package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.gameconfig.RemoteEngineConfig
import com.dangerfield.drop2048.libraries.progress.GameMode
import kotlin.random.Random
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * Where a run comes from.
 *
 * A seam rather than a `Cascade.newGame(Random.nextLong())` inlined in the
 * ViewModel, because three later chunks need to hand the same screen a
 * different starting state and none of them should have to fork it: C5's
 * tutorial is a list of forced `GameState`s (SPEC 13), C6's Daily Challenge is
 * one shared seed (SPEC 14), and C12's debug menu replays a recorded one.
 * A scenario test is the fourth, and it is the one that keeps the seam honest
 * today.
 */
interface RunFactory {

    /** A fresh Endless run on a seed nobody has seen (SPEC 2). */
    fun newRun(): StartedRun

    /**
     * The Daily Challenge for [seed] (SPEC 14).
     *
     * The seed is handed in rather than derived here because spending the day's
     * attempt and choosing the day's board are one decision, and it is
     * `DailyRepository`'s. A factory that computed the date itself could hand out
     * a board for a day the ledger has not opened.
     */
    fun dailyRun(seed: Long): StartedRun
}

/**
 * A fresh run, and the two things about it that the [GameState] cannot be asked
 * for afterwards.
 *
 * The RNG carried inside the state has already advanced past [seed] by the time
 * the first block is drawn, so a run that does not record its seed at the start
 * can never record it at all — and `run_record` keeps it precisely so a
 * surprising score can be replayed (SPEC 11, SPEC 4.1). [mode] is here for the
 * same reason: it is a property of how the run was *started*.
 */
data class StartedRun(
    val state: GameState,
    val seed: Long,
    val mode: GameMode,
)

/**
 * Both modes, and the one difference between them that matters.
 *
 * ### Endless: a fresh seed, on today's numbers
 *
 * **[RemoteEngineConfig.current] is called here and nowhere else, and that is the
 * mechanism by which a fetched config takes effect at the start of the next run
 * and never mid-run.** The value goes into the [GameState] (D5) and every in-run
 * read — the drop interval, the level bar's denominator, the nudge distance —
 * goes through `state.config` rather than back to the config map. A refresh that
 * lands while a run is alive changes nothing until the player starts another one.
 *
 * SPEC 10's rule holds unchanged: every key falls back to a compiled-in default,
 * so with the server unreachable this returns exactly [EngineConfig.Default] and
 * the binary is fully playable. That default is **eight rows**, settled on device
 * in C3. See SPEC 3.
 *
 * ### Daily: the day's seed, on numbers no server can move
 *
 * [dailyRun] does **not** read [RemoteEngineConfig]. It pins
 * [EngineConfig.Default] — the numbers compiled into this binary — and that is
 * the single most consequential decision in C6.
 *
 * The mode's whole promise is that everyone played the same game. C7 ruled that a
 * fetched config is sampled once at the start of a run and never changes under
 * one, which is enough to keep a *single* run coherent and is not enough here:
 * two players starting the same day's seed ten minutes apart, one before a config
 * push and one after, would each get an internally coherent run and a different
 * board. The seed would match, the scores would not be comparable, and nothing
 * anywhere would say so. That is the expensive kind of wrong — the kind that
 * looks right.
 *
 * The cost is real and is accepted: the Daily plays on whatever balance the
 * binary shipped with, so it does not benefit from a live spawn-table fix until
 * the next release. That is the correct trade. A remote push is invisible,
 * instant and per-device-segment; a release is versioned, staged and something
 * the owner decides to do. Comparability wants the boundary at the thing you can
 * see.
 *
 * **What this makes immovable.** From the first recorded Daily score,
 * [EngineConfig.Default] is a leaderboard-visible constant. Moving any field of
 * it in a release splits that day's board between app versions exactly the way a
 * remote push would have — the difference is only that a release is visible. The
 * pinned determinism digest and `level.blocksPerLevel` are in the same position
 * for the same reason. See `docs/decisions.md`.
 *
 * `DailyConfigPinningTest` is the enforcement: it moves every remote key it can
 * and asserts the Daily's block sequence does not budge.
 */
@ContributesBinding(AppScope::class)
@Inject
class RealRunFactory(
    private val engineConfig: RemoteEngineConfig,
) : RunFactory {

    override fun newRun(): StartedRun {
        val seed = Random.nextLong()
        return StartedRun(
            state = Cascade.newGame(seed = seed, config = engineConfig.current()),
            seed = seed,
            mode = GameMode.ENDLESS,
        )
    }

    override fun dailyRun(seed: Long): StartedRun = StartedRun(
        state = Cascade.newGame(seed = seed, config = EngineConfig.Default),
        seed = seed,
        mode = GameMode.DAILY,
    )
}
