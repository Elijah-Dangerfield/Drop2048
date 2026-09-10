package com.dangerfield.drop2048.features.debug

import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.MergeKind
import com.dangerfield.drop2048.libraries.cascade.ResolutionStep
import com.dangerfield.drop2048.libraries.cascade.Transcript

/**
 * One line of the diagnostics overlay's resolution log.
 *
 * [step] is kept as a number rather than folded into [text] so the overlay can
 * group and indent by cascade step without parsing its own output.
 */
data class TranscriptLine(
    val step: Int,
    val text: String,
    val points: Int,
)

/**
 * The transcript, in the order it happened, as text a human can read at 3am.
 *
 * This is a **formatter over data the engine already returns** (SPEC 4.2), which
 * is the entire reason SPEC 19 calls the overlay nearly free. It is pure and has
 * no Compose in it, so the thing that decides what the log says is unit-tested
 * rather than screenshot-tested — a golden can only say the panel looked the
 * same, and what matters here is whether it said the right thing.
 *
 * Every line names cells, because "merged left" is not an answer to "why did
 * that merge go left" and `(2,6) <- (3,6)` is. The direction is derived from the
 * cells rather than printed from a field, so a line cannot disagree with the
 * merge it describes.
 */
fun Transcript.describe(): List<TranscriptLine> = steps.map { it.describe() }

private fun ResolutionStep.describe(): TranscriptLine = when (this) {
    is ResolutionStep.Merge -> TranscriptLine(
        step = step,
        text = buildString {
            append(if (kind == MergeKind.WILDCARD) "wildcard " else "merge ")
            append(initiator)
            append(' ')
            append(arrow(initiator, partner))
            append(' ')
            append(partner)
            append(" = ")
            append(result.points)
        },
        points = points,
    )

    is ResolutionStep.Detonation -> TranscriptLine(
        step = step,
        text = "bomb $bomb took ${destroyed.size} (${destroyed.joinToString(" ")})",
        points = points,
    )

    is ResolutionStep.Burst -> TranscriptLine(
        step = step,
        text = "burst row $row, ${cleared.size} cells" +
            if (stones > 0) ", $stones stone${if (stones == 1) "" else "s"}" else "",
        points = points,
    )

    is ResolutionStep.Gravity -> TranscriptLine(
        step = step,
        text = "gravity " + moves.joinToString(" ") { "${it.from}>${it.to}" },
        points = points,
    )

    is ResolutionStep.Survival -> TranscriptLine(step, "survived level $level", points)

    is ResolutionStep.LevelUp -> TranscriptLine(step, "level $level", points)

    is ResolutionStep.BoardCleared -> TranscriptLine(step, "board cleared", points)
}

/**
 * Which way the initiator travelled, read off the two cells.
 *
 * SPEC 4.3's priority order has no `UP`, so a merge that reports one is a bug in
 * the resolver and the log should say `?` rather than quietly pick a plausible
 * arrow. This is a debugging tool; making a wrong answer look right is the one
 * thing it must not do.
 */
private fun arrow(from: Cell, to: Cell): String = when {
    to.row > from.row && to.col == from.col -> "v"
    to.col < from.col && to.row == from.row -> "<"
    to.col > from.col && to.row == from.row -> ">"
    else -> "?"
}
