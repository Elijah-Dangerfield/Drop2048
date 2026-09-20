package com.dangerfield.drop2048.libraries.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.components.AppBottomBar
import com.dangerfield.drop2048.libraries.ui.components.BottomBarItem
import com.dangerfield.drop2048.libraries.ui.components.dialog.DialogHost
import com.dangerfield.drop2048.libraries.ui.components.dialog.LocalDialogHostState
import com.dangerfield.drop2048.libraries.ui.components.dialog.rememberDialogHostState
import com.dangerfield.drop2048.libraries.ui.system.LocalAppState
import com.dangerfield.drop2048.libraries.ui.system.LocalBuildInfo
import com.dangerfield.drop2048.libraries.ui.system.LocalClock
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import com.dangerfield.drop2048.libraries.ui.system.color.ColorResource
import com.dangerfield.drop2048.libraries.core.AppState
import com.dangerfield.drop2048.libraries.core.BuildInfo
import com.dangerfield.drop2048.libraries.core.fixed
import com.dangerfield.drop2048.system.AppThemeProvider
import com.dangerfield.drop2048.system.background
import com.dangerfield.drop2048.system.color.defaultColors
import com.dangerfield.drop2048.system.thenIf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock
import kotlin.time.Instant

sealed class PreviewBottomBar(val render: @Composable () -> Unit) {

    object None: PreviewBottomBar({})

    object Home : PreviewBottomBar({
        AppBottomBar(
            items = listOf(
                BottomBarItem.Home(isSelected = true),
                BottomBarItem.Activity(isSelected = false),
                BottomBarItem.Profile(isSelected = false),
            ),
            onItemClick = {},
        )
    })

    object Activity : PreviewBottomBar({
        AppBottomBar(
            items = listOf(
                BottomBarItem.Home(isSelected = false),
                BottomBarItem.Activity(isSelected = true),
                BottomBarItem.Profile(isSelected = false),
            ),
            onItemClick = {},
        )
    })

    object Profile : PreviewBottomBar({
        AppBottomBar(
            items = listOf(
                BottomBarItem.Home(isSelected = false),
                BottomBarItem.Activity(isSelected = false),
                BottomBarItem.Profile(isSelected = true),
            ),
            onItemClick = {},
        )
    })
}

/**
 * A composable that is suitable as the root for any composable preview.
 *
 * It sets up the theme and some suitable defaults like a background colour.
 *
 * [palette], [reduceMotion] and [largeNumbers] default to the same values a
 * fresh install has, and exist so a preview or a screenshot golden can be taken
 * of a screen **under an accessibility setting** rather than only under the
 * defaults. That is the only way to prove those settings reach what is drawn,
 * which is what they had not been doing for six chunks.
 *
 * [reduceMotion] is the one with no player-facing switch behind it any more: in
 * the app it comes from the OS alone (see `isOsReduceMotionEnabled`), and this
 * parameter is how a preview reaches a state a preview has no OS to ask about.
 */
@Composable
fun PreviewContent(
    modifier: Modifier = Modifier,
    appState: AppState = PreviewAppState,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    backgroundColor: ColorResource? = defaultColors.background,
    bottomBar: PreviewBottomBar = PreviewBottomBar.None,
    palette: BlockPaletteChoice = BlockPaletteChoice.Default,
    reduceMotion: Boolean = false,
    largeNumbers: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dialogHostState = rememberDialogHostState()
    CompositionLocalProvider(
        LocalAppState provides appState,
        LocalClock provides Clock.fixed(Instant.parse("2023-01-01T00:00:00Z")),
        LocalBuildInfo provides BuildInfo,
        LocalDialogHostState provides dialogHostState
    ) {
        AppThemeProvider(
            palette = palette,
            reduceMotion = reduceMotion,
            largeNumbers = largeNumbers,
        ) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .thenIf(backgroundColor != null) { background(backgroundColor!!) }
                    .padding(contentPadding),
            ) {
                content()
                Box(Modifier.align(Alignment.BottomCenter)) {
                    bottomBar.render()
                }

                DialogHost(
                    modifier = Modifier.matchParentSize(),
                    hostState = dialogHostState
                )
            }
        }
    }
}


val PreviewAppState = object : AppState {
    override val isOffline: StateFlow<Boolean> = MutableStateFlow(false)

    override val isBlockActive: StateFlow<Boolean> = MutableStateFlow(false)
}