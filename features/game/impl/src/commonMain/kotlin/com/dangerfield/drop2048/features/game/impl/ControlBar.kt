package com.dangerfield.drop2048.features.game.impl

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.ui.components.button.Button
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonAccent
import com.dangerfield.drop2048.libraries.ui.components.icon.Icons
import com.dangerfield.drop2048.system.Dimension
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The three bottom controls (SPEC 6, decision D11): ◀, ▼, ▶.
 *
 * The middle button used to be hard drop and is now the **nudge**. That is not
 * a rename: hard drop ended the block's fall and the nudge advances it two rows,
 * so the control went from the most consequential press in the game to the least.
 * It keeps its place in the middle because a thumb reaching for the centre is
 * still the cheapest reach, and because the handoff puts it there.
 *
 * It is **not** widened any more. The old comment said the drop button was the
 * widest because it was "the one press a player makes on nearly every block";
 * that stopped being true with the ruling, and the handoff draws all three
 * buttons equal with ▼ recessive. The recessive *styling* is C3b's; the equal
 * width is here because it is a layout consequence of the ruling rather than a
 * visual one.
 *
 * **Mirrored, not re-laid-out.** The left-handed option reverses the row rather
 * than rebuilding it, so the two arrangements cannot drift apart and there is
 * one place a fourth control would have to be added.
 */
@Composable
fun ControlBar(
    leftHanded: Boolean,
    enabled: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onNudge: () -> Unit,
    onSoftDropStart: () -> Unit,
    onSoftDropEnd: () -> Unit,
    labels: ControlLabels,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimension.D300),
        modifier = modifier.fillMaxWidth(),
    ) {
        val leading = if (leftHanded) Sideways.Right else Sideways.Left
        val trailing = if (leftHanded) Sideways.Left else Sideways.Right

        MoveButton(
            direction = leading,
            enabled = enabled,
            labels = labels,
            onMoveLeft = onMoveLeft,
            onMoveRight = onMoveRight,
            modifier = Modifier.weight(SideWeight).height(ControlHeight),
        )

        NudgeButton(
            enabled = enabled,
            label = labels.nudge,
            onNudge = onNudge,
            onSoftDropStart = onSoftDropStart,
            onSoftDropEnd = onSoftDropEnd,
            modifier = Modifier.weight(SideWeight).height(ControlHeight),
        )

        MoveButton(
            direction = trailing,
            enabled = enabled,
            labels = labels,
            onMoveLeft = onMoveLeft,
            onMoveRight = onMoveRight,
            modifier = Modifier.weight(SideWeight).height(ControlHeight),
        )
    }
}

private enum class Sideways { Left, Right }

@Composable
private fun MoveButton(
    direction: Sideways,
    enabled: Boolean,
    labels: ControlLabels,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon = if (direction == Sideways.Left) Icons.ChevronLeft else Icons.ChevronRight
    val label = if (direction == Sideways.Left) labels.moveLeft else labels.moveRight
    Button(
        onClick = if (direction == Sideways.Left) onMoveLeft else onMoveRight,
        enabled = enabled,
        accent = ButtonAccent.Secondary,
        icon = icon(label),
        deep = true,
        modifier = modifier,
        content = {},
    )
}

/**
 * One control, two of SPEC 6's inputs: tap to nudge, hold to soft drop.
 *
 * They share a button because there is no room for a fourth and because they are
 * now the same verb at two rates — two rows on a tap, a row every 40ms while
 * held. Since the ruling that is a genuinely continuous relationship rather than
 * the old "down, now" / "down, faster" pair, which is an argument for keeping
 * the pairing rather than against it.
 *
 * A hold that also fired the tap would nudge twice, so engaging the soft drop
 * disarms the tap for that gesture.
 *
 * The gesture only **watches**: it never consumes, so the button underneath
 * still detects its own click and still shows its own press state. That is also
 * why the disarm is a flag rather than a consume — the click has already fired
 * by the time this sees the pointer come up.
 */
@Composable
private fun NudgeButton(
    enabled: Boolean,
    label: String,
    onNudge: () -> Unit,
    onSoftDropStart: () -> Unit,
    onSoftDropEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val softDropping = remember { mutableStateOf(false) }
    Button(
        onClick = { if (!softDropping.value) onNudge() },
        enabled = enabled,
        icon = Icons.DropDown(label),
        deep = true,
        modifier = modifier.pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                val held = withTimeoutOrNull(SoftDropAfterMillis) { waitForUpOrCancellation() }
                if (held == null) {
                    softDropping.value = true
                    onSoftDropStart()
                    waitForUpOrCancellation()
                    onSoftDropEnd()
                    softDropping.value = false
                }
            }
        },
        content = {},
    )
}

/** The three content descriptions, supplied by the screen so this file holds no copy. */
data class ControlLabels(
    val moveLeft: String,
    val moveRight: String,
    val nudge: String,
)

/**
 * Long enough that a decisive tap is never read as a hold, short enough that a
 * player who meant to nudge the block down does not feel the control stick.
 */
private const val SoftDropAfterMillis = 160L

private const val SideWeight = 1f

/** Comfortably past the 48dp minimum: this is the surface the whole game is played on. */
private val ControlHeight = 64.dp
