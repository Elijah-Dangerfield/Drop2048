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
 * The three bottom controls (SPEC 6): move left, hard drop, move right.
 *
 * Buttons ship first because they are the scheme an automated test can drive,
 * which matters while the loop is still being tuned. Drag arrives in C3a and
 * takes the default if it feels better on device.
 *
 * **Mirrored, not re-laid-out.** The left-handed option reverses the row rather
 * than rebuilding it, so the two arrangements cannot drift apart and there is
 * one place a fourth control would have to be added.
 *
 * The drop button is the widest and sits in the middle: SPEC 16 asks for
 * generous targets on the primary interaction surface, and the drop is the one
 * press a player makes on nearly every block.
 */
@Composable
fun ControlBar(
    leftHanded: Boolean,
    enabled: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onHardDrop: () -> Unit,
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

        DropButton(
            enabled = enabled,
            label = labels.drop,
            onHardDrop = onHardDrop,
            onSoftDropStart = onSoftDropStart,
            onSoftDropEnd = onSoftDropEnd,
            modifier = Modifier.weight(DropWeight).height(ControlHeight),
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
 * One control, two of SPEC 6's inputs: tap to hard drop, hold to soft drop.
 *
 * They share a button because there is no room for a fourth and because they are
 * the same intent at two commitment levels — "down, now" and "down, faster".
 * A hold that ends in a hard drop would be both at once, so engaging the soft
 * drop disarms the tap for that gesture.
 *
 * The gesture only **watches**: it never consumes, so the button underneath
 * still detects its own click and still shows its own press state. That is also
 * why the disarm is a flag rather than a consume — the click has already fired
 * by the time this sees the pointer come up.
 */
@Composable
private fun DropButton(
    enabled: Boolean,
    label: String,
    onHardDrop: () -> Unit,
    onSoftDropStart: () -> Unit,
    onSoftDropEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val softDropping = remember { mutableStateOf(false) }
    Button(
        onClick = { if (!softDropping.value) onHardDrop() },
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
    val drop: String,
)

/**
 * Long enough that a decisive tap is never read as a hold, short enough that a
 * player who meant to nudge the block down does not feel the control stick.
 */
private const val SoftDropAfterMillis = 160L

private const val SideWeight = 1f
private const val DropWeight = 1.4f

/** Comfortably past the 48dp minimum: this is the surface the whole game is played on. */
private val ControlHeight = 64.dp
