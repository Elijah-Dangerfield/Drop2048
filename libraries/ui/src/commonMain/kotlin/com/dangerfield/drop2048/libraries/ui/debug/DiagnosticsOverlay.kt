package com.dangerfield.drop2048.libraries.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.system.AppTheme

/**
 * SPEC 19's in-game diagnostics panel.
 *
 * Takes plain values rather than a feature's types, because it is a design
 * system component and the design system has never heard of a run. The two
 * things it draws that a `Text` cannot — the frame rate and the frame time — are
 * drawn in [FrameRateMeter] for the reason that rule exists.
 */
@Composable
fun DiagnosticsOverlay(
    modifier: Modifier = Modifier,
    showFrameRate: Boolean = false,
    intendedTickMs: Long? = null,
    actualTickMs: Long? = null,
    cascadeStep: Int? = null,
    transcript: List<String> = emptyList(),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppTheme.colors.surfacePrimary.color.copy(alpha = ScrimAlpha))
            .navigationBarsPadding()
            .padding(PanelPadding),
    ) {
        if (showFrameRate) FrameRateMeter()
        if (intendedTickMs != null) {
            Text(
                text = "tick ${actualTickMs ?: 0}ms / ${intendedTickMs}ms",
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )
        }
        if (cascadeStep != null) {
            Text(
                text = "cascade step $cascadeStep",
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )
        }
        if (transcript.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LogHeight)
                    .verticalScroll(rememberScrollState()),
            ) {
                transcript.forEach { line ->
                    Text(
                        text = line,
                        typography = AppTheme.typography.Body.B500,
                        color = AppTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * Frames per second and frame time, sampled every frame and drawn in
 * `drawBehind`.
 *
 * **The number is never read during composition**, which is the whole design of
 * this component and not a stylistic choice. A value that changes every frame,
 * read in a composable body, recomposes the subtree sixty times a second and
 * feeds Skia's glyph cache a new string each time — the repo has a detekt rule
 * that fails the build over it, and a downstream app wedged its RenderThread
 * doing exactly this. Reading the state inside the draw lambda invalidates the
 * draw phase only.
 *
 * Under [LocalInspectionMode] the frame loop never starts and a fixed value is
 * drawn. An infinite `withFrameNanos` loop is precisely the thing that hangs
 * preview and screenshot capture, because the test waits for an idle state that
 * a running loop never reaches.
 */
@Composable
fun FrameRateMeter(modifier: Modifier = Modifier) {
    val inspecting = LocalInspectionMode.current
    val fps = remember { mutableFloatStateOf(if (inspecting) InspectionFps else 0f) }
    val measurer = rememberTextMeasurer()
    val style = TextStyle(color = AppTheme.colors.textSecondary.color, fontSize = MeterFontSize)

    if (!inspecting) {
        LaunchedEffect(Unit) {
            var previous = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val deltaNanos = now - previous
                previous = now
                if (deltaNanos > 0) {
                    val instant = NanosPerSecond.toFloat() / deltaNanos
                    // A rolling mean rather than the instantaneous value: at 60Hz
                    // a raw per-frame number swings by ten either side and is
                    // unreadable, which makes a real drop from 60 to 45 harder to
                    // see rather than easier.
                    fps.floatValue =
                        if (fps.floatValue == 0f) instant else fps.floatValue * Smoothing + instant * (1 - Smoothing)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MeterHeight)
            .drawBehind {
                val value = fps.floatValue
                val frameMs = if (value > 0f) MillisPerSecond / value else 0f
                drawText(
                    textMeasurer = measurer,
                    text = "${value.toInt()} fps  ${frameMs.toInt()}ms",
                    style = style,
                    topLeft = Offset.Zero,
                )
            },
    )
}

private const val NanosPerSecond = 1_000_000_000L
private const val MillisPerSecond = 1_000f
private const val Smoothing = 0.9f
private const val InspectionFps = 60f
private const val ScrimAlpha = 0.85f
private val PanelPadding = 8.dp
private val LogHeight = 120.dp
private val MeterHeight = 18.dp
private val MeterFontSize = 12.sp
