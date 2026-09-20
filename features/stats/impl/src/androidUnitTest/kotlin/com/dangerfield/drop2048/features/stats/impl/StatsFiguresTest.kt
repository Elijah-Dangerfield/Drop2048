package com.dangerfield.drop2048.features.stats.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsNode
import com.dangerfield.drop2048.features.stats.impl.screenshot.ROBOLECTRIC_QUALIFIERS
import com.dangerfield.drop2048.features.stats.impl.screenshot.ROBOLECTRIC_SDK
import com.dangerfield.drop2048.libraries.progress.RunStats
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * The two figures the owner asked for on 2026-09-20 are on the page, with the
 * player's own numbers in them.
 *
 * A goldens-only check would not do: the page is longer than the frame it is
 * captured in, so a row added below the fold moves no pixels and a row that
 * rendered `0` instead of the real value would look identical to one that
 * worked. This reads the semantics tree instead, which is also what a screen
 * reader gets.
 *
 * The high score is read digit by digit because `ScoreCounter` draws it through
 * `FixedWidthDigits` — one text node per character, so the number the player
 * sees only exists as a concatenation (decision D15).
 *
 * **Not covered here:** that the figures are the right fold of `run_record`.
 * `RunStatsTest` hand-counts both against arithmetic done off the machine.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ROBOLECTRIC_SDK], qualifiers = ROBOLECTRIC_QUALIFIERS)
class StatsFiguresTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the page shows the high score and the highest level reached`() {
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PreviewContent {
                    StatsScreen(
                        state = StatsState(loading = false, stats = Played),
                        onAction = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        val drawn = compose.allText()

        assertTrue("HIGH SCORE" in drawn, "the high score has no label: $drawn")
        assertTrue("18240" in drawn, "the high score is not on the page: $drawn")
        assertTrue("HIGHEST LEVEL" in drawn, "the highest level has no label: $drawn")
        assertTrue("11" in drawn, "the highest level is not on the page: $drawn")
    }

    /**
     * The negative half (L35). Both assertions above would pass against rows
     * hard-coded to the fixture's numbers, so a second set proves the page is
     * reading the stats it was handed.
     */
    @Test
    fun `the figures follow the run history rather than being drawn in`() {
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PreviewContent {
                    StatsScreen(
                        state = StatsState(
                            loading = false,
                            stats = Played.copy(bestScore = 640, highestLevel = 3),
                        ),
                        onAction = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        val drawn = compose.allText()

        assertTrue("640" in drawn, "the high score did not follow the stats: $drawn")
        assertTrue("18240" !in drawn, "an older high score is still on the page: $drawn")
    }

    private companion object {
        val Played = RunStats(
            runsPlayed = 14,
            bestScore = 18_240,
            averageScore = 6_112,
            highestLevel = 11,
            highestTier = 1024,
            totalMerges = 1_284,
            totalBlocksPlaced = 2_610,
            longestCascade = 7,
            mostBurstsInARun = 2,
            lifetimeBursts = 5,
            totalPlaytimeMs = 9_240_000,
            recentScores = listOf(18_240L, 4_010L, 9_120L, 2_400L, 6_780L),
        )
    }
}

/**
 * Every string the composition would hand a screen reader, in tree order and
 * concatenated.
 *
 * Concatenated rather than a list because the one number this file is about is
 * split across a node per digit, and joining is what puts it back together.
 */
private fun SemanticsNodeInteractionsProvider.allText(): String =
    onRoot().fetchSemanticsNode().collectText().joinToString(separator = "")

private fun SemanticsNode.collectText(): List<String> {
    val own = config.getOrNull(SemanticsProperties.Text)
        ?.joinToString(separator = "") { it.text }
        .orEmpty()
    return listOf(own) + children.flatMap { it.collectText() }
}
