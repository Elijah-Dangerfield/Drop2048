package com.dangerfield.drop2048.features.settings.impl

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import com.dangerfield.drop2048.features.settings.PlayerSettings
import com.dangerfield.drop2048.libraries.ui.system.HapticsSetting
import com.dangerfield.drop2048.libraries.ui.system.LocalLargeNumbers
import com.dangerfield.drop2048.libraries.ui.system.LocalReduceMotion
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPalettes
import com.dangerfield.drop2048.libraries.ui.system.color.LocalBlockPalette
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The test this chunk exists for.**
 *
 * C2 built five block palettes, an OS-aware reduce-motion signal and a
 * large-numbers scale, and widened `AppThemeProvider` to take all three. Nothing
 * ever passed them: `App.kt` called the provider on its defaults for six chunks,
 * so every accessibility setting in this app was inert plumbing that compiled,
 * tested green and could not be reached by a player.
 *
 * This composes the **production** wire — [PlayerThemeProvider], the composable
 * `App` calls — over a real store, and reads back the CompositionLocals every
 * component in `:libraries:ui` reads. A component that honours
 * `LocalBlockPalette` (the block face), `LocalReduceMotion` (`reducible`, the
 * ghost pulse, the toast, the score counter) or `LocalLargeNumbers` (the tile
 * numeral) is reached by a player's choice if and only if this passes.
 *
 * It also asserts the **live** case, which the screenshot goldens cannot: a
 * setting changed while the app is running has to reach the composition without
 * a relaunch, because the settings screen opens over a live board.
 *
 * Reduce motion is here on different terms since the owner ruling of
 * 2026-09-20. There is no in-app toggle to reach the UI any more, so what is
 * tested is the half that survived: the phone's own setting, read through
 * `isOsReduceMotionEnabled` and driven here by writing the Android global the
 * Android actual reads.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK])
class AccessibilitySettingsReachTheUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the defaults a fresh install has are what the theme provides`() {
        val store = FakeSettingsStore()
        val seen = Observed()

        compose.setContent { PlayerThemeProvider(store) { seen.Record() } }
        compose.waitForIdle()

        assertEquals(BlockPalettes[BlockPaletteChoice.Default], seen.palette)
        assertFalse(seen.reduceMotion)
        assertFalse(seen.largeNumbers)
    }

    @Test
    fun `a stored palette choice is the palette every block face reads`() {
        val store = FakeSettingsStore(
            PlayerSettings(palette = BlockPaletteChoice.HighContrast),
        )
        val seen = Observed()

        compose.setContent { PlayerThemeProvider(store) { seen.Record() } }
        compose.waitForIdle()

        assertEquals(BlockPalettes[BlockPaletteChoice.HighContrast], seen.palette)
        // Not the default one wearing the right name. `BlockPalettes` is a map
        // and an identity check would pass on a lookup that quietly returned the
        // default, so this compares a colour that actually differs.
        assertTrue(
            seen.palette!!.styles[0].face !=
                BlockPalettes[BlockPaletteChoice.Default].styles[0].face,
        )
    }

    @Test
    fun `larger numbers reach the composition`() {
        val store = FakeSettingsStore(PlayerSettings(largeNumbers = true))
        val seen = Observed()

        compose.setContent { PlayerThemeProvider(store) { seen.Record() } }
        compose.waitForIdle()

        assertTrue(seen.largeNumbers)
    }

    /**
     * **Reduce motion has no in-app switch any more** (owner ruling,
     * 2026-09-20), and the OS path it now depends on entirely is the one thing
     * that could have gone with it. Android maps "Remove animations" onto
     * `TRANSITION_ANIMATION_SCALE`, so a zero scale is a phone asking for less
     * motion, and this composes the production wire over it.
     *
     * Paired with its negative below (L35): without the second half this would
     * pass against a `LocalReduceMotion` wired to a constant `true`.
     */
    @Test
    fun `the os reduce-motion setting still reaches the composition`() {
        setOsAnimationScale(0f)
        val seen = Observed()

        compose.setContent { PlayerThemeProvider(FakeSettingsStore()) { seen.Record() } }
        compose.waitForIdle()

        assertTrue(
            seen.reduceMotion,
            "a phone with animations removed must still get the shortened transcript. " +
                "isOsReduceMotionEnabled is the only source left.",
        )
    }

    @Test
    fun `a phone that wants animation gets it`() {
        setOsAnimationScale(1f)
        val seen = Observed()

        compose.setContent { PlayerThemeProvider(FakeSettingsStore()) { seen.Record() } }
        compose.waitForIdle()

        assertFalse(seen.reduceMotion)
    }

    private fun setOsAnimationScale(scale: Float) {
        Settings.Global.putFloat(
            RuntimeEnvironment.getApplication().contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            scale,
        )
    }

    /**
     * The live case. The settings screen opens over a running app, so a choice
     * made there has to land without a relaunch — and this is the half a
     * screenshot golden cannot cover, because a golden only ever sees one frame
     * of one composition.
     */
    @Test
    fun `changing a setting while the app is running reaches the composition`() {
        val store = FakeSettingsStore()
        val seen = Observed()

        compose.setContent { PlayerThemeProvider(store) { seen.Record() } }
        compose.waitForIdle()
        assertEquals(BlockPalettes[BlockPaletteChoice.Default], seen.palette)

        runBlocking {
            store.update {
                it.copy(
                    palette = BlockPaletteChoice.Deuteranopia,
                    largeNumbers = true,
                    haptics = HapticsSetting.Strong,
                )
            }
        }
        compose.waitForIdle()

        assertEquals(BlockPalettes[BlockPaletteChoice.Deuteranopia], seen.palette)
        assertTrue(seen.largeNumbers)
    }

    /**
     * Every one of the five palettes is selectable and arrives distinct, which
     * is what SPEC 16 asks for. That the ramps differ from each other is
     * `BlockPaletteTest`'s job; what this adds is that the *selection* arrives —
     * for all five, rather than for the one that happened to get a test.
     *
     * All five in one composition because `setContent` may be called once per
     * rule, and five stores under five providers is exactly what the assertion
     * needs anyway.
     */
    @Test
    fun `all five palettes are reachable`() {
        val seen = BlockPaletteChoice.entries.associateWith { Observed() }

        compose.setContent {
            seen.forEach { (choice, observed) ->
                PlayerThemeProvider(FakeSettingsStore(PlayerSettings(palette = choice))) {
                    observed.Record()
                }
            }
        }
        compose.waitForIdle()

        seen.forEach { (choice, observed) ->
            assertEquals(
                BlockPalettes[choice],
                observed.palette,
                "the $choice palette did not reach the UI",
            )
        }
    }
}

/**
 * What a component below the theme sees.
 *
 * A holder rather than assertions inline, because a `CompositionLocal` can only
 * be read from a composable and the assertions have to run after the frame.
 */
private class Observed {
    var palette: com.dangerfield.drop2048.libraries.ui.system.color.BlockPalette? = null
    var reduceMotion: Boolean = false
    var largeNumbers: Boolean = false

    @Composable
    fun Record() {
        palette = LocalBlockPalette.current
        reduceMotion = LocalReduceMotion.current
        largeNumbers = LocalLargeNumbers.current
    }
}

/** Pinned rather than tracking `compileSdk`, as the other harnesses do. */
internal const val ROBOLECTRIC_SDK = 34
