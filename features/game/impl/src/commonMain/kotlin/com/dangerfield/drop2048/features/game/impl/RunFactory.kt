package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.GameState
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
    fun newRun(): StartedRun
}

/**
 * A fresh run, and the two things about it that the [GameState] cannot be asked
 * for afterwards.
 *
 * The RNG carried inside the state has already advanced past [seed] by the time
 * the first block is drawn, so a run that does not record its seed at the start
 * can never record it at all — and `run_record` keeps it precisely so a
 * surprising score can be replayed (SPEC 11, SPEC 4.1). [mode] is here for the
 * same reason: it is a property of how the run was *started*, and C6's Daily
 * will start one from the same seam.
 */
data class StartedRun(
    val state: GameState,
    val seed: Long,
    val mode: GameMode,
)

/**
 * Endless mode: a fresh seed every time, on the compiled-in defaults.
 *
 * The config is [EngineConfig.Default] rather than a remote value because
 * remote config is C7. When it lands, this is the one place that reads it, and
 * SPEC 10's rule still holds either way: the binary is fully playable with the
 * server unreachable.
 *
 * That default is **eight rows**, settled on device in C3. See SPEC 3.
 */
@ContributesBinding(AppScope::class)
@Inject
class EndlessRunFactory : RunFactory {
    override fun newRun(): StartedRun {
        val seed = Random.nextLong()
        return StartedRun(
            state = Cascade.newGame(seed = seed, config = EngineConfig.Default),
            seed = seed,
            mode = GameMode.ENDLESS,
        )
    }
}
