package com.dangerfield.drop2048.system

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.dangerfield.drop2048.libraries.ui.isOsReduceMotionEnabled
import com.dangerfield.drop2048.libraries.ui.system.HapticsSetting
import com.dangerfield.drop2048.libraries.ui.system.LocalColors
import com.dangerfield.drop2048.libraries.ui.system.LocalContentColor
import com.dangerfield.drop2048.libraries.ui.system.LocalCues
import com.dangerfield.drop2048.libraries.ui.system.LocalLargeNumbers
import com.dangerfield.drop2048.libraries.ui.system.LocalReduceMotion
import com.dangerfield.drop2048.libraries.ui.system.LocalTypography
import com.dangerfield.drop2048.libraries.ui.system.SoundPlayer
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPalette
import com.dangerfield.drop2048.libraries.ui.system.color.LocalBlockPalette
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPalettes
import com.dangerfield.drop2048.libraries.ui.system.rememberCues
import com.dangerfield.drop2048.system.color.Colors
import com.dangerfield.drop2048.system.color.defaultColors
import com.dangerfield.drop2048.system.typography.rememberTypography

object AppTheme {
    val colors: Colors
        @ReadOnlyComposable
        @Composable
        get() = LocalColors.current

    val typography
        @ReadOnlyComposable
        @Composable
        get() = LocalTypography.current

    /** The eleven block tiers, in whichever palette the player has chosen. */
    val blocks: BlockPalette
        @ReadOnlyComposable
        @Composable
        get() = LocalBlockPalette.current
}

/**
 * The theme, and every accessibility setting that changes how the game renders.
 *
 * Wide on purpose. This took no parameters in all three sibling repos, and that
 * is the structural reason Sodogku ends up providing `LocalReduceAnimations`
 * from `App.kt` and `LocalHaptics` from two separate feature entry points — once
 * the theme cannot carry a setting, every setting finds its own way down the
 * tree, and they stop agreeing about where they came from.
 *
 * So all four arrive here and are provided here. A component reads what it needs
 * from a CompositionLocal and takes no accessibility parameters of its own
 * (decision D4).
 *
 * Every argument has a default that is right for a preview, which is what keeps
 * `PreviewContent` a no-argument call.
 *
 * @param reduceMotion is **not** a player setting any more (owner ruling,
 *   2026-09-20). In the app it is always left at its default and the live value
 *   comes from [isOsReduceMotionEnabled] below, so the phone's own accessibility
 *   setting is the single source. It survives as a parameter because a preview
 *   and a screenshot golden have no OS setting to read and still have to be able
 *   to render the reduced state. Passing `true` from production code would put a
 *   second source back.
 */
@Composable
fun AppThemeProvider(
    palette: BlockPaletteChoice = BlockPaletteChoice.Default,
    reduceMotion: Boolean = false,
    largeNumbers: Boolean = false,
    haptics: HapticsSetting = HapticsSetting.Off,
    sounds: SoundPlayer = SoundPlayer.Silent,
    content: @Composable () -> Unit
) {

    val colors = defaultColors

    val textSelectionColors = TextSelectionColors(
        handleColor = colors.accentPrimary.color,
        backgroundColor = colors.accentPrimary.color.copy(alpha = 0.4F)
    )

    val typography = rememberTypography()
    val cues = rememberCues(setting = haptics, sounds = sounds)
    val motionReduced = reduceMotion || isOsReduceMotionEnabled()

    MaterialWrapper {
        CompositionLocalProvider(
            LocalContentColor provides colors.text,
            LocalTextSelectionColors provides textSelectionColors,
            LocalTypography provides typography,
            androidx.compose.material3.LocalContentColor provides colors.text.color,
            LocalColors provides colors,
            LocalBlockPalette provides BlockPalettes[palette],
            LocalReduceMotion provides motionReduced,
            LocalLargeNumbers provides largeNumbers,
            LocalCues provides cues,
            content = content
        )
    }
}

@Composable
private fun MaterialWrapper(content: @Composable () -> Unit) {
    val invalidTextStyle = TextStyle(color = Color.Red)
    MaterialTheme(
        typography = Typography(
            displayLarge = invalidTextStyle,
            displayMedium = invalidTextStyle,
            displaySmall = invalidTextStyle,
            headlineLarge = invalidTextStyle,
            headlineMedium = invalidTextStyle,
            headlineSmall = invalidTextStyle,
            titleLarge = invalidTextStyle,
            titleMedium = invalidTextStyle,
            titleSmall = invalidTextStyle,
            bodyLarge = invalidTextStyle,
            bodyMedium = invalidTextStyle,
            bodySmall = invalidTextStyle,
            labelLarge = invalidTextStyle,
            labelMedium = invalidTextStyle,
            labelSmall = invalidTextStyle
        ),
        colorScheme = ColorScheme(
            primary = Color.Red,
            onPrimary = Color.Red,
            primaryContainer = Color.Red,
            onPrimaryContainer = Color.Red,
            inversePrimary = Color.Red,
            secondary = Color.Red,
            onSecondary = Color.Red,
            secondaryContainer = Color.Red,
            onSecondaryContainer = Color.Red,
            tertiary = Color.Red,
            onTertiary = Color.Red,
            tertiaryContainer = Color.Red,
            onTertiaryContainer = Color.Red,
            background = Color.Red,
            onBackground = Color.Red,
            surface = Color.Red,
            onSurface = Color.Red,
            surfaceVariant = Color.Red,
            onSurfaceVariant = Color.Red,
            surfaceTint = Color.Red,
            inverseSurface = Color.Red,
            inverseOnSurface = Color.Red,
            error = Color.Red,
            onError = Color.Red,
            errorContainer = Color.Red,
            onErrorContainer = Color.Red,
            outline = Color.Red,
            outlineVariant = Color.Red,
            scrim = Color.Red
        ),
        content = content
    )
}
