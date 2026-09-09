package com.dangerfield.drop2048.libraries.ui

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

/**
 * iOS exposes the setting directly. Read at composition time; the rare
 * mid-session flip (a player backgrounding the game to change it) is picked up
 * on the next recomposition, which is soon enough for a setting that changes the
 * length of an animation rather than the state of the run.
 */
@Composable
actual fun isOsReduceMotionEnabled(): Boolean = UIAccessibilityIsReduceMotionEnabled()
