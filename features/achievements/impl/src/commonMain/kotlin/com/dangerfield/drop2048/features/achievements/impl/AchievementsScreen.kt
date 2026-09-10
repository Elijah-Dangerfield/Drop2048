package com.dangerfield.drop2048.features.achievements.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import com.dangerfield.drop2048.features.achievements.AchievementCopy
import com.dangerfield.drop2048.libraries.achievements.AchievementId
import com.dangerfield.drop2048.libraries.achievements.Achievements
import com.dangerfield.drop2048.libraries.ui.Border
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.border
import com.dangerfield.drop2048.libraries.ui.bounceClick
import com.dangerfield.drop2048.libraries.ui.components.ListItem
import com.dangerfield.drop2048.libraries.ui.components.NonLazyVerticalGrid
import com.dangerfield.drop2048.libraries.ui.components.ProgressRow
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonPrimary
import com.dangerfield.drop2048.libraries.ui.components.dialog.Dialog
import com.dangerfield.drop2048.libraries.ui.components.header.TopBar
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.screenContentPadding
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.Dimension
import com.dangerfield.drop2048.system.Radii
import com.dangerfield.drop2048.system.VerticalSpacerD300
import com.dangerfield.drop2048.system.VerticalSpacerD500
import com.dangerfield.drop2048.system.VerticalSpacerD800
import com.dangerfield.drop2048.system.clip
import com.dangerfield.drop2048.system.thenIf
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.achievements_close
import drop2048.libraries.resources.generated.resources.achievements_earned
import drop2048.libraries.resources.generated.resources.achievements_leaderboards
import drop2048.libraries.resources.generated.resources.achievements_locked
import drop2048.libraries.resources.generated.resources.achievements_progress
import drop2048.libraries.resources.generated.resources.achievements_summary
import drop2048.libraries.resources.generated.resources.achievements_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Every badge in the catalog, earned or not.
 *
 * Locked badges are **shown**, with how far along they are. A grid that only
 * held what a player already has is a trophy case; the ones they have not got
 * are the reason to open it. Nothing here is a mystery tile — every one of SPEC
 * 15's twenty-four is a thing to go and do, and a "???" over a goal is a goal
 * nobody can aim at.
 *
 * Drawn shelf by shelf. Twenty-four tiles is past the point where catalog order
 * carries the grouping on its own, and an unlabelled wall of tiles is a bag
 * rather than a set of ladders.
 *
 * Every colour is a design-system token. The meta screens and the board are
 * being brought onto one theme in a concurrent chunk, and a hardcoded palette
 * here would be a screen that misses it.
 *
 * The detail dialog renders nothing in place: the design system's `Dialog`
 * registers itself with the host mounted in `App.kt` and is drawn over the whole
 * window, top bar included.
 */
@Composable
fun AchievementsScreen(
    state: AchievementsState,
    onAction: (AchievementsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.achievements_title),
                onNavigateBack = { onAction(AchievementsAction.Back) },
                scrollState = scrollState,
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (!state.loading) {
                BadgeGrid(
                    state = state,
                    onAction = onAction,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .screenContentPadding(padding),
                )
            }

            state.selected?.let { badge ->
                BadgeDetailDialog(
                    badge = badge,
                    onDismiss = { onAction(AchievementsAction.CloseDetail) },
                )
            }
        }
    }
}

@Composable
private fun BadgeGrid(
    state: AchievementsState,
    onAction: (AchievementsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        VerticalSpacerD500()

        Text(
            text = stringResource(
                Res.string.achievements_summary,
                state.earnedCount.toString(),
                state.totalCount.toString(),
            ),
            typography = AppTheme.typography.Heading.H600,
        )

        if (state.leaderboardsOfferable) {
            VerticalSpacerD500()
            ListItem(
                headlineContent = { Text(stringResource(Res.string.achievements_leaderboards)) },
                onClick = { onAction(AchievementsAction.OpenLeaderboards) },
                modifier = Modifier
                    .clip(Radii.Card)
                    .background(AppTheme.colors.surfacePrimary.color),
            )
        }

        state.sections.forEach { section ->
            VerticalSpacerD800()

            Text(
                text = stringResource(AchievementCopy.groupName(section.group)),
                typography = AppTheme.typography.Heading.H700,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            VerticalSpacerD300()

            NonLazyVerticalGrid(
                columns = GridColumns,
                data = section.badges,
                verticalSpacing = Dimension.D400,
                horizontalSpacing = Dimension.D400,
            ) { _, badge ->
                BadgeTile(badge = badge, onClick = { onAction(AchievementsAction.Select(badge.id)) })
            }
        }

        VerticalSpacerD800()
    }
}

/**
 * One card in the grid.
 *
 * Locked badges keep their shape and lose their colour, so the grid reads as one
 * set at a glance rather than as two. The earned outline is in the card's own
 * shape and sits *outside* the clip: a border drawn inside the clip has its
 * outer edge shaved off by it.
 *
 * Colour separates the two states weakly — the locked surface and the one beside
 * it are a step apart on the ramp — so the glyph carries it: earned badges are
 * lit, the rest are faded. The alpha is read inside `graphicsLayer` rather than
 * in composition, per the design system's rule.
 *
 * The progress bar is drawn only on a badge that is genuinely part-way there. A
 * bar sitting at zero under fifteen unstarted cards is furniture that says
 * nothing.
 */
@Composable
private fun BadgeTile(badge: Badge, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .fillMaxWidth()
            .aspectRatio(1f)
            .thenIf(badge.unlocked) { border(Border(AppTheme.colors.accentPrimary)) }
            .clip(Radii.Card)
            .background(
                if (badge.unlocked) {
                    AppTheme.colors.surfacePrimary.color
                } else {
                    AppTheme.colors.surfaceSecondary.color
                },
            )
            .padding(Dimension.D300),
    ) {
        Text(
            text = badge.face(),
            typography = AppTheme.typography.Display.D1000,
            modifier = Modifier.graphicsLayer {
                alpha = if (badge.unlocked) 1f else LockedGlyphAlpha
            },
        )
        VerticalSpacerD300()
        Text(
            text = badge.displayName(),
            typography = AppTheme.typography.Caption.C300,
            color = if (badge.unlocked) AppTheme.colors.text else AppTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = MaxNameLines,
        )
        if (!badge.unlocked && badge.progress > 0f) {
            VerticalSpacerD300()
            ProgressRow(
                progressPercent = badge.progress,
                modifier = Modifier.padding(horizontal = Dimension.D300),
            )
        }
    }
}

/**
 * The badge, full size, with what it takes to earn it.
 *
 * The design system's [Dialog], not a hand-rolled scrim, which is what buys the
 * entrance animation, the back-press handling and a scrim that covers the top
 * bar rather than stopping at its own sibling.
 */
@Composable
private fun BadgeDetailDialog(badge: Badge, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimension.D500),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = badge.face(), typography = AppTheme.typography.Display.D1400)
            Text(
                text = badge.displayName(),
                typography = AppTheme.typography.Heading.H700,
                textAlign = TextAlign.Center,
            )
            Text(
                text = badge.displayDescription(),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (badge.unlocked) {
                    stringResource(Res.string.achievements_earned)
                } else {
                    stringResource(Res.string.achievements_locked)
                },
                typography = AppTheme.typography.Label.L400,
                color = if (badge.unlocked) {
                    AppTheme.colors.accentPrimary
                } else {
                    AppTheme.colors.textSecondary
                },
            )
            if (!badge.unlocked) {
                Text(
                    text = stringResource(
                        Res.string.achievements_progress,
                        badge.current.toString(),
                        badge.target.toString(),
                    ),
                    typography = AppTheme.typography.Body.B600,
                )
                if (badge.progress > 0f) {
                    ProgressRow(progressPercent = badge.progress)
                }
            }
            ButtonPrimary(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.achievements_close))
            }
        }
    }
}

private fun Badge.face(): String = AchievementCopy.glyph(id)

@Composable
private fun Badge.displayName(): String = stringResource(AchievementCopy.name(id))

/**
 * The target is passed in rather than written into the copy, so a rung derived
 * from SPEC 7's coefficients cannot end up described by a stale number.
 */
@Composable
private fun Badge.displayDescription(): String =
    stringResource(AchievementCopy.description(id), target.toString())

/** Three across fits a two-word badge name on the narrowest phone we support. */
private const val GridColumns = 3

private const val MaxNameLines = 2

/** Faded, not hidden: the shape of an unearned badge is half the invitation. */
private const val LockedGlyphAlpha = 0.35f

@Preview
@Composable
private fun AchievementsScreenPreview() {
    PreviewContent {
        AchievementsScreen(state = previewState(), onAction = {})
    }
}

@Preview
@Composable
private fun BadgeDetailPreview() {
    PreviewContent {
        AchievementsScreen(
            state = previewState().copy(selectedId = AchievementId.ChainOfFive),
            onAction = {},
        )
    }
}

internal fun previewState(earned: Set<AchievementId> = DefaultEarned): AchievementsState =
    AchievementsState(
        loading = false,
        badges = Achievements.catalog.map { achievement ->
            val unlocked = achievement.id in earned
            Badge(
                id = achievement.id,
                group = Achievements.groupOf(achievement.id),
                unlocked = unlocked,
                progress = if (unlocked) 1f else PreviewProgress.getOrElse(achievement.id) { 0f },
                current = if (unlocked) {
                    achievement.target
                } else {
                    (achievement.target * PreviewProgress.getOrElse(achievement.id) { 0f }).toLong()
                },
                target = achievement.target,
            )
        },
    )

private val DefaultEarned = setOf(
    AchievementId.FirstMerge,
    AchievementId.SixtyFour,
    AchievementId.FirstFigures,
    AchievementId.OneHour,
)

private val PreviewProgress = mapOf(
    AchievementId.ChainOfFive to 0.6f,
    AchievementId.SolidRun to 0.45f,
    AchievementId.LevelTwenty to 0.75f,
    AchievementId.FiveHours to 0.2f,
)
