package com.dangerfield.drop2048.libraries.navigation

import kotlin.test.Test

/**
 * [assertSheetOwnsItsDeepLink] and friends, run on Kotlin/Native.
 *
 * This is the run that matters. The graph-build landmines this module exists to
 * guard against are Native-only, and a deep link is the one way into a
 * destination that the app itself never exercises during a normal launch, so
 * nothing else would notice it going missing.
 *
 * The Android half of the same three checks is
 * `DeepLinkRegistrationRobolectricTest`.
 */
class DeepLinkRegistrationNativeTest {

    @Test
    fun aScreenKeepsTheDeepLinkItWasRegisteredWith() = assertScreenOwnsItsDeepLink()

    @Test
    fun aDialogKeepsTheDeepLinkItWasRegisteredWith() = assertDialogOwnsItsDeepLink()

    @Test
    fun aSheetKeepsTheDeepLinkItWasRegisteredWith() = assertSheetOwnsItsDeepLink()
}
