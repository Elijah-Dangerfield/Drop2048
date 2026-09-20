package com.dangerfield.drop2048.libraries.navigation

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [assertSheetOwnsItsDeepLink] and friends, run on the JVM.
 *
 * Robolectric is here for one class: `NavUri` is `android.net.Uri` on the
 * Android target, and a plain JVM unit test gets the stub that throws
 * "Method parse in android.net.Uri not mocked". Nothing here renders.
 *
 * Worth the dependency because this is the run that lands in
 * `testDebugUnitTest`, which is the gate everyone runs. The Native half is
 * `DeepLinkRegistrationNativeTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ROBOLECTRIC_SDK])
class DeepLinkRegistrationRobolectricTest {

    @Test
    fun aScreenKeepsTheDeepLinkItWasRegisteredWith() = assertScreenOwnsItsDeepLink()

    @Test
    fun aDialogKeepsTheDeepLinkItWasRegisteredWith() = assertDialogOwnsItsDeepLink()

    @Test
    fun aSheetKeepsTheDeepLinkItWasRegisteredWith() = assertSheetOwnsItsDeepLink()
}

private const val ROBOLECTRIC_SDK = 34
