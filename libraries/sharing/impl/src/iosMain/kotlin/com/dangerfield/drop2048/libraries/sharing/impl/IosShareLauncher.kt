package com.dangerfield.drop2048.libraries.sharing.impl

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.sharing.ShareLauncher
import me.tatarka.inject.annotations.Inject
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.popoverPresentationController
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The system activity sheet.
 *
 * Presented from the *topmost* view controller rather than from the window's
 * root. The share is offered from the stacked-out sheet, which on iOS is Compose
 * content inside the root controller — but anything the app later presents
 * modally (a rewarded ad, a review prompt) sits above it, and presenting on a
 * controller that is already covered silently does nothing.
 *
 * `popoverPresentationController` is a category on `UIViewController`, so
 * Kotlin/Native surfaces it as an extension property that has to be imported by
 * name rather than found on the type. iPad presents the sheet as a popover and
 * needs an anchor; without a source view it raises rather than falling back.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosShareLauncher @Inject constructor() : ShareLauncher {

    override fun share(text: String) {
        Catching {
            val host = requireNotNull(topViewController()) {
                "No view controller to present the share sheet from"
            }
            val controller = UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null,
            )
            controller.popoverPresentationController?.sourceView = host.view
            host.presentViewController(controller, animated = true, completion = null)
        }.logOnFailure { "Failed to present the share sheet" }
    }

    private fun topViewController(): UIViewController? {
        val windows = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>()
        var controller = (windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull())
            ?.rootViewController
            ?: return null
        while (true) {
            controller = controller.presentedViewController ?: return controller
        }
    }
}
