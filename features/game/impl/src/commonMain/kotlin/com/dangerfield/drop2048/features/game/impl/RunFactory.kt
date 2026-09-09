package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.GameState
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
    fun newRun(): GameState
}

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
    override fun newRun(): GameState =
        Cascade.newGame(seed = Random.nextLong(), config = EngineConfig.Default)
}
