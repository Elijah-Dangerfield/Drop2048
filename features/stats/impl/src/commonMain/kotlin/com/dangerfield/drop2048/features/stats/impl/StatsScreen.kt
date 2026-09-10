package com.dangerfield.drop2048.features.stats.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.dangerfield.drop2048.libraries.progress.RunStats
import com.dangerfield.drop2048.libraries.progress.daily.DailyStreak
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.BarChart
import com.dangerfield.drop2048.libraries.ui.components.BarChartEntry
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.SectionCard
import com.dangerfield.drop2048.libraries.ui.components.SummaryRow
import com.dangerfield.drop2048.libraries.ui.components.game.ScoreCounter
import com.dangerfield.drop2048.libraries.ui.components.header.TopBar
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.screenContentPadding
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.VerticalSpacerD1000
import com.dangerfield.drop2048.system.VerticalSpacerD500
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.daily_title
import drop2048.libraries.resources.generated.resources.stats_average
import drop2048.libraries.resources.generated.resources.stats_best
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
 * see, the shape of their recent runs, then the lifetime totals.
 *
 * A pure render of [StatsState]. Every value on it was folded out of
 * `run_record` before it got here — nothing on this screen adds anything up.
 *
 * The Daily streak SPEC 15 lists arrived with C6, in its own section rather than
 * folded into Lifetime: everything in Lifetime is a total over `run_record` and
 * these two are a walk over `daily_result`, so putting them side by side would
 * imply a relationship the fold does not have.
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
 */
@Composable
private fun Headline(stats: RunStats) {
    VerticalSpacerD500()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.stats_best).uppercase(),
            typography = AppTheme.typography.Label.L500,
            color = AppTheme.colors.textSecondary,
        )
        ScoreCounter(score = stats.bestScore.toInt(), countFrom = 0)
    }
    VerticalSpacerD500()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimension.D500),
    ) {
        SummaryRow(
            label = stringResource(Res.string.stats_runs_played),
            value = stats.runsPlayed.toString(),
            modifier = Modifier.weight(1f),
        )
        SummaryRow(
            label = stringResource(Res.string.stats_average),
            value = stats.averageScore.toString(),
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Oldest run on the left, so the bars read left to right like every other
 * timeline. [RunStats.recentScores] arrives newest-first because that is the
 * order the query wants; reversing it here keeps that decision out of the fold.
 */
@Composable
private fun RecentRuns(stats: RunStats) {
    SectionCard(title = stringResource(Res.string.stats_recent)) {
        BarChart(
            entries = stats.recentScores.reversed().mapIndexed { index, score ->
                BarChartEntry<Int, Nothing>(
                    id = index,
                    value = score.toFloat(),
                    label = (index + 1).toString(),
                )
            },
        )
    }
}

/**
 * SPEC 15's Daily streak, current and best.
 *
 * Drawn even at zero, unlike the rest of the page before the first run, because
 * a streak of zero on a mode that exists is a true and actionable statement —
 * whereas C4 left this section out entirely, and was right to, when the mode did
 * not exist.
 */
@Composable
private fun Daily(streak: DailyStreak) {
    SectionCard(title = stringResource(Res.string.daily_title)) {
        SummaryRow(
            label = stringResource(Res.string.stats_daily_streak),
            value = daysLabel(streak.current),
        )
        SummaryRow(
            label = stringResource(Res.string.stats_daily_best_streak),
            value = daysLabel(streak.best),
        )
    }
}

@Composable
private fun daysLabel(days: Int): String = if (days == 1) {
    stringResource(Res.string.stats_one_day)
} else {
    stringResource(Res.string.stats_days, days)
}

@Composable
private fun Lifetime(stats: RunStats) {
    SectionCard(title = stringResource(Res.string.stats_lifetime)) {
        SummaryRow(
            label = stringResource(Res.string.stats_highest_tier),
            value = stats.highestTier.toString(),
        )
        SummaryRow(
            label = stringResource(Res.string.stats_total_merges),
            value = stats.totalMerges.toString(),
        )
        SummaryRow(
            label = stringResource(Res.string.stats_blocks_placed),
            value = stats.totalBlocksPlaced.toString(),
        )
        SummaryRow(
            label = stringResource(Res.string.stats_longest_cascade),
            value = stats.longestCascade.toString(),
        )
        SummaryRow(
            label = stringResource(Res.string.stats_most_bursts),
            value = stats.mostBurstsInARun.toString(),
        )
        SummaryRow(
            label = stringResource(Res.string.stats_lifetime_bursts),
            value = stats.lifetimeBursts.toString(),
        )
        SummaryRow(
            label = stringResource(Res.string.stats_playtime),
            value = formatPlaytime(stats.totalPlaytimeMs),
        )
    }
}

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
