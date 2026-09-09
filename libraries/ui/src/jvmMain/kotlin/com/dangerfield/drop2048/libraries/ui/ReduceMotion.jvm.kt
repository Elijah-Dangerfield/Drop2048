package com.dangerfield.drop2048.libraries.ui

import androidx.compose.runtime.Composable

/**
 * Desktop has no OS-level reduce-motion signal to read, so animations play. This
 * target is preview and tooling only.
 */
@Composable
actual fun isOsReduceMotionEnabled(): Boolean = false
