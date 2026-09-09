package com.dangerfield.drop2048.libraries.ui.components.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.system.LocalReduceMotion
import com.dangerfield.drop2048.libraries.ui.system.Motion
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.libraries.ui.system.reduced
import com.dangerfield.drop2048.system.typography.FredokaFontFamily

/**
 * `CHAIN ×3`, `ROW BUST!`, `LEVEL 4` — one line of oversized type that rises out
 * of the board and fades.
 *
 * It draws over the board rather than in a HUD slot, and that is the design
 * decision worth keeping: the player is looking at the cascade when it fires, so
 * anything that appears anywhere else is not read at all. The cost is that it
 * briefly covers cells, which is why it is 900ms and gone.
 *
 * The animation is three properties on one clock: scale overshoots to 1.1 at
 * 30%, the whole thing drifts from 20% below centre up to 20% above it, and the
 * alpha holds through 80% before dropping. Driven by a single `Animatable`
 * running 0→1 so the three can never desynchronise, which they would as three
 * separate `animate*AsState` calls with three separate specs.
 *
 * Nothing is read during composition. Under `LocalInspectionMode` the clock is
 * parked at the overshoot, which is both the most useful frame for a preview and
 * the one a screenshot test wants; without that a golden would sample whatever
 * phase the capture happened to catch.
 *
 * @param text the line to show. Supplied by the feature — this module has no copy.
 * @param key what identifies *this* toast. The animation restarts when it
 *   changes, so two consecutive `CHAIN ×2`s need two different keys or the
 *   second one silently does not play.
 */
@Composable
fun GameToast(
    text: String,
    modifier: Modifier = Modifier,
    key: Any = text,
    scale: BoardScale = LocalBoardScale.current,
) {
    val inspecting = LocalInspectionMode.current
    val reduceMotion = LocalReduceMotion.current
    val clock = remember { Animatable(if (inspecting) PeakFraction else 0f) }

    LaunchedEffect(key, inspecting) {
        if (inspecting) return@LaunchedEffect
        clock.snapTo(0f)
        clock.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = reduced(Motion.ToastMillis, reduceMotion),
                easing = LinearOutSlowInEasing,
            ),
        )
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        BasicText(
            text = text,
            style = TextStyle(
                fontFamily = FredokaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = scale.toast.value.sp,
                color = GameColors.Ink,
                textAlign = TextAlign.Center,
                shadow = Shadow(
                    color = GameColors.ToastShadow,
                    offset = Offset(x = 0f, y = ShadowDropEm * scale.em.value),
                    blurRadius = 0f,
                ),
            ),
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                val t = clock.value
                alpha = toastAlpha(t)
                val s = toastScale(t)
                scaleX = s
                scaleY = s
                translationY = size.height * toastRise(t)
            },
        )
    }
}

/** `.6 → 1.1 at 30% → 1`, then held. */
private fun toastScale(t: Float): Float = when {
    t <= PeakFraction -> lerp(FromScale, PeakScale, t / PeakFraction)
    t <= SettleFraction -> lerp(PeakScale, 1f, (t - PeakFraction) / (SettleFraction - PeakFraction))
    else -> 1f
}

/** `+20% → centre → -20%`, as a fraction of the line's own height. */
private fun toastRise(t: Float): Float = lerp(RiseFrom, RiseTo, t)

/** Opaque by 30%, held to 80%, gone at the end. */
private fun toastAlpha(t: Float): Float = when {
    t <= PeakFraction -> t / PeakFraction
    t <= HoldFraction -> 1f
    else -> 1f - (t - HoldFraction) / (1f - HoldFraction)
}

private fun lerp(from: Float, to: Float, fraction: Float) =
    from + (to - from) * fraction.coerceIn(0f, 1f)

private const val FromScale = 0.6f
private const val PeakScale = 1.1f
private const val PeakFraction = 0.30f
private const val SettleFraction = 0.55f
private const val HoldFraction = 0.80f
private const val RiseFrom = 0.20f
private const val RiseTo = -0.20f

/**
 * The design's `text-shadow: 0 .12em 0 #3a2a5c`, hard rather than blurred, which
 * is the same rule every other surface in the game follows. The soft white halo
 * it pairs with in CSS is dropped: Compose allows one shadow per `TextStyle`,
 * and of the two the hard one is the one carrying the shape.
 */
private const val ShadowDropEm = 0.12f
