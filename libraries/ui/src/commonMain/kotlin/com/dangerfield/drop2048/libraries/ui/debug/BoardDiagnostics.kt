package com.dangerfield.drop2048.libraries.ui.debug

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The two halves of SPEC 19's overlay that are properties of a *cell* rather
 * than of a frame, and therefore have to be drawn by the board itself.
 *
 * A `CompositionLocal` rather than two parameters threaded down through the
 * board, its slots and its falling block, for the reason `AGENTS.md` gives for
 * every cross-cutting value: the alternative is a debug-only argument on three
 * private composables that every future signature change has to carry. Defaulted
 * to off rather than to `error(...)`, so previews and screenshot tests get the
 * quiet version for free.
 */
data class BoardDiagnostics(
    val showCellCoordinates: Boolean = false,
    val showMergeArrows: Boolean = false,
) {
    val anythingOn: Boolean get() = showCellCoordinates || showMergeArrows
}

val LocalBoardDiagnostics = staticCompositionLocalOf { BoardDiagnostics() }
