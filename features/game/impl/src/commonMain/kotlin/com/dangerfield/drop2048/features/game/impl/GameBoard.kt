package com.dangerfield.drop2048.features.game.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.features.settings.ControlScheme
import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.SpecialBlock
import com.dangerfield.drop2048.libraries.ui.components.game.BoardScale
import com.dangerfield.drop2048.libraries.ui.components.game.BoardWell
import com.dangerfield.drop2048.libraries.ui.components.game.GameToast
import com.dangerfield.drop2048.libraries.ui.components.game.LandingGhost
import com.dangerfield.drop2048.libraries.ui.components.game.LocalBoardScale
import com.dangerfield.drop2048.libraries.ui.components.game.SpecialTile
import com.dangerfield.drop2048.libraries.ui.components.game.Tile
import com.dangerfield.drop2048.libraries.ui.components.game.TileSlot
import com.dangerfield.drop2048.libraries.ui.components.game.rememberTilePop
import com.dangerfield.drop2048.libraries.ui.system.Motion
import com.dangerfield.drop2048.libraries.ui.system.color.BlockSpecial
import com.dangerfield.drop2048.system.thenIfNotNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The well, the stack, the landing preview, the block in flight and the callout
 * over the top of it.
 *
 * Every visual here comes from `:libraries:ui`. What lives in this file is the
 * three things a design system cannot know: which cell holds what, where the
 * falling block is going, and what the finger is doing.
 *
 * **The board is square-celled and width-bound**, which is why the cell pitch is
 * derived from the width alone. At five columns a phone's width sets the cell
 * size and the extra rows cost gutter rather than pixels (L25), so a height-bound
 * board would only ever appear on a screen shaped nothing like a phone. The
 * caller is responsible for not handing this more height than `rows/cols` of its
 * width — see `GameScreen`, which is where that arithmetic is done and where the
 * design's hardcoded `max-width: 370px` is reconciled with 5x8.
 *
 * The board's `em` is derived from the measured pitch rather than fixed at the
 * design's 17px. That is [BoardScale]'s own argument taken to its conclusion: the
 * handoff writes every board dimension in `em` precisely so the whole thing
 * scales from one number, and pinning that number to a dp constant would give a
 * small phone small cells with a full-size gutter.
 */
@Composable
fun GameBoard(
    state: GameUiState,
    boardDescription: String,
    fallingDescription: String?,
    calloutText: String?,
    onSteerTo: (Int) -> Unit,
    onFlickDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val steerable = state.phase == GamePhase.Playing &&
        state.controlScheme != ControlScheme.Buttons

    BoardWell(
        modifier = modifier,
        danger = state.inDanger,
        contentDescription = boardDescription,
    ) {
        BoxWithConstraints(contentAlignment = Alignment.TopStart) {
            val cols = state.board.cols
            val rows = state.board.rows
            val pitch = maxWidth / cols
            val em = pitch * EmPerCell
            val scale = BoardScale(em = em, cell = pitch - BoardScale(em = em).gutter * 2)
            val pitchPx = with(LocalDensity.current) { pitch.toPx() }

            CompositionLocalProvider(LocalBoardScale provides scale) {
                Box(
                    modifier = Modifier
                        .size(width = pitch * cols, height = pitch * rows)
                        .steering(
                            enabled = steerable,
                            startCol = state.falling?.cell?.col ?: 0,
                            cellWidthPx = pitchPx,
                            onSteerTo = onSteerTo,
                            onFlickDown = onFlickDown,
                        ),
                ) {
                    Slots(
                        cols = cols,
                        rows = rows,
                        activeCol = state.falling?.cell?.col,
                        scale = scale,
                        pitch = pitch,
                    )

                    state.board.cells.forEachIndexed { index, block ->
                        if (block == null) return@forEachIndexed
                        val at = Cell(index % cols, index / cols)
                        key(at) {
                            PlacedBlock(
                                block = block,
                                at = at,
                                scale = scale,
                                pitch = pitch,
                            )
                        }
                    }

                    state.ghost?.let { landing ->
                        state.falling?.let { falling ->
                            landingPreview(state.board, falling.block, landing).forEach { ghost ->
                                key(ghost.cell) {
                                    Ghost(ghost = ghost, scale = scale, pitch = pitch)
                                }
                            }
                        }
                    }

                    state.falling?.let { falling ->
                        key(state.dropIndex) {
                            FallingBlock(
                                block = falling.block,
                                at = falling.cell,
                                scale = scale,
                                pitchPx = pitchPx,
                                description = fallingDescription,
                            )
                        }
                    }

                    if (calloutText != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .matchParentSize()
                                .offset(y = -pitch * rows * ToastRise),
                        ) {
                            GameToast(text = calloutText, key = state.calloutNonce, scale = scale)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Drag-anywhere steering (decision D11) and the downward flick, on one gesture.
 *
 * **Absolute from the grab point**, which is the line the handoff singles out as
 * what makes the control feel locked to the finger: the pointer's x and the
 * block's column are recorded on the way down, and every move asks for
 * `startCol + round(dx / cellWidth)`. Nothing accumulates, so a drag out and back
 * returns the block exactly where it started.
 *
 * The whole gesture is watched in [PointerEventPass.Initial] and **never
 * consumed**. The board has no other gesture to compete with, and consuming would
 * take the pointer away from anything a later chunk puts on top of it — a coach
 * mark in C5, a debug overlay in C12.
 *
 * [startCol] is captured through [rememberUpdatedState] rather than keyed into
 * `pointerInput`. Keying on it would tear down and rebuild the gesture detector
 * every time the block moved a column, which is once per drag step: the pointer
 * would be dropped mid-drag and the next move would be measured from a grab point
 * that no longer exists.
 */
@Composable
private fun Modifier.steering(
    enabled: Boolean,
    startCol: Int,
    cellWidthPx: Float,
    onSteerTo: (Int) -> Unit,
    onFlickDown: () -> Unit,
): Modifier {
    val column = rememberUpdatedState(startCol)
    val steer = rememberUpdatedState(onSteerTo)
    val flick = rememberUpdatedState(onFlickDown)
    val flickDistance = with(LocalDensity.current) { FlickDistance.toPx() }

    return this.pointerInput(enabled, cellWidthPx) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val grabbedAt = down.position
            val grabbedCol = column.value
            var last = down.position
            var elapsed = 0L

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                last = pointer.position
                elapsed = pointer.uptimeMillis - down.uptimeMillis
                if (!pointer.pressed) break
                steer.value(grabbedCol + ((last.x - grabbedAt.x) / cellWidthPx).roundToInt())
            }

            val dx = last.x - grabbedAt.x
            val dy = last.y - grabbedAt.y
            if (dy > flickDistance && dy > abs(dx) && elapsed < FlickWindowMillis) flick.value()
        }
    }
}

@Composable
private fun Slots(cols: Int, rows: Int, activeCol: Int?, scale: BoardScale, pitch: Dp) {
    repeat(rows) { row ->
        repeat(cols) { col ->
            TileSlot(
                active = col == activeCol,
                scale = scale,
                modifier = Modifier.offset(
                    x = pitch * col + scale.gutter,
                    y = pitch * row + scale.gutter,
                ),
            )
        }
    }
}

/**
 * A block at rest, popping whenever it arrives or changes.
 *
 * Keyed on its cell by the caller and replayed on the block itself, which covers
 * both halves of the design's "every landing, merge result and gravity-settled
 * tile pops". A block that moved is a new cell and therefore a new composition; a
 * 4 becoming an 8 in place is the same cell with a different block, and without
 * the block in the key that one merge — the one the player was aiming for — would
 * be the only merge with no feedback at all.
 */
@Composable
private fun PlacedBlock(block: Block, at: Cell, scale: BoardScale, pitch: Dp) {
    val pop = rememberTilePop()
    val still = LocalInspectionMode.current
    LaunchedEffect(block, still) {
        if (!still) pop.replay()
    }

    val position = Modifier.offset(x = pitch * at.col + scale.gutter, y = pitch * at.row + scale.gutter)
    when (block) {
        is NumberBlock -> Tile(value = block.value.points, scale = scale, pop = pop.value, modifier = position)
        is SpecialBlock -> SpecialTile(
            special = block.special.face,
            scale = scale,
            pop = pop.value,
            modifier = position,
        )
    }
}

@Composable
private fun Ghost(ghost: GhostCell, scale: BoardScale, pitch: Dp) {
    val target = { cell: Cell -> pitch * cell.col + scale.gutter to pitch * cell.row + scale.gutter }
    val (x, y) = target(ghost.cell)
    LandingGhost(
        willMerge = ghost.bright,
        label = ghost.label,
        scale = scale,
        modifier = Modifier.offset(x = x, y = y),
    )
}

/**
 * The block in flight: the one thing on this board that moves *between* cells.
 *
 * No sibling repo has anything like it — every animation in them is a per-cell
 * `Animatable`, which cannot express travel — so the position is two
 * `Animatable`s in pixels, read inside `graphicsLayer` and never during
 * composition. Reading them in the composable body would recompose the tile and
 * its numeral on every frame of every drop, and the
 * `AnimatedStateReadInComposition` detekt rule fails the build over it.
 *
 * The two axes have different curves on purpose, and they are the handoff's:
 * `top .11s linear`, `left .07s ease-out`. The descent has to be linear because
 * it steps one whole row per tick — ease anything onto it and a constant-rate
 * fall acquires a heartbeat. The sideways step is eased because it is a response
 * to a finger rather than a consequence of gravity.
 */
@Composable
private fun FallingBlock(
    block: Block,
    at: Cell,
    scale: BoardScale,
    pitchPx: Float,
    description: String?,
) {
    val still = LocalInspectionMode.current
    val gutterPx = with(LocalDensity.current) { scale.gutter.toPx() }
    val x = remember { Animatable(at.col * pitchPx + gutterPx) }
    val y = remember { Animatable(at.row * pitchPx + gutterPx) }

    LaunchedEffect(at.col, pitchPx, still) {
        val target = at.col * pitchPx + gutterPx
        if (still) {
            x.snapTo(target)
        } else {
            x.animateTo(target, tween(Motion.StepSideMillis, easing = LinearOutSlowInEasing))
        }
    }
    LaunchedEffect(at.row, pitchPx, still) {
        val target = at.row * pitchPx + gutterPx
        if (still) {
            y.snapTo(target)
        } else {
            y.animateTo(target, tween(Motion.StepDownMillis, easing = LinearEasing))
        }
    }

    val position = Modifier
        .graphicsLayer {
            translationX = x.value
            translationY = y.value
        }
        .thenIfNotNull(description) { label -> semantics { contentDescription = label } }

    when (block) {
        is NumberBlock -> Tile(
            value = block.value.points,
            scale = scale,
            lifted = true,
            modifier = position,
        )

        is SpecialBlock -> SpecialTile(
            special = block.special.face,
            scale = scale,
            lifted = true,
            modifier = position,
        )
    }
}

val Special.face: BlockSpecial
    get() = when (this) {
        Special.WILDCARD -> BlockSpecial.Wildcard
        Special.BOMB -> BlockSpecial.Bomb
        Special.STONE -> BlockSpecial.Stone
    }

/**
 * The design's `font-size: 17px` against the cell pitch its 370px board produces
 * at five columns: `(370 - 16) / 5 = 70.8`, so `17 / 70.8`.
 *
 * Written as the ratio rather than as 17dp so the whole board scales from the one
 * measured number, which is what [BoardScale] exists to make possible.
 */
private const val EmPerCell = 0.24f

/** The handoff puts the toast at 40% of the board's height rather than at its centre. */
private const val ToastRise = 0.10f

/** The design's `dy > 30px` flick threshold, in dp because 30 CSS pixels is 30dp. */
private val FlickDistance: Dp = 30.dp

/** `released within 450ms`. */
private const val FlickWindowMillis = 450L
