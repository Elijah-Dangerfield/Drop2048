package com.dangerfield.drop2048.libraries.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalWindowInfo
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors

/**
 * The surface every screen in the app is drawn on: the handoff's
 * `radial-gradient(120% 70% at 50% -10%, …)`, sized to whatever it is drawn into.
 *
 * Written for the board and applied to everything since. [Screen] draws it, so
 * settings and stats fall away from the same point above the top edge
 * that the board does; a flat fill beside a gradient is a seam even when both are
 * the same dark violet.
 *
 * A modifier rather than a `Box` with a background, because the brush depends on
 * the measured size and a `Brush` built at composition time is built before
 * anything has been measured. Bake it once and it stretches wrongly on the next
 * phone — which is a bug that only appears on hardware nobody on the team owns.
 */
fun Modifier.appBackdrop(): Modifier = drawBehind {
    drawRect(GameColors.backdrop(size.width, size.height))
}

/**
 * The same backdrop, drawn into an element that only covers the **top** of the
 * screen, so its pixels line up with the full-screen one behind it.
 *
 * [TopBar] needs this. It has to be opaque — every list screen scrolls its
 * content underneath it, and a transparent bar let rows ride up over the title —
 * and a flat fill at any single colour is a visible band across the top of a
 * gradient. Drawing the gradient at the bar's own height would be worse: the
 * brush is sized to what it is drawn into, so a 56dp bar would compress the whole
 * falloff into 56dp.
 *
 * The trick is that the bar starts at the window's origin, so its local
 * coordinates *are* the screen's. Sizing the brush to the window height and
 * drawing it in the bar's own space reproduces exactly the top slice of the
 * screen's backdrop.
 *
 * Falls back to the element's own height when the window size is not known yet,
 * which is the first frame and every screenshot harness that measures a `Box`
 * rather than a window.
 */
@Composable
fun Modifier.appBackdropTopSlice(): Modifier {
    val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
    return drawBehind {
        drawRect(GameColors.backdrop(size.width, if (windowHeight > 0f) windowHeight else size.height))
    }
}
