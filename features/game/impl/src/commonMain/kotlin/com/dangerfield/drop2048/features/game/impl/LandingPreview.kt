package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.SpecialBlock
import com.dangerfield.drop2048.libraries.cascade.numberValue
import com.dangerfield.drop2048.libraries.cascade.specialKind

/**
 * One outlined cell of the landing preview.
 *
 * @param bright the design's will-merge appearance: near-white border, faint
 *   fill. The plain state is a dim dashed outline and says only "it goes here".
 * @param label the mark in the middle, or null for a bright cell that is part of
 *   a footprint rather than a promise about a value.
 */
data class GhostCell(val cell: Cell, val bright: Boolean, val label: String? = null)

/**
 * What the landing ghost draws, for every block the game can hand it.
 *
 * **The handoff drew two states and SPEC 5.2 needs five.** The prototype has no
 * special blocks at all, so its ghost is "will it merge, yes or no". This project
 * ships a Wildcard, a Bomb and a Stone, and each of them makes a different
 * promise about the cell the player is about to commit to. Drawing all three as a
 * plain dashed outline would be honest only about the Stone.
 *
 * The rulings, and the reasoning for each:
 *
 * - **Stone** — always plain, never bright, no label. A Stone has no value and
 *   never merges with anything, so a bright ghost would be a lie the very first
 *   time a player saw one. Its landing preview says exactly one true thing:
 *   this is where the obstacle lands.
 *
 * - **Wildcard** — the bright cell is the **neighbour**, not the landing cell.
 *   SPEC 5.2 has the Wildcard take a neighbour's value doubled, so the cell that
 *   *changes* is the neighbour's, and the interesting question is which of up to
 *   four neighbours it will pick. Marking the landing cell instead would answer a
 *   question nobody is asking. The label is `×2` for the same reason it is on a
 *   value merge: the neighbour doubles. The landing cell keeps a plain outline so
 *   the player can still see where the block itself goes.
 *
 * - **Bomb** — all five cells outlined bright, and **no label on any of them**.
 *   The footprint is the message. A `×5` in the middle would read as a
 *   multiplier, which is what every other mark on this board means, and a bomb
 *   multiplies nothing. Only occupied neighbours are drawn: an empty cell is not
 *   going to visibly clear, and outlining it promises a bang that does not
 *   happen (SPEC 18.4 scores a bomb per block destroyed, so an empty neighbour is
 *   worth nothing either).
 *
 * - **A value block** — bright with `×2` when any orthogonal neighbour of the
 *   landing cell holds the same value, or holds a Wildcard. The Wildcard case is
 *   decision D6's symmetry: a value block landing beside a resting Wildcard
 *   merges with it, and without the ghost saying so the Wildcard reads as an
 *   inert obstacle and the obvious move looks like it does nothing.
 *
 * A [BlockValue] at the top tier cannot double, so a 2048 next to a 2048 is not a
 * merge and is not drawn as one. That falls out of `doubled` being nullable.
 */
fun landingPreview(board: Board, falling: Block, landing: Cell): List<GhostCell> {
    val neighbours = board.neighborsOf(landing)
    return when (falling.specialKind) {
        Special.STONE -> listOf(GhostCell(landing, bright = false))

        Special.BOMB -> {
            val hit = neighbours.filter { board[it] != null }
            (listOf(landing) + hit).map { GhostCell(it, bright = true) }
        }

        Special.WILDCARD -> {
            val target = neighbours.firstOrNull { board[it]?.numberValue?.doubled != null }
            if (target == null) {
                listOf(GhostCell(landing, bright = false))
            } else {
                listOf(GhostCell(landing, bright = false), GhostCell(target, bright = true, label = MergeLabel))
            }
        }

        null -> {
            val value = (falling as? NumberBlock)?.value
            val merges = value?.doubled != null && neighbours.any { neighbour ->
                board[neighbour]?.numberValue == value ||
                    board[neighbour] == SpecialBlock(Special.WILDCARD)
            }
            listOf(GhostCell(landing, bright = merges, label = MergeLabel.takeIf { merges }))
        }
    }
}

/**
 * A symbol rather than a string resource. `×2` reads the same in every locale
 * this game ships to, and routing it through `:libraries:resources` would move a
 * design decision into a translation file.
 */
private const val MergeLabel = "×2"
