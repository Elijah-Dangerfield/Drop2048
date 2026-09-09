package com.dangerfield.drop2048.libraries.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Android maps "Remove animations" and the developer-options animation scales
 * onto [Settings.Global.TRANSITION_ANIMATION_SCALE]; zero means the user wants
 * motion suppressed. There is no dedicated flag — reading the global scale is
 * the platform-standard reduce-motion probe.
 */
@Composable
actual fun isOsReduceMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    return scale == 0f
}
