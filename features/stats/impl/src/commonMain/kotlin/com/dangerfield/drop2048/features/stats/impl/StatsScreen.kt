package com.dangerfield.drop2048.features.stats.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.libraries.progress.RunStats
import com.dangerfield.drop2048.libraries.progress.daily.DailyStreak
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.BarChart
import com.dangerfield.drop2048.libraries.ui.components.BarChartEntry
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.game.BoardScale
import com.dangerfield.drop2048.libraries.ui.components.game.GameSection
import com.dangerfield.drop2048.libraries.ui.components.game.LocalBoardScale
import com.dangerfield.drop2048.libraries.ui.components.game.ScoreCounter
import com.dangerfield.drop2048.libraries.ui.components.game.StatLabel
import com.dangerfield.drop2048.libraries.ui.components.game.StatPanel
import com.dangerfield.drop2048.libraries.ui.components.game.StatReadout
import com.dangerfield.drop2048.libraries.ui.components.game.Tile
import com.dangerfield.drop2048.libraries.ui.components.header.TopBar
import com.dangerfield.drop2048.libraries.ui.components.streak.StreakStop
import com.dangerfield.drop2048.libraries.ui.components.streak.StreakStopState
import com.dangerfield.drop2048.libraries.ui.components.streak.StreakTrack
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.screenContentPadding
import com.dangerfield.drop2048.libraries.ui.system.color.GameColors
import com.dangerfield.drop2048.libraries.ui.system.deepFace
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.Radii
import com.dangerfield.drop2048.system.VerticalSpacerD1000
import com.dangerfield.drop2048.system.VerticalSpacerD200
import com.dangerfield.drop2048.system.VerticalSpacerD500
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.daily_title
import drop2048.libraries.resources.generated.resources.stats_average
import drop2048.libraries.resources.generated.resources.stats_best
import drop2048.libraries.resources.generated.resources.stats_bests
import drop2048.libraries.resources.generated.resources.stats_blocks_placed
import drop2048.libraries.resources.generated.resources.stats_daily_best_streak
import drop2048.libraries.resources.generated.resources.stats_daily_streak
import drop2048.libraries.resources.generated.resources.stats_days
import drop2048.libraries.resources.generated.resources.stats_empty
import drop2048.libraries.resources.generated.resources.stats_highest_tier
import drop2048.libraries.resources.generated.resources.stats_lifetime
import drop2048.libraries.resources.generated.resources.stats_lifetime_bursts
import drop2048.libraries.resources.generated.resources.stats_longest_cascade
import drop2048.libraries.resources.generated.resources.stats_most_bursts
import drop2048.libraries.resources.generated.resources.stats_none_yet
import drop2048.libraries.resources.generated.resources.stats_one_day
import drop2048.libraries.resources.generated.resources.stats_playtime
import drop2048.libraries.resources.generated.resources.stats_recent
import drop2048.libraries.resources.generated.resources.stats_runs_played
import drop2048.libraries.resources.generated.resources.stats_title
import drop2048.libraries.resources.generated.resources.stats_total_merges
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * SPEC 15's numbers, in the order a player asks for them: the one they came to
 * see, the shape of their recent runs, then what they have ever done.
 *
 * A pure render of [StatsState]. Every value on it was folded out of
 * `run_record` before it got here — nothing on this screen adds anything up.
 *
 * ### It is drawn as a HUD rather than as a settings list (C14)
 *
 * The page used to be `SectionCard` + `SummaryRow`: flat fills on role tokens
 * with the label and the value in the same face, which is the shape a
 * preferences screen has. C3c gave it the dark palette and that fixed the colour
 * clash without touching the form, so it still read as a different product from
 * the board. Everything here is now the game's own vocabulary — chunky panels
 * with a hard offset shadow and an inset top highlight, Fredoka numerals in
 * fixed-width slots, the HUD's quiet uppercase labels — and the one number that
 * is *also a tier* is drawn as an actual tile in that tier's colour.
 *
 * ### Three questions, three groups
 *
 * Grouping matters more here than density. **Bests** is "how good have I ever
 * been", **Lifetime** is "how much have I played", and **Daily** is a walk over
 * `daily_result` rather than a fold over `run_record` at all — putting its two
 * numbers beside the lifetime totals would imply a relationship the fold does not
 * have.
 */
@Composable
fun StatsScreen(
    state: StatsState,
    onAction: (StatsAction) -> Unit,
) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.stats_title),
                onNavigateBack = { onAction(StatsAction.Back) },
                scrollState = scrollState,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .screenContentPadding(paddingValues = padding),
            verticalArrangement = Arrangement.Top,
        ) {
            if (state.loading) return@Column

            if (!state.stats.hasRuns) {
                EmptyStats()
                return@Column
            }

            Headline(state.stats)
            VerticalSpacerD1000()
            RecentRuns(state.stats)
            VerticalSpacerD1000()
            Bests(state.stats)
            VerticalSpacerD1000()
            Lifetime(state.stats)
            VerticalSpacerD1000()
            Daily(state.streak)
            VerticalSpacerD1000()
        }
    }
}

@Composable
private fun EmptyStats() {
    VerticalSpacerD1000()
    Text(
        text = stringResource(Res.string.stats_empty),
        typography = AppTheme.typography.Body.B600,
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The best score gets the counter the HUD uses, counting from zero, because it
 * is the number the page is about and a player who has just beaten it should
 * watch it arrive.
 *
 * Runs played and the average sit under it on their own plates rather than as
 * two more rows. They are the second and third numbers anyone looks for, and a
 * plate is what says so without needing a heading.
 */
@Composable
private fun Headline(stats: RunStats) {
    VerticalSpacerD500()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        StatLabel(text = stringResource(Res.string.stats_best).uppercase())
        VerticalSpacerD200()
        ScoreCounter(score = stats.bestScore.toInt(), countFrom = 0)
    }
    VerticalSpacerD1000()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimension.D500),
    ) {
        StatPanel(
            label = stringResource(Res.string.stats_runs_played).uppercase(),
            value = stats.runsPlayed.toString(),
            modifier = Modifier.weight(1f),
        )
        StatPanel(
            label = stringResource(Res.string.stats_average).uppercase(),
            value = stats.averageScore.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Oldest run on the left, so the bars read left to right like every other
 * timeline. [RunStats.recentScores] arrives newest-first because that is the
 * order the query wants; reversing it here keeps that decision out of the fold.
 *
 * **No axis and no guide lines.** A tick ladder and a grid are what a chart wears
 * when the reader has to take a value off it, and nothing here asks that — the
 * exact best and the exact average are already printed above in a larger face.
 * What is left is the shape of the last five runs, and that reads better as five
 * chunky blocks than as a plotted series. The best of the five takes the accent
 * violet, so the tallest bar is also the one that is coloured rather than merely
 * the one that is tall.
 */
@Composable
private fun RecentRuns(stats: RunStats) {
    val scores = stats.recentScores.reversed()
    val bestIndex = scores.indices.maxByOrNull { scores[it] } ?: 0

    GameSection(title = stringResource(Res.string.stats_recent)) {
        BarChart(
            entries = scores.mapIndexed { index, score ->
                BarChartEntry<Int, Nothing>(
                    id = index,
                    value = score.toFloat(),
                    label = (index + 1).toString(),
                )
            },
            selectedEntryId = bestIndex,
            chartHeight = ChartHeight,
            minBarHeightFraction = MinBarFraction,
            drawGuides = false,
            axisContent = {},
            barContent = { _, isSelected, _, _, sizedModifier ->
                Box(
                    sizedModifier.deepFace(
                        color = if (isSelected) GameColors.AccentViolet else GameColors.ControlRaised,
                        shape = Radii.R400,
                        depth = BarDepth,
                        shadow = if (isSelected) {
                            GameColors.AccentVioletShadow
                        } else {
                            GameColors.ControlShadow
                        },
                    ),
                )
            },
        )
    }
}

/**
 * How good the player has ever been.
 *
 * **Highest tier is drawn as the tile it is.** It is the one number on this page
 * that names an object the player has held on the board, and printing `1024` as
 * a digit string throws away the fact that they know exactly what that block
 * looks like. Drawn from the live [com.dangerfield.drop2048.libraries.ui.system.color.BlockPalette],
 * so a player on the tritanopia ramp sees their own 1024 rather than the default
 * one.
 *
 * Its own [BoardScale] rather than the board's: the tile is a piece of content in
 * a panel here, and at the board's 70dp cell it would be half the panel wide.
 */
@Composable
private fun Bests(stats: RunStats) {
    GameSection(title = stringResource(Res.string.stats_bests)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatLabel(text = stringResource(Res.string.stats_highest_tier).uppercase())
            if (stats.highestTier > 0) {
                CompositionLocalProvider(LocalBoardScale provides TierTileScale) {
                    Tile(value = stats.highestTier)
                }
            } else {
                Text(
                    text = stringResource(Res.string.stats_none_yet),
                    typography = AppTheme.typography.Body.B500,
                    color = AppTheme.colors.textSecondary,
                )
            }
        }
        StatReadout(
            label = stringResource(Res.string.stats_longest_cascade).uppercase(),
            value = stats.longestCascade.toString(),
        )
        StatReadout(
            label = stringResource(Res.string.stats_most_bursts).uppercase(),
            value = stats.mostBurstsInARun.toString(),
        )
    }
}

@Composable
private fun Lifetime(stats: RunStats) {
    GameSection(title = stringResource(Res.string.stats_lifetime)) {
        StatReadout(
            label = stringResource(Res.string.stats_total_merges).uppercase(),
            value = stats.totalMerges.toString(),
        )
        StatReadout(
            label = stringResource(Res.string.stats_blocks_placed).uppercase(),
            value = stats.totalBlocksPlaced.toString(),
        )
        StatReadout(
            label = stringResource(Res.string.stats_lifetime_bursts).uppercase(),
            value = stats.lifetimeBursts.toString(),
        )
        StatReadout(
            label = stringResource(Res.string.stats_playtime).uppercase(),
            value = formatPlaytime(stats.totalPlaytimeMs),
        )
    }
}

/**
 * SPEC 15's Daily streak, current and best, over SPEC 14's milestone track.
 *
 * Drawn even at zero, unlike the rest of the page before the first run, because
 * a streak of zero on a mode that exists is a true and actionable statement —
 * whereas C4 left this section out entirely, and was right to, when the mode did
 * not exist.
 *
 * The track earns its place by answering the question the two numbers do not:
 * how far to the next milestone. The milestones are cosmetic and pay nothing
 * (D19), so nothing here is captioned as a reward.
 */
@Composable
private fun Daily(streak: DailyStreak) {
    GameSection(title = stringResource(Res.string.daily_title)) {
        StreakTrack(stops = streakStops(streak.current))
        StatReadout(
            label = stringResource(Res.string.stats_daily_streak).uppercase(),
            value = daysLabel(streak.current),
        )
        StatReadout(
            label = stringResource(Res.string.stats_daily_best_streak).uppercase(),
            value = daysLabel(streak.best),
        )
    }
}

@Composable
private fun streakStops(current: Int): List<StreakStop> = StreakMilestones.map { day ->
    StreakStop(
        label = day.toString(),
        description = daysLabel(day),
        state = if (current >= day) StreakStopState.Reached else StreakStopState.Ahead,
    )
}

@Composable
private fun daysLabel(days: Int): String = if (days == 1) {
    stringResource(Res.string.stats_one_day)
} else {
    stringResource(Res.string.stats_days, days)
}

/** SPEC 14's milestones. */
private val StreakMilestones = listOf(3, 7, 14, 30)

/**
 * Small enough that the tile is a value in a row rather than a board fragment,
 * and its em is scaled with it so the numeral stays in proportion.
 */
private val TierTileScale = BoardScale(em = 12.dp, cell = 48.dp)

private val ChartHeight = 150.dp
private val BarDepth = 4.dp

/** Tall enough that the shortest bar still has a face above its own shadow. */
private const val MinBarFraction = 0.08f

@Preview
@Composable
private fun StatsScreenPreview() {
    PreviewContent {
        StatsScreen(
            state = StatsState(
                loading = false,
                stats = RunStats(
                    runsPlayed = 14,
                    bestScore = 18_240,
                    averageScore = 6_112,
                    highestTier = 1024,
                    totalMerges = 1_284,
                    totalBlocksPlaced = 2_610,
                    longestCascade = 7,
                    mostBurstsInARun = 2,
                    lifetimeBursts = 5,
                    totalPlaytimeMs = 9_240_000,
                    recentScores = listOf(18_240L, 4_010L, 9_120L, 2_400L, 6_780L),
                ),
                streak = DailyStreak(current = 4, best = 11),
            ),
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun StatsScreenEmptyPreview() {
    PreviewContent {
        StatsScreen(state = StatsState(loading = false), onAction = {})
    }
}
