package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.debug.DebugController
import com.dangerfield.drop2048.features.debug.applyTo
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.gameconfig.RemoteEngineConfig
import kotlin.random.Random
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * Where a run comes from.
 *
 * A seam rather than a `Cascade.newGame(Random.nextLong())` inlined in the
 * ViewModel, because later chunks need to hand the same screen a different
 * starting state and none of them should have to fork it: C5's tutorial is a
 * list of forced `GameState`s (SPEC 13), and C12's debug menu replays a recorded
 * one. A scenario test is the third, and it is the one that keeps the seam
 * honest today.
 */
interface RunFactory {

    /** A fresh run on a seed nobody has seen (SPEC 2). */
    fun newRun(): StartedRun
}

/**
 * A fresh run, and the two things about it that the [GameState] cannot be asked
 * for afterwards.
 *
 * The RNG carried inside the state has already advanced past [seed] by the time
 * the first block is drawn, so a run that does not record its seed at the start
 * can never record it at all — and `run_record` keeps it precisely so a
 * surprising score can be replayed (SPEC 11, SPEC 4.1).
 */
data class StartedRun(
    val state: GameState,
    val seed: Long,
    /**
     * Whether this run was started in a session that has opened the debug menu
     * (SPEC 19).
     *
     * True means the run writes no `run_record` and posts no leaderboard score.
     * It is a property of the run rather than a
     * question asked at the end so that a menu opened *mid-run* cannot
     * retroactively delete a legitimate run's record — the flag is read once,
     * here, and travels with the run it describes.
     */
    val debug: Boolean = false,
)

/**
 * A fresh seed, on today's numbers.
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
 * There was a second factory method, `dailyRun`, which pinned
 * [EngineConfig.Default] against remote config so two players on the same day's
 * seed got the same board (the old D18). D27 deleted the mode, and the pin with
 * it — nothing in the game now needs a run that ignores the config the rest of
 * them read.
 */
@ContributesBinding(AppScope::class)
@Inject
class RealRunFactory(
    private val engineConfig: RemoteEngineConfig,
    private val debug: DebugController,
) : RunFactory {

    override fun newRun(): StartedRun {
        val overrides = debug.overrides.value
        val seed = overrides.seed ?: Random.nextLong()
        val fresh = Cascade.newGame(seed = seed, config = engineConfig.current())
        return StartedRun(
            state = overrides.applyTo(fresh, forcedBlock = debug.takeForcedBlock()),
            seed = seed,
            debug = debug.isDebugSession.value,
        )
    }
}
