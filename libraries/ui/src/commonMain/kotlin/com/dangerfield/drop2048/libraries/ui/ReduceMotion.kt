package com.dangerfield.drop2048.libraries.ui

import androidx.compose.runtime.Composable

/**
 * Reads the platform "reduce motion" accessibility setting.
 *
 * Returns `true` when the player has asked the OS to minimise non-essential
 * animation (iOS Settings → Accessibility → Motion → Reduce Motion; Android
 * Settings → Accessibility → Remove animations, or a system animation scale of
 * zero).
 *
 * This is one half of what [com.dangerfield.drop2048.libraries.ui.system.LocalReduceMotion]
 * carries — the in-app toggle is the other, and they are ORed. A player who has
 * already told the phone once should not have to tell the game again, and a
 * player who wants calm from this game only should not have to change a system
 * setting to get it.
 *
 * Composable so it observes the live setting where the platform exposes a
 * reactive source; desktop has no such signal and answers `false`.
 */
@Composable
expect fun isOsReduceMotionEnabled(): Boolean
