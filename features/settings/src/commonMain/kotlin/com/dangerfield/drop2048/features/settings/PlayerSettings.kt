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
 */
@Immutable
data class PlayerSettings(
    /** SPEC 16's five ramps. `HighContrast` is one of them, not a sixth switch. */
    val palette: BlockPaletteChoice = BlockPaletteChoice.Default,

    /**
     * SPEC 16's reduce motion. ORed with the OS setting inside
     * `AppThemeProvider`, so `false` here does not mean animations run.
     */
    val reduceMotion: Boolean = false,

    /** SPEC 16's larger block numerals. A scale on the face, not a font size. */
    val largeNumbers: Boolean = false,

    /** SPEC 9's Off / Light / Strong. Scales every haptic in the game. */
    val haptics: HapticsSetting = HapticsSetting.Light,

    /** SPEC 9's effects. The sample bank is C3a; the switch is honoured now. */
    val soundEnabled: Boolean = true,

    /** SPEC 9's one music track and its three intensity layers. */
    val musicEnabled: Boolean = true,

    /** SPEC 6. Buttons ship first and are the default; drag lands beside them. */
    val controlScheme: ControlScheme = ControlScheme.Both,

    /** SPEC 6's mirror. Moved here from the pause overlay's placeholder. */
    val leftHanded: Boolean = false,

    /** SPEC 6's landing outline. On by default. */
    val ghostEnabled: Boolean = true,

    /**
     * Whether Quit from the pause overlay asks first.
     *
     * On by default: quitting ends the run and there is no undo for it, and the
     * control sits one thumb-width from Restart.
     */
    val confirmBeforeQuit: Boolean = true,

    /**
     * Whether a feedback report may carry the device and build details with it.
     *
     * Off by default and opt-in per SPEC 17 — diagnostics are useful to us and
     * are nobody's default expectation.
     */
    val diagnosticsOptIn: Boolean = false,

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

/** SPEC 6's schemes. Buttons is the shipping default; drag is additive, not exclusive. */
enum class ControlScheme {
    /** The three bottom buttons only. Drag on the board does nothing. */
    Buttons,

    /** Drag anywhere on the board, absolute from the grab point. Buttons hidden. */
    Drag,

    /** Both at once, which is what the game has shipped with since C3. */
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
