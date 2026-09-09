package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.runtime.Composable

/** Desktop has nothing to vibrate. This target is preview and tooling only. */
@Composable
actual fun rememberPlatformHapticEngine(): HapticEngine = HapticEngine.Silent
