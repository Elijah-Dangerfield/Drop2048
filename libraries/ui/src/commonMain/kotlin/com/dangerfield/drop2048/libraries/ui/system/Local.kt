package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.dangerfield.drop2048.libraries.ui.system.color.ColorResource
import com.dangerfield.drop2048.libraries.core.AppState
import com.dangerfield.drop2048.libraries.core.BuildInfo
import com.dangerfield.drop2048.system.color.Colors
import com.dangerfield.drop2048.system.typography.Typography
import kotlin.time.Clock

val LocalColors = compositionLocalOf<Colors> {
    error("Theme wasn't applied")
}

val LocalContentColor = compositionLocalOf<ColorResource> {
    error("Theme wasn't applied")
}

val LocalTypography = compositionLocalOf<Typography> {
    error("Theme wasn't applied")
}

val LocalBuildInfo = staticCompositionLocalOf<BuildInfo> {
    error("No LocalBuildInfo provided")
}

val LocalAppState = staticCompositionLocalOf<AppState> {
    error("No LocalAppState provided")
}

val LocalClock = staticCompositionLocalOf<Clock> {
    error("No LocalClock provided")
}

/**
 * True when animation should be shortened and shake and particles dropped.
 *
 * The in-app accessibility toggle ORed with the OS setting, resolved once in
 * `AppThemeProvider`. Components read it here and nowhere else (decision D4):
 * threading it as a parameter is how Sodogku ended up with three incompatible
 * idioms for one setting in one repo, one of which turned out to be a battery
 * setting wearing an accessibility setting's name.
 *
 * Defaults to `false` rather than erroring, so a preview animates.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * True when the player has asked for larger numbers on block faces (SPEC 16).
 *
 * A scale applied by the block face rather than a font size, so it composes with
 * whatever size the cell happens to be at this board dimension.
 */
val LocalLargeNumbers = staticCompositionLocalOf { false }