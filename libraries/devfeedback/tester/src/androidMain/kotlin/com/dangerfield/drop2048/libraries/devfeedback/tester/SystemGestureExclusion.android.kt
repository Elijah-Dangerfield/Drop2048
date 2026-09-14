package com.dangerfield.drop2048.libraries.devfeedback.tester

import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier

internal actual fun Modifier.excludeFromSystemGestures(): Modifier = systemGestureExclusion()
