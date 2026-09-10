package com.dangerfield.drop2048.libraries.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dangerfield.drop2048.libraries.ui.Elevation
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonGhost
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonSize
import com.dangerfield.drop2048.libraries.ui.components.icon.Icon
import com.dangerfield.drop2048.libraries.ui.components.icon.IconButton
import com.dangerfield.drop2048.libraries.ui.components.icon.IconResource
import com.dangerfield.drop2048.libraries.ui.components.icon.Icons
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.HorizontalSpacerD400
import com.dangerfield.drop2048.system.Radii
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * A card that floats over whatever the player was doing and says one thing.
 *
 * The design system's answer to "tell them, don't stop them" — the dismissible
 * half of every launch gate, and the shape a maintenance notice, a soft-update
 * suggestion and a terms change all take. A card rather than a full-bleed strip:
 * a bar across the top reads as chrome the app always had, and this has to read
 * as something that just arrived.
 *
 * It carries its own status-bar inset, because the thing underneath it is a
 * `Screen` that has already consumed its own — a banner overlaid on top would
 * otherwise sit under the clock.
 *
 * **[onDismiss] is not optional by accident.** A notice with no way to close it
 * is a block wearing a banner's clothes, and the whole point of the split is that
 * one of them can be waved away. A message that genuinely must not be dismissed
 * belongs on a blocking screen.
 */
@Composable
fun NoticeBanner(
    text: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: IconResource? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        // Not `Card`: its inset is sized for a page section, and a banner that
        // tall over a live board hides the thing the player is looking at.
        color = AppTheme.colors.surfacePrimary,
        contentColor = AppTheme.colors.onSurfacePrimary,
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = Dimension.D500, vertical = Dimension.D400)
            .fillMaxWidth(),
        radius = Radii.Card,
        elevation = Elevation.Button,
        bounceScale = 1f,
        onClick = {},
        contentPadding = PaddingValues(
            start = Dimension.D700,
            top = Dimension.D500,
            bottom = Dimension.D500,
        ),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (icon != null) {
                Icon(icon = icon, color = AppTheme.colors.textSecondary)
                HorizontalSpacerD400()
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.text,
                )

                if (actionLabel != null && onAction != null) {
                    ButtonGhost(
                        onClick = onAction,
                        size = ButtonSize.Small,
                        modifier = Modifier.offset(x = -Dimension.D500),
                    ) {
                        Text(text = actionLabel)
                    }
                }
            }

            IconButton(
                icon = Icons.Close(dismissLabel),
                onClick = onDismiss,
                iconColor = AppTheme.colors.textSecondary,
                size = IconButton.Size.Smallest,
            )
        }
    }
}

@Preview
@Composable
private fun NoticeBannerPreview() {
    PreviewContent {
        NoticeBanner(
            text = "A newer version of Drop 2048 is out.",
            dismissLabel = "Dismiss",
            onDismiss = {},
            icon = Icons.Info("Notice"),
            actionLabel = "Update",
            onAction = {},
        )
    }
}
