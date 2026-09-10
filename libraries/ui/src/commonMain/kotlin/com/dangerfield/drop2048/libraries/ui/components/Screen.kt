package com.dangerfield.drop2048.libraries.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.libraries.ui.system.LocalContentColor
import com.dangerfield.drop2048.libraries.ui.system.color.ColorResource
import com.dangerfield.drop2048.libraries.ui.components.text.LocalTextConfig

/**
 * Every full screen in the app, on the handoff's radial backdrop.
 *
 * The backdrop is drawn here and the scaffold sits on it transparently, so a
 * screen made of lists and cards falls away from the same point above the top
 * edge that the board does. It is what stops "the settings screen" and "the game"
 * from reading as two apps even once they share a palette — a flat fill at
 * `#141021` beside a gradient is still a seam.
 *
 * Pass [containerColor] to opt out; anything other than transparent paints over
 * the gradient.
 *
 * [contentColor] comes from the theme rather than from Material's
 * `contentColorFor`. The template stubs every `ColorScheme` slot to red on
 * purpose, so `contentColorFor` matches nothing and silently returns
 * `Color.Unspecified` — which every text then had to fall back out of.
 */
@Composable
fun Screen(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = Color.Transparent,
    contentColor: Color = AppTheme.colors.onBackground.color,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {

    val contentColorResource = ColorResource.FromColor(contentColor, "")

    CompositionLocalProvider(
        LocalContentColor provides contentColorResource,
        androidx.compose.material3.LocalContentColor provides contentColorResource.color,
        LocalTextConfig provides LocalTextConfig.current.copy(color = contentColorResource)
    ) {
        Scaffold(
            modifier = modifier.appBackdrop(),
            topBar = topBar,
            bottomBar = bottomBar,
            snackbarHost = snackbarHost,
            containerColor = containerColor,
            contentColor = contentColor,
            contentWindowInsets = contentWindowInsets
        ) { paddingValues ->
            Box(
                Modifier.fillMaxSize(),
                propagateMinConstraints = true) {
                content(paddingValues)
            }
        }
    }
}
