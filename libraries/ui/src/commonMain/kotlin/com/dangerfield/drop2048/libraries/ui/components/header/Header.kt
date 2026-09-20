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
import com.dangerfield.drop2048.libraries.ui.components.appBackdropTopSlice
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
    /**
     * Null draws the screen's own radial backdrop, aligned so the bar is a slice
     * of it rather than a band across it.
     *
     * The bar cannot simply be transparent: every list screen applies the
     * scaffold's top padding *inside* its scroll container, so its rows travel
     * underneath and a see-through bar had section headers riding up over the
     * title. And it cannot be a flat fill either — `colors.background` against
     * the gradient is the seam that made a settings screen look like a different
     * app from the board. See [appBackdropTopSlice].
     */
    backgroundColor: Color? = null,
    actions: @Composable () -> Unit = {},
    scrollState: ScrollState? = null,
    liftOnScroll: Boolean = scrollState != null,
) {
    val surface = if (backgroundColor == null) {
        Modifier.appBackdropTopSlice()
    } else {
        Modifier.background(backgroundColor)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .thenIf(liftOnScroll) { elevateOnScroll(scrollState) }
            .then(surface)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                )
            ),
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

/**
 * The bar's lift, as a shadow cast by the bar.
 *
 * **Where this goes in the chain is the whole component.** A `graphicsLayer`
 * only contains what comes *after* it, so applied below the bar's background and
 * its inset padding it wrapped the title row and nothing else: the surface was
 * drawn outside the layer and could not occlude the shadow, and the layer's
 * bounds were the padded content box rather than the bar, so the lift rendered
 * as a hard rectangle around the title *on top of* the header instead of under
 * the header onto the scrolling content. It looked exactly like a drop shadow on
 * the text, which is how it was reported. It has to sit above both the surface
 * and the inset padding so the layer is the bar.
 *
 * `@Composable` rather than `composed`, and `graphicsLayer` rather than
 * `Modifier.shadow`. The two go together: dropping `composed` moves this body
 * into [TopBar]'s own composition, so the animated elevation had to stop being
 * read there, or every lift would recompose the whole header at 60fps.
 * `shadow()` takes its elevation as a plain argument and has no lambda form;
 * `graphicsLayer` does, and `shadow()` is itself only a graphicsLayer setting
 * shadowElevation, shape and clip. Reading `.value` in the lambda keeps the lift
 * a draw-phase invalidation, so the suppression this used to carry is gone.
 *
 * `clip` mirrors what `shadow()` does — it defaults to `elevation > 0.dp`, not
 * to false — so the lift clips to bounds exactly as it did before. Shape stays
 * the graphicsLayer default, which is the `RectangleShape` `shadow()` also
 * defaults to.
 */
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

