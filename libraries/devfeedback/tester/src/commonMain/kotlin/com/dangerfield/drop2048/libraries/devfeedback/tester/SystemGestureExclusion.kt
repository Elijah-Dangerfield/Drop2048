package com.dangerfield.drop2048.libraries.devfeedback.tester

import androidx.compose.ui.Modifier

/**
 * Asks the OS not to claim touches inside this element for its own edge
 * gestures.
 *
 * Needed because Android's gesture navigation owns both screen edges for system
 * back, and it owns them for *taps* too, not only drags. The directive button
 * can be dragged flush against either edge, so without this it becomes
 * untappable exactly where a tester is most likely to park it to keep it off the
 * board.
 *
 * A no-op on iOS, where the right edge is free — the interactive pop gesture
 * lives on the left, and the app has no swipe-back of its own to compete with.
 */
internal expect fun Modifier.excludeFromSystemGestures(): Modifier
