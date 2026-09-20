package com.dangerfield.drop2048.features.settings

import androidx.compose.runtime.Immutable
import com.dangerfield.drop2048.libraries.ui.system.HapticsSetting
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import kotlinx.coroutines.flow.StateFlow

/**
 * Every setting that changes how the game looks, sounds or is driven.
 *
 * **This type is the whole point of C11.** C2 built five block palettes, an
 * OS-aware reduce-motion signal, a large-numbers scale and a platform haptic
 * engine, and `AppThemeProvider` was still being called on its defaults six
 * chunks later, so none of it was reachable. The values here are the ones
 * `AppThemeProvider` takes, in the types it takes them in — not a parallel enum
 * that a mapping function keeps almost in step.
 *
 * Read from one [PlayerSettingsStore] at the root of the composition, so a
 * component still reads what it needs from a CompositionLocal and takes no
 * accessibility parameters of its own (decision D4).
 *
 * **There is no `reduceMotion` here, and that is deliberate (owner ruling,
 * 2026-09-20).** Reduce motion is the OS setting and nothing else:
 * `isOsReduceMotionEnabled` is read inside `AppThemeProvider` and published on
 * `LocalReduceMotion`, so a player who told their phone once is honoured without
 * a second switch in this app that could disagree with it. Adding a field back
 * here is how the two sources start diverging again.
 */
@Immutable
data class PlayerSettings(
    /** SPEC 16's five ramps. `HighContrast` is one of them, not a sixth switch. */
    val palette: BlockPaletteChoice = BlockPaletteChoice.Default,

    /** SPEC 16's larger block numerals. A scale on the face, not a font size. */
    val largeNumbers: Boolean = false,

    /** SPEC 9's Off / Light / Strong. Scales every haptic in the game. */
    val haptics: HapticsSetting = HapticsSetting.Light,

    /** SPEC 9's effects. The sample bank is C3a; the switch is honoured now. */
    val soundEnabled: Boolean = true,

    /**
     * SPEC 6, and an owner ruling on 2026-09-20: the game ships on drag, with no
     * arrow row, and the settings switch is how a player gets one back.
     *
     * Buttons shipped first because they were the easiest thing to drive from a
     * test while the loop was being tuned, and SPEC 6 always said drag takes the
     * default once the loop is proven. It is. Nothing should be built that
     * assumes the button row is on screen.
     */
    val controlScheme: ControlScheme = ControlScheme.Drag,

    /** SPEC 6's landing outline. On by default. */
    val ghostEnabled: Boolean = true,

    /** Seven taps on the version number (C12). Persisted, so it survives a launch. */
    val debugMenuUnlocked: Boolean = false,
)

/**
 * A stored enum name, or [fallback] if it is missing or no longer exists.
 *
 * Never throws. A palette that was removed between releases must cost the player
 * a colour scheme, not their whole record — which is what `valueOf` would do,
 * because these decode on the way out of one serialized blob that also carries
 * the install id and the onboarding flag.
 *
 * Here rather than beside the store because the game screen decodes
 * [ControlScheme] out of the same blob, and two copies of a lenient decoder is
 * two places for one of them to become `valueOf`.
 */
inline fun <reified T : Enum<T>> String?.asEnumOr(fallback: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback

/**
 * SPEC 6's schemes. [Drag] is the shipping default since 2026-09-20; the arrows
 * are additive, not exclusive.
 *
 * Three values, and the settings screen offers one switch. That is deliberate:
 * what a player is choosing is whether the arrow row is on their screen, and the
 * two answers to that are [Drag] and [Both]. [Buttons] is the same answer with
 * dragging switched off as well — nothing sets it any more, and a stored one is
 * still honoured, because taking a scheme away from somebody who chose it is a
 * worse trade than leaving a value nothing writes.
 */
enum class ControlScheme {
    /** The three bottom buttons only. Drag on the board does nothing. */
    Buttons,

    /** Drag anywhere on the board, absolute from the grab point. Buttons hidden. */
    Drag,

    /** Both at once, which is what the game shipped with from C3 until the ruling. */
    Both,
}

/**
 * The one place the player's settings are read from and written to.
 *
 * A [StateFlow] rather than a suspend read because the root of the composition
 * has to hand these to `AppThemeProvider` on the very first frame: a palette
 * that arrives one frame late is a visible flash of the wrong colours, and a
 * reduce-motion setting that arrives late has already played the animation it
 * was meant to suppress.
 *
 * Every write goes straight through to disk rather than batching on exit. There
 * is no "save" button on a settings screen to hang a commit off, and a toggle
 * the player flipped before backgrounding the app has to still be flipped.
 */
interface PlayerSettingsStore {
    val settings: StateFlow<PlayerSettings>

    suspend fun update(transform: (PlayerSettings) -> PlayerSettings)
}
