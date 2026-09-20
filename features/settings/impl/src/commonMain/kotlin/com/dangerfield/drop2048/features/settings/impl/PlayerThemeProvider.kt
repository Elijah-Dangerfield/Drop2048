package com.dangerfield.drop2048.features.settings.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.dangerfield.drop2048.features.settings.PlayerSettingsStore
import com.dangerfield.drop2048.libraries.ui.system.rememberPlatformSoundPlayer
import com.dangerfield.drop2048.system.AppThemeProvider

/**
 * The theme, built from the player's own settings rather than from defaults.
 *
 * **This composable is the point of C11.** C2 built five block palettes, an
 * OS-aware reduce-motion signal, a large-numbers scale and a platform haptic
 * engine, and widened `AppThemeProvider` to take all four — and then `App` called
 * it with no arguments for six chunks, so not one of them was reachable from the
 * app. This is the wire.
 *
 * It lives here rather than in `App.kt` for two reasons. It is the composable
 * that reads the store, so it belongs beside it. And a settings read is a state
 * read: a state read in `App` recomposes the root, and the root rebuilding the
 * nav graph has already pushed a duplicate start destination once. Isolating it
 * means a palette change recomposes the theme and its content and never re-runs
 * `App` itself.
 *
 * The store starts on defaults and publishes the player's values as soon as the
 * disk read lands. It is an `AutoInit`, so that read is in flight before the
 * first composition, and the window where defaults are on screen is the boot
 * loading screen — which draws no blocks.
 *
 * **The sound player is bound here too, and it is the same argument one chunk
 * later.** C2 built the `SoundPlayer` seam and C3a built a real engine behind it
 * on both platforms; without this line `AppThemeProvider` would go on defaulting
 * to `SoundPlayer.Silent` and the whole of SPEC 9's audio would be unreachable
 * from the app, exactly as the accessibility settings were. It is the one place
 * in the app that asks for the platform's bank, so `soundEnabled` is honoured
 * everywhere for free.
 *
 * **Reduce motion is not passed and must not be.** The owner ruling of
 * 2026-09-20 removed the in-app toggle, so `AppThemeProvider` reads the phone's
 * own accessibility setting and publishes that. Passing a stored value here
 * again is how the app ends up with two answers to one question.
 */
@Composable
fun PlayerThemeProvider(
    store: PlayerSettingsStore,
    content: @Composable () -> Unit,
) {
    val settings by store.settings.collectAsState()
    AppThemeProvider(
        palette = settings.palette,
        largeNumbers = settings.largeNumbers,
        haptics = settings.haptics,
        sounds = rememberPlatformSoundPlayer(enabled = settings.soundEnabled),
        content = content,
    )
}
