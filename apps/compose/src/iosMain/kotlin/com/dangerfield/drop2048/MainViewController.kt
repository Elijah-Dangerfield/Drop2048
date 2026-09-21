package com.dangerfield.drop2048

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

fun MainViewController(
    appComponent: IosAppComponent,
): UIViewController = ComposeUIViewController {
    App(appComponent = appComponent)
}
