package com.dangerfield.drop2048.libraries.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The one answer to "does this device own Pro", and the two controls that change
 * it.
 *
 * ### Why this type is in a library and not in a feature
 *
 * Until C10 there were **two** of these. `:features:settings` owned a
 * `StateFlow`-shaped `ProEntitlement` for the settings row, and
 * `:libraries:progress` owned a synchronous `fun interface` of the same name for
 * SPEC 14's second Daily attempt. They were never the same object, so the debug
 * menu's "grant Pro" — folded into the settings one — could not reach the Daily
 * at all, and a library impl may not read a feature's api to fix it. Two types
 * for one fact meant one of them was always going to be wrong, and it was
 * silently wrong: granting Pro looked like it worked everywhere except the one
 * place a tester would not think to check.
 *
 * Both are now this. It lives in a leaf library so a feature *and* a library impl
 * can both depend on it, which is the shape the problem always wanted.
 *
 * ### The Daily reads `isPro.value`, deliberately
 *
 * The old `fun interface` was synchronous on purpose, and the reason survives:
 * the question is asked once, at the moment an attempt is requested, and an
 * entitlement that changed mid-run must not retroactively change how many
 * attempts the day had. A `StateFlow` read at that instant is the same answer;
 * what it also buys is a settings row that updates without polling.
 */
interface Entitlements {
    val isPro: StateFlow<Boolean>

    /**
     * [trigger] is the [PaywallTrigger.id] that opened the sheet, carried into
     * `iap.purchase_result` so "which moment converts" is one query rather than
     * a join. Optional because the QA path and the test doubles have no moment.
     */
    suspend fun purchasePro(trigger: String? = null): PurchaseOutcome

    /**
     * The explicit restore Apple requires a visible control for.
     *
     * Returns an outcome rather than a `Boolean` because every branch has to say
     * something. A restore that silently does nothing is the most common reason
     * this control gets reported as broken: the player cannot tell "you never
     * bought it" from "we could not ask the store".
     */
    suspend fun restore(): RestoreOutcome
}

/**
 * How a purchase attempt ended.
 *
 * [name] is a literal rather than `::class.simpleName` because R8 renames these
 * classes in a Play build, and a dashboard filtering on `Success` would read zero
 * while the Play Console showed sales. Keep these spellings and the dashboard's
 * in step.
 */
sealed interface PurchaseOutcome {
    val name: String

    data object Success : PurchaseOutcome {
        override val name = "Success"
    }

    data object Cancelled : PurchaseOutcome {
        override val name = "Cancelled"
    }

    data object AlreadyOwned : PurchaseOutcome {
        override val name = "AlreadyOwned"
    }

    /** No store on this device, or no store wired up at all. */
    data object Unavailable : PurchaseOutcome {
        override val name = "Unavailable"
    }

    data class Failed(val kind: String) : PurchaseOutcome {
        override val name = "Failed"
    }
}

enum class RestoreOutcome {
    Restored,
    NothingToRestore,

    /** The store could not be reached, or there is no store wired up yet. */
    Unavailable,
}

/**
 * QA's "grant Pro" switch (SPEC 19), as a type a library can read.
 *
 * The flag used to live in `:features:debug` and be folded in by a binding in
 * `:features:settings:impl`, which is why the Daily never saw it. It is here so
 * the fold happens once, in [RealEntitlements][com.dangerfield.drop2048.libraries.billing.impl.RealEntitlements],
 * above everything that asks. `:features:debug` drives it from the other side;
 * a feature depending on a library is the direction that is allowed.
 *
 * In memory only, like everything else the debug menu sets. A granted
 * entitlement that survived a relaunch is indistinguishable from a billing bug.
 */
interface ProGrant {
    val granted: StateFlow<Boolean>

    suspend fun setGranted(granted: Boolean)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class InMemoryProGrant : ProGrant {
    private val state = MutableStateFlow(false)

    override val granted: StateFlow<Boolean> = state.asStateFlow()

    override suspend fun setGranted(granted: Boolean) {
        state.value = granted
    }
}

/**
 * What Pro does until a store is reachable: nobody owns it, and nothing can be
 * bought.
 *
 * It lives in this api module rather than an `impl` so
 * [RealEntitlements][com.dangerfield.drop2048.libraries.billing.impl.RealEntitlements]
 * can replace it with `replaces = [FreeEntitlements::class]` without a module
 * depending on an impl.
 *
 * It still folds [ProGrant] in, which matters more than it looks: the debug menu
 * is the only way to exercise every Pro path on a build with no store account,
 * and a default binding that ignored the grant would make the QA switch work on
 * exactly the builds where the real one already does.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class FreeEntitlements(
    proGrant: ProGrant,
) : Entitlements {
    override val isPro: StateFlow<Boolean> = proGrant.granted

    override suspend fun purchasePro(trigger: String?): PurchaseOutcome = PurchaseOutcome.Unavailable

    override suspend fun restore(): RestoreOutcome = RestoreOutcome.Unavailable
}
