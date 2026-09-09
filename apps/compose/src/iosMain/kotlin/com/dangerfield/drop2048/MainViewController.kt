package com.dangerfield.drop2048

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import com.dangerfield.drop2048.libraries.ui.nativeviews.LocalNativeViewFactory
import com.dangerfield.drop2048.libraries.ui.nativeviews.NativeViewFactory
import platform.UIKit.UIViewController

fun MainViewController(
    appComponent: IosAppComponent,
): UIViewController {
    return ComposeUIViewController {
        CompositionLocalProvider(LocalNativeViewFactory provides appComponent.nativeViewFactory) {
            App(appComponent = appComponent)
        }
    }
}
