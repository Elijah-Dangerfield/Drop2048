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
 * **This is the whole of what [com.dangerfield.drop2048.libraries.ui.system.LocalReduceMotion]
 * carries in the app** (owner ruling, 2026-09-20). There was an in-app toggle
 * ORed with this; it is gone, because a player who has already told the phone
 * once should not have to tell the game again, and a second switch that could
 * disagree with the system one is a second thing to keep in step. Previews and
 * screenshot goldens still force the reduced state through
 * `AppThemeProvider(reduceMotion = true)` — they have no OS setting to read.
 *
 * Composable so it observes the live setting where the platform exposes a
 * reactive source; desktop has no such signal and answers `false`.
 */
@Composable
expect fun isOsReduceMotionEnabled(): Boolean
