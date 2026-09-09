package com.dangerfield.drop2048.libraries.ui.components.header

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.thenIf
import com.dangerfield.drop2048.system.typography.TypographyResource
import com.dangerfield.drop2048.libraries.ui.Elevation
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.icon.IconButton
import com.dangerfield.drop2048.libraries.ui.components.icon.Icons
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun TopBar(
    title: String? = null,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    typographyToken: TypographyResource = AppTheme.typography.Display.D900,
    backgroundColor: Color = AppTheme.colors.background.color,
    actions: @Composable () -> Unit = {},
    scrollState: ScrollState? = null,
    liftOnScroll: Boolean = scrollState != null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            )
            .thenIf(liftOnScroll) { elevateOnScroll(scrollState) }
            ,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (onNavigateBack != null) {
                IconButton(
                    size = IconButton.Size.Large,
                    icon = Icons.ChevronLeft("Navigate back"),
                    onClick = onNavigateBack
                )
            }
            title?.let {
                Text(text = title, typography = typographyToken)
            }
        }
        
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions()
        }
    }
}

// `@Composable` rather than `composed`, and `graphicsLayer` rather than
// `Modifier.shadow`. The two go together: dropping `composed` moves this body
// into TopBar's own composition, so the animated elevation had to stop being
// read there, or every lift would recompose the whole header at 60fps.
// `shadow()` takes its elevation as a plain argument and has no lambda form;
// `graphicsLayer` does, and `shadow()` is itself only a graphicsLayer setting
// shadowElevation, shape and clip. Reading `.value` in the lambda keeps the lift
// a draw-phase invalidation, so the suppression this used to carry is gone.
@Composable
private fun Modifier.elevateOnScroll(
    scrollState: ScrollState?,
): Modifier {

    checkNotNull(scrollState) {
        "ScrollState should not be null when liftOnScroll is true"
    }

    val elevation = animateDpAsState(
        if (scrollState.canScrollBackward) {
            Elevation.Header.dp
        } else {
            0.dp
        }, label = ""
    )

    // `clip` mirrors what `shadow()` does — it defaults to `elevation > 0.dp`,
    // not to false — so the lift clips to bounds exactly as it did before.
    // Shape stays the graphicsLayer default, which is the RectangleShape
    // `shadow()` also defaults to.
    return this.graphicsLayer {
        shadowElevation = elevation.value.toPx()
        clip = shadowElevation > 0f
    }
}

@Preview
@Composable
private fun PreviewHeader() {
    PreviewContent {
        com.dangerfield.drop2048.libraries.ui.components.header.TopBar(
            title = "Heading Title",
        )
    }
}

