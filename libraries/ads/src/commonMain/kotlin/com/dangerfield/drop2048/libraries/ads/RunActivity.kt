package com.dangerfield.drop2048.libraries.ads

import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Whether a run is alive, asked by the ad layer and answered by the game.
 *
 * ### Why this is not a parameter on `InterstitialGate.showIfReady`
 *
 * It could be, and the gate would be one type smaller. The difference is who the
 * guarantee belongs to. SPEC 12's governing principle — *the player never sees
 * an ad they did not choose while a run is alive* — is the one rule the spec
 * refuses to negotiate, and a boolean argument makes it the caller's rule: it
 * holds exactly as long as every present and future call site passes the right
 * value, and it fails silently the first time one does not.
 *
 * Asked of the app instead, it is the ad layer's rule. A call site that gets the
 * moment wrong is refused by the gate rather than obeyed by it, and
 * `AdPolicyTest` can hold the principle directly instead of holding a convention
 * about arguments.
 *
 * App-scoped rather than owned by the game's ViewModel because the ViewModel dies
 * with its screen and the ad layer outlives it.
 */
interface RunActivity {
    val isRunAlive: Boolean

    fun runStarted()

    /**
     * The engine ended the run. Distinct from the player dismissing the results:
     * SPEC 12 puts the interstitial after the *dismissal*, and between the two
     * there is a sheet, a score counting up, possibly a rewarded continue, and
     * however long the player wants to look at it.
     */
    fun runEnded()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class InMemoryRunActivity : RunActivity {
    private var alive: Boolean = false

    override val isRunAlive: Boolean get() = alive

    override fun runStarted() {
        alive = true
    }

    override fun runEnded() {
        alive = false
    }
}
