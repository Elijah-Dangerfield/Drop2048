package com.dangerfield.drop2048.features.debug

import kotlinx.coroutines.flow.StateFlow

/**
 * QA's answer to "what does this device own", for as long as the process lives.
 *
 * SPEC 19 wants grant Pro and reset IAP state, and C10 has not shipped billing,
 * so there is no store to ask and nothing to restore from. Rather than invent a
 * fake receipt store, this is one flag that the existing
 * [com.dangerfield.drop2048.features.settings.ProEntitlement] binding folds in —
 * so the Pro row, and everything gated on it, behaves for a tester exactly as it
 * will for a buyer. When C10 replaces that binding it should keep the fold, or
 * the menu quietly stops working on the release it matters most on.
 *
 * In memory only, like everything else the menu sets. A granted entitlement that
 * survived a relaunch is indistinguishable from a billing bug.
 *
 * **The Daily Challenge's second attempt is not wired to this**, and that is a
 * gap rather than a decision: SPEC 12's extra attempt is gated on a *different*
 * `ProEntitlement` — the `fun interface` in `:libraries:progress` — and a library
 * impl may not read a feature's api. The two seams want to be one type in one
 * library before C10 binds either of them.
 */
interface DebugEntitlements {

    val proGranted: StateFlow<Boolean>

    suspend fun setProGranted(granted: Boolean)

    /** Back to what a fresh install owns, which today is nothing. */
    suspend fun resetPurchases()
}
