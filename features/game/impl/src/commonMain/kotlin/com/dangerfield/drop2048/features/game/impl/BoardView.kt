package com.dangerfield.drop2048.features.game.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.SpecialBlock
import com.dangerfield.drop2048.libraries.ui.components.board.BlockFace
import com.dangerfield.drop2048.libraries.ui.components.board.BoardCellGap
import com.dangerfield.drop2048.libraries.ui.components.board.BoardSurface
import com.dangerfield.drop2048.libraries.ui.components.game.ChainCallout
import com.dangerfield.drop2048.libraries.ui.system.Motion
import com.dangerfield.drop2048.libraries.ui.system.color.BlockSpecial
import com.dangerfield.drop2048.libraries.ui.system.pulsingBorder
import com.dangerfield.drop2048.libraries.ui.system.rememberPulsingColor
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Radii
import com.dangerfield.drop2048.system.thenIfNotNull

/**
 * The well, the stack, the ghost and the block in flight.
 *
 * Cell size is derived from whatever box the screen gives it, on both axes, so
 * SPEC 3's unresolved seven-versus-eight rows is a config value rather than a
 * layout rewrite: eight rows on a short phone simply draws smaller cells.
 *
 * The falling block is the one thing here that is not a grid child. Nothing in
 * either sibling repo has a piece that travels *between* cells — every animation
 * there is a per-cell `Animatable`, which cannot express travel — so it is
 * positioned absolutely and moved by a pair of `Animatable`s read inside
 * `graphicsLayer`. Reading them in composition instead would recompose the block
 * face, and its text with it, on every frame of every drop.
 */
@Composable
fun BoardView(
    state: GameUiState,
    chainLabel: String,
    boardDescription: String,
    fallingDescription: String?,
    modifier: Modifier = Modifier,
) {
    BoardSurface(modifier = modifier, contentDescription = boardDescription) {
        BoxWithConstraints {
            val cols = state.board.cols
            val rows = state.board.rows
            val cell = cellSize(maxWidth, maxHeight, cols, rows)
            val pitch = cell + BoardCellGap
            val pitchPx = with(LocalDensity.current) { pitch.toPx() }

            Box(
                modifier = Modifier.size(
                    width = pitch * cols - BoardCellGap,
                    height = pitch * rows - BoardCellGap,
                )
            ) {
                Wells(cols = cols, rows = rows, cell = cell, pitch = pitch, inDanger = state.inDanger)

                state.board.cells.forEachIndexed { index, block ->
                    if (block == null) return@forEachIndexed
                    val at = Cell(index % cols, index / cols)
                    BlockCell(
                        block = block,
                        size = cell,
                        modifier = Modifier.offset(x = pitch * at.col, y = pitch * at.row),
                    )
                }

                state.ghost?.let { ghost ->
                    Box(
                        modifier = Modifier
                            .offset(x = pitch * ghost.col, y = pitch * ghost.row)
                            .size(cell)
                            .border(
                                width = GhostBorder,
                                color = AppTheme.colors.text.color.copy(alpha = GhostAlpha),
                                shape = Radii.R300.shape,
                            ),
                    )
                }

                state.falling?.let { falling ->
                    key(state.dropIndex) {
                        FallingBlockView(
                            block = falling.block,
                            col = falling.cell.col,
                            row = falling.cell.row,
                            cell = cell,
                            pitchPx = pitchPx,
                            description = fallingDescription,
                        )
                    }
                }

                if (state.chainStep >= 2) {
                    val at = state.chainCell ?: Cell(cols / 2, rows / 2)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset(x = pitch * at.col, y = pitch * at.row)
                            .size(cell),
                    ) {
                        ChainCallout(text = chainLabel, step = state.chainStep, nonce = state.chainNonce)
                    }
                }
            }
        }
    }
}

/**
 * The empty grid the blocks sit in.
 *
 * Row 0 carries SPEC 8.3's pulsing red border while the danger state is on. It
 * is drawn on the wells rather than on the blocks so the warning is about the
 * *row*, which is what the rule is about — a player whose row 0 is half full
 * still needs to see where the line is.
 */
@Composable
private fun Wells(cols: Int, rows: Int, cell: Dp, pitch: Dp, inDanger: Boolean) {
    val pulse = rememberPulsingColor(
        from = AppTheme.colors.danger.color.copy(alpha = DangerPulseFloor),
        to = AppTheme.colors.danger.color,
    )
    repeat(rows) { row ->
        repeat(cols) { col ->
            val danger = inDanger && row == 0
            Box(
                modifier = Modifier
                    .offset(x = pitch * col, y = pitch * row)
                    .size(cell)
                    .clip(Radii.R300.shape)
                    .background(AppTheme.colors.surfaceTertiary.color)
                    .then(
                        if (danger) {
                            Modifier.pulsingBorder(
                                width = DangerBorder,
                                shape = Radii.R300.shape,
                                color = { pulse.value },
                            )
                        } else {
                            Modifier
                        }
                    ),
            )
        }
    }
}

@Composable
private fun FallingBlockView(
    block: Block,
    col: Int,
    row: Int,
    cell: Dp,
    pitchPx: Float,
    description: String?,
) {
    val still = LocalInspectionMode.current
    val x = remember { Animatable(col * pitchPx) }
    val y = remember { Animatable(row * pitchPx) }

    LaunchedEffect(col, pitchPx, still) {
        if (still) x.snapTo(col * pitchPx) else x.animateTo(col * pitchPx, Motion.Tap)
    }
    LaunchedEffect(row, pitchPx, still) {
        if (still) y.snapTo(row * pitchPx) else y.animateTo(row * pitchPx, Motion.Fall)
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = x.value
                translationY = y.value
            }
            .thenIfNotNull(description) { label -> semantics { contentDescription = label } }
    ) {
        BlockCell(block = block, size = cell)
    }
}

/** The engine's block, drawn by the design system's face. The only place the two meet. */
@Composable
fun BlockCell(block: Block, size: Dp, modifier: Modifier = Modifier) {
    when (block) {
        is NumberBlock -> BlockFace(value = block.value.points, size = size, modifier = modifier)
        is SpecialBlock -> BlockFace(special = block.special.face, size = size, modifier = modifier)
    }
}

val Special.face: BlockSpecial
    get() = when (this) {
        Special.WILDCARD -> BlockSpecial.Wildcard
        Special.BOMB -> BlockSpecial.Bomb
        Special.STONE -> BlockSpecial.Stone
    }

private fun cellSize(maxWidth: Dp, maxHeight: Dp, cols: Int, rows: Int): Dp {
    val fromWidth = (maxWidth - BoardCellGap * (cols - 1)) / cols
    val fromHeight = (maxHeight - BoardCellGap * (rows - 1)) / rows
    return minOf(fromWidth, fromHeight).coerceAtLeast(MinCell)
}

/** A floor so a pathological constraint cannot produce a negative-sized block. */
private val MinCell = 8.dp

/**
 * An **outline**, never a fill.
 *
 * The first pass drew the ghost as a translucent grey square and on device it
 * read as a Stone: a Stone is the one block with nothing on its face, so a
 * blank filled cell is already spoken for. An outline cannot be mistaken for a
 * block because it has no face at all.
 */
private const val GhostAlpha = 0.45f
private val GhostBorder = 2.dp

/** The quiet end of the danger pulse. Never off, so the row is always marked. */
private const val DangerPulseFloor = 0.35f

private val DangerBorder = 2.dp
