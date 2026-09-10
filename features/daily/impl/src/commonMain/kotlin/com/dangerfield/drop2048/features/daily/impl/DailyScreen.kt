package com.dangerfield.drop2048.features.daily.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.dangerfield.drop2048.libraries.progress.daily.DailyResult
import com.dangerfield.drop2048.libraries.progress.daily.DailyStatus
import com.dangerfield.drop2048.libraries.progress.daily.DailyStreak
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.SectionCard
import com.dangerfield.drop2048.libraries.ui.components.SummaryRow
import com.dangerfield.drop2048.libraries.ui.components.button.Button
import com.dangerfield.drop2048.libraries.ui.components.game.ScoreCounter
import com.dangerfield.drop2048.libraries.ui.components.header.TopBar
import com.dangerfield.drop2048.libraries.ui.components.streak.StreakStop
import com.dangerfield.drop2048.libraries.ui.components.streak.StreakStopState
import com.dangerfield.drop2048.libraries.ui.components.streak.StreakTrack
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.screenContentPadding
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.VerticalSpacerD1000
import com.dangerfield.drop2048.system.VerticalSpacerD500
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.daily_attempts_remaining
import drop2048.libraries.resources.generated.resources.daily_completed
import drop2048.libraries.resources.generated.resources.daily_disabled
import drop2048.libraries.resources.generated.resources.daily_leaderboard
import drop2048.libraries.resources.generated.resources.daily_leaderboard_soon
import drop2048.libraries.resources.generated.resources.daily_no_attempts
import drop2048.libraries.resources.generated.resources.daily_play
import drop2048.libraries.resources.generated.resources.daily_resets_in
import drop2048.libraries.resources.generated.resources.daily_resume
import drop2048.libraries.resources.generated.resources.daily_retry
import drop2048.libraries.resources.generated.resources.daily_retry_unavailable
import drop2048.libraries.resources.generated.resources.daily_streak
import drop2048.libraries.resources.generated.resources.daily_streak_best
import drop2048.libraries.resources.generated.resources.daily_streak_days
import drop2048.libraries.resources.generated.resources.daily_streak_milestone
import drop2048.libraries.resources.generated.resources.daily_streak_next
import drop2048.libraries.resources.generated.resources.daily_streak_none
import drop2048.libraries.resources.generated.resources.daily_streak_one_day
import drop2048.libraries.resources.generated.resources.daily_subtitle
import drop2048.libraries.resources.generated.resources.daily_title
import drop2048.libraries.resources.generated.resources.daily_your_score
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * SPEC 14, in the order a player asks for it: can I play, what did I get, how
 * long is my streak, where do I stand.
 *
 * A pure render of [DailyState]. Every number on it was resolved against one
 * snapshot of the clock before it arrived, including the countdown — see
 * `DailyViewModel`.
 *
 * The leaderboard is a placeholder and says so in words. C9 owns platform
 * leaderboards; a card that pretended to be loading one would be a lie the
 * player cannot tell from a bug.
 */
@Composable
fun DailyScreen(
    state: DailyState,
    onAction: (DailyAction) -> Unit,
) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.daily_title),
                onNavigateBack = { onAction(DailyAction.Back) },
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

            if (!state.status.enabled) {
                Disabled()
                return@Column
            }

            Today(state = state, onAction = onAction)
            VerticalSpacerD1000()
            Streak(state.status.streak)
            VerticalSpacerD1000()
            LeaderboardPlaceholder()
            VerticalSpacerD1000()
        }
    }
}

@Composable
private fun Disabled() {
    VerticalSpacerD1000()
    Text(
        text = stringResource(Res.string.daily_disabled),
        typography = AppTheme.typography.Body.B600,
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The three states of a day, and they are mutually exclusive by construction
 * rather than by three flags a caller has to keep in agreement: untouched,
 * started and unfinished, finished.
 */
@Composable
private fun Today(state: DailyState, onAction: (DailyAction) -> Unit) {
    val status = state.status
    VerticalSpacerD500()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.daily_subtitle),
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        VerticalSpacerD500()

        if (status.completed) {
            Text(
                text = stringResource(Res.string.daily_your_score).uppercase(),
                typography = AppTheme.typography.Label.L500,
                color = AppTheme.colors.textSecondary,
            )
            ScoreCounter(score = (status.result?.score ?: 0).toInt(), countFrom = 0, grouped = true)
            VerticalSpacerD500()
            Text(
                text = stringResource(Res.string.daily_completed),
                typography = AppTheme.typography.Body.B600,
            )
        }

        VerticalSpacerD500()

        when {
            // Not `playable && inProgress`, which is a contradiction under the
            // one-attempt rule and so never drew: starting the attempt is what
            // spends it, so a run left in flight always has zero attempts left.
            // Resume is the one action a spent day still owes the player.
            status.enabled && status.inProgress ->
                Button(onClick = { onAction(DailyAction.Play) }) {
                    Text(stringResource(Res.string.daily_resume))
                }

            status.playable ->
                Button(onClick = { onAction(DailyAction.Play) }) {
                    Text(stringResource(Res.string.daily_play))
                }

            status.retryOffered ->
                Button(onClick = { onAction(DailyAction.Retry) }) {
                    Text(stringResource(Res.string.daily_retry))
                }

            status.completed -> Unit

            else -> Text(
                text = stringResource(Res.string.daily_no_attempts),
                typography = AppTheme.typography.Body.B600,
                color = AppTheme.colors.textSecondary,
            )
        }

        if (state.lastRetry != null && !status.playable) {
            VerticalSpacerD500()
            Text(
                text = stringResource(Res.string.daily_retry_unavailable),
                typography = AppTheme.typography.Caption.C400,
                color = AppTheme.colors.textSecondary,
            )
        }

        VerticalSpacerD500()
        Text(
            text = stringResource(
                Res.string.daily_attempts_remaining,
                status.attemptsRemaining,
                status.attemptsAllowed,
            ),
            typography = AppTheme.typography.Caption.C400,
            color = AppTheme.colors.textSecondary,
        )
        Text(
            text = stringResource(Res.string.daily_resets_in, formatResetsIn(status.resetsIn)),
            typography = AppTheme.typography.Caption.C400,
            color = AppTheme.colors.textSecondary,
        )
    }
}

/**
 * SPEC 14's milestones at 3, 7, 14 and 30 days.
 *
 * The headline is the number and the track is the distance to the next one,
 * because those are two different questions and a single row of dots answers
 * neither well.
 *
 * **Milestones pay nothing** (decision D19). Coins and cosmetics are both cut
 * from v1, so a milestone is celebratory copy and a filled dot and the copy here
 * says exactly that. It used to read "N more for the next reward", which promised
 * a payout the app has no way to make.
 */
@Composable
private fun Streak(streak: DailyStreak) {
    SectionCard(title = stringResource(Res.string.daily_streak)) {
        if (streak.current == 0) {
            Text(
                text = stringResource(Res.string.daily_streak_none),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )
        } else {
            Text(
                text = stringResource(
                    if (streak.milestone != null) {
                        Res.string.daily_streak_milestone
                    } else {
                        Res.string.daily_streak_days
                    },
                    streak.current,
                ),
                typography = AppTheme.typography.Heading.H700,
            )
        }

        StreakTrack(stops = stopsFor(streak))

        streak.nextMilestone?.let { next ->
            Text(
                text = stringResource(Res.string.daily_streak_next, next - streak.current),
                typography = AppTheme.typography.Caption.C400,
                color = AppTheme.colors.textSecondary,
            )
        }

        SummaryRow(
            label = stringResource(Res.string.daily_streak_best),
            value = daysLabel(streak.best),
        )
    }
}

@Composable
private fun daysLabel(days: Int): String = if (days == 1) {
    stringResource(Res.string.daily_streak_one_day)
} else {
    stringResource(Res.string.daily_streak_days, days)
}

@Composable
private fun stopsFor(streak: DailyStreak): List<StreakStop> =
    DailyStreak.Milestones.map { day ->
        StreakStop(
            label = day.toString(),
            description = daysLabel(day),
            state = if (streak.current >= day) StreakStopState.Reached else StreakStopState.Ahead,
        )
    }

@Composable
private fun LeaderboardPlaceholder() {
    SectionCard(title = stringResource(Res.string.daily_leaderboard)) {
        Text(
            text = stringResource(Res.string.daily_leaderboard_soon),
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )
    }
}

@Preview
@Composable
private fun DailyNotPlayedPreview() {
    PreviewContent {
        DailyScreen(state = previewState(), onAction = {})
    }
}

@Preview
@Composable
private fun DailyCompletedPreview() {
    PreviewContent {
        DailyScreen(
            state = previewState(
                result = DailyResult(
                    date = PreviewDate,
                    seed = 42,
                    score = 18_240,
                    attemptsUsed = 1,
                    completed = true,
                ),
                streak = DailyStreak(current = 7, best = 12),
            ),
            onAction = {},
        )
    }
}

private fun previewState(
    result: DailyResult? = null,
    streak: DailyStreak = DailyStreak(current = 2, best = 9),
) = DailyState(
    loading = false,
    zone = TimeZone.UTC,
    status = DailyStatus(
        date = PreviewDate,
        seed = 42,
        result = result,
        streak = streak,
        attemptsAllowed = 1,
        retryOffered = false,
        resetsIn = 4.hours + 12.minutes,
        enabled = true,
    ),
)

private val PreviewDate = LocalDate(2026, 9, 9)
