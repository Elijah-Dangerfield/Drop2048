package com.dangerfield.drop2048.features.debug.impl

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dangerfield.drop2048.features.debug.DiagnosticsSettings
import com.dangerfield.drop2048.features.debug.PresetBoard
import com.dangerfield.drop2048.libraries.ads.AdPlacement
import com.dangerfield.drop2048.libraries.ads.AdShowResult
import com.dangerfield.drop2048.libraries.cascade.autoplay.Policy
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.ListItemAccessory
import com.dangerfield.drop2048.libraries.ui.components.ListSection
import com.dangerfield.drop2048.libraries.ui.components.ListSectionItem
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonPrimary
import com.dangerfield.drop2048.libraries.ui.components.button.ButtonSize
import com.dangerfield.drop2048.libraries.ui.components.header.TopBar
import com.dangerfield.drop2048.libraries.ui.components.text.OutlinedTextField
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.screenContentPadding
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.VerticalSpacerD1000
import com.dangerfield.drop2048.system.VerticalSpacerD500
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * SPEC 19's menu, drawn out of the design system's own list idiom.
 *
 * The handoff has nothing to say about a QA screen and inventing a look for one
 * would be the wrong kind of effort, so this is `ListSection` in the order a
 * tester works: what the board starts as, what the simulation does to it, what
 * the device owns, what seed it is on, what the overlay draws, and last the four
 * controls that destroy something.
 *
 * A pure render of [DebugState]. The only value it holds is the passphrase being
 * typed, which is deliberately not kept anywhere.
 */
@Composable
fun DebugScreen(
    state: DebugState,
    onAction: (DebugAction) -> Unit,
) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = DebugCopy.Title,
                onNavigateBack = { onAction(DebugAction.Back) },
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
            VerticalSpacerD500()
            if (state.needsPassphrase) {
                PassphraseGate(state, onAction)
            } else {
                SessionBanner()
                VerticalSpacerD1000()
                BoardSection(state, onAction)
                VerticalSpacerD1000()
                SimulationSection(state, onAction)
                VerticalSpacerD1000()
                RngSection(state, onAction)
                VerticalSpacerD1000()
                EconomySection(state, onAction)
                VerticalSpacerD1000()
                AdsSection(state.ads, onAction)
                VerticalSpacerD1000()
                DiagnosticsSection(state.diagnostics, onAction)
                VerticalSpacerD1000()
                TranscriptSection(state)
                VerticalSpacerD1000()
                StateSection(state, onAction)
                VerticalSpacerD1000()
            }
        }
    }
}

/**
 * The whole screen is behind this on a release build.
 *
 * Drawn instead of the menu rather than over it, so there is nothing underneath
 * to read the current seed off or to tap through a scrim onto.
 */
@Composable
private fun PassphraseGate(state: DebugState, onAction: (DebugAction) -> Unit) {
    var typed by remember { mutableStateOf("") }
    Text(text = DebugCopy.PassphraseTitle, typography = AppTheme.typography.Heading.H700)
    VerticalSpacerD500()
    Text(
        text = DebugCopy.PassphraseBody,
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.textSecondary,
    )
    VerticalSpacerD500()
    DebugTextField(
        label = DebugCopy.PassphraseLabel,
        value = typed,
        onValueChange = { typed = it },
    )
    if (state.passphraseRejected) {
        VerticalSpacerD500()
        Text(
            text = DebugCopy.PassphraseWrong,
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.danger,
        )
    }
    VerticalSpacerD500()
    ButtonPrimary(
        onClick = { onAction(DebugAction.SubmitPassphrase(typed)) },
        size = ButtonSize.Small,
    ) { Text(DebugCopy.PassphraseSubmit) }
}

/**
 * Says out loud that nothing is being recorded.
 *
 * The guard itself is in the game and cannot be switched off; this is here
 * because a silent guard is indistinguishable from a broken one, and the first
 * bug report this menu produces would otherwise be "my run did not save".
 */
@Composable
private fun SessionBanner() {
    ListSection(
        title = DebugCopy.SessionBannerTitle,
        items = listOf(
            ListSectionItem(
                headlineText = DebugCopy.SessionBannerBody,
                accessory = ListItemAccessory.None,
            ),
        ),
    )
}

@Composable
private fun BoardSection(state: DebugState, onAction: (DebugAction) -> Unit) {
    val overrides = state.overrides
    ListSection(
        title = DebugCopy.SectionBoard,
        supportingText = DebugCopy.Preset,
        items = (listOf(null) + PresetBoard.entries).map { preset ->
            ListSectionItem(
                headlineText = preset?.let { DebugCopy.presetLabel(it) } ?: DebugCopy.PresetNone,
                accessory = tickIf(overrides.preset == preset),
                onClick = { onAction(DebugAction.SetPreset(preset)) },
            )
        },
    )
    VerticalSpacerD500()
    ListSection(
        items = listOf(
            ListSectionItem(
                headlineText = DebugCopy.StartLevel,
                accessory = ListItemAccessory.Text(
                    text = overrides.startLevel?.toString() ?: DebugCopy.PresetNone,
                ),
                onClick = { onAction(DebugAction.SetStartLevel(nextLevel(overrides.startLevel))) },
            ),
            ListSectionItem(
                headlineText = DebugCopy.TickInterval,
                supportingText = DebugCopy.TickIntervalHint,
                accessory = ListItemAccessory.Text(
                    text = overrides.tickIntervalMs?.toString() ?: DebugCopy.TickIntervalDefault,
                ),
                onClick = { onAction(DebugAction.SetTickInterval(nextTick(overrides.tickIntervalMs))) },
            ),
        ),
    )
    VerticalSpacerD500()
    Text(text = DebugCopy.ForcedBlocks, typography = AppTheme.typography.Heading.H700)
    VerticalSpacerD500()
    Text(
        text = DebugCopy.ForcedBlocksHint,
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.textSecondary,
    )
    VerticalSpacerD500()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ChipGap),
    ) {
        DebugBlockChoices.forEach { block ->
            ButtonPrimary(
                onClick = { onAction(DebugAction.QueueBlock(block)) },
                size = ButtonSize.Small,
            ) { Text(DebugCopy.blockLabel(block)) }
        }
    }
    VerticalSpacerD500()
    ListSection(
        items = listOf(
            ListSectionItem(
                headlineText = overrides.forcedBlocks
                    .joinToString(" ") { DebugCopy.blockLabel(it) }
                    .ifEmpty { DebugCopy.QueueEmpty },
                accessory = ListItemAccessory.None,
            ),
            ListSectionItem(
                headlineText = DebugCopy.ClearQueue,
                onClick = { onAction(DebugAction.ClearQueue) },
            ),
        ),
    )
    VerticalSpacerD500()
    Text(
        text = if (overrides.changesTheNextRun) "" else DebugCopy.NoOverrides,
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.textSecondary,
    )
    VerticalSpacerD500()
    Row(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
        ButtonPrimary(
            onClick = { onAction(DebugAction.StartForcedRun) },
            size = ButtonSize.Small,
        ) { Text(DebugCopy.StartRun) }
        ButtonPrimary(
            onClick = { onAction(DebugAction.ClearOverrides) },
            size = ButtonSize.Small,
        ) { Text(DebugCopy.ClearOverrides) }
    }
}

@Composable
private fun SimulationSection(state: DebugState, onAction: (DebugAction) -> Unit) {
    ListSection(
        title = DebugCopy.SectionSimulation,
        items = listOf(
            ListSectionItem(
                headlineText = DebugCopy.Invincible,
                supportingText = DebugCopy.InvincibleHint,
                accessory = ListItemAccessory.Switch(
                    checked = state.overrides.invincible,
                    onCheckedChange = { onAction(DebugAction.ToggleInvincible) },
                ),
                onClick = { onAction(DebugAction.ToggleInvincible) },
            ),
            ListSectionItem(
                headlineText = DebugCopy.FreezeTimer,
                supportingText = DebugCopy.FreezeTimerHint,
                accessory = ListItemAccessory.Switch(
                    checked = state.overrides.freezeTimer,
                    onCheckedChange = { onAction(DebugAction.ToggleFreezeTimer) },
                ),
                onClick = { onAction(DebugAction.ToggleFreezeTimer) },
            ),
        ),
    )
    VerticalSpacerD500()
    ListSection(
        supportingText = DebugCopy.SoakHint,
        items = Policy.All.map { policy ->
            ListSectionItem(
                headlineText = policy.name + if (policy.cheats) " (${DebugCopy.SoakCheats})" else "",
                onClick = { onAction(DebugAction.RunSoak(policy)) },
            )
        } + listOfNotNull(
            state.soak?.let { soak ->
                ListSectionItem(
                    headlineText = soakSummary(soak),
                    supportingText = soak.faults.joinToString().ifEmpty { null },
                    accessory = ListItemAccessory.None,
                )
            },
            if (state.soaking) {
                ListSectionItem(
                    headlineText = DebugCopy.SoakRunning,
                    accessory = ListItemAccessory.None,
                )
            } else {
                null
            },
        ),
    )
}

@Composable
private fun RngSection(state: DebugState, onAction: (DebugAction) -> Unit) {
    var typed by remember(state.overrides.seed) {
        mutableStateOf(state.overrides.seed?.toString().orEmpty())
    }
    Text(text = DebugCopy.SectionRng, typography = AppTheme.typography.Heading.H700)
    VerticalSpacerD500()
    Text(
        text = DebugCopy.SeedHint,
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.textSecondary,
    )
    VerticalSpacerD500()
    DebugTextField(
        label = DebugCopy.Seed,
        value = typed,
        onValueChange = {
            typed = it
            onAction(DebugAction.SetSeed(it.trim().toLongOrNull()))
        },
    )
    VerticalSpacerD500()
    Row(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
        ButtonPrimary(
            onClick = { onAction(DebugAction.RollSeed) },
            size = ButtonSize.Small,
        ) { Text(DebugCopy.RandomSeed) }
        ButtonPrimary(
            onClick = { onAction(DebugAction.CopySeed) },
            size = ButtonSize.Small,
            enabled = state.overrides.seed != null,
        ) { Text(DebugCopy.CopySeed) }
    }
}

@Composable
private fun EconomySection(state: DebugState, onAction: (DebugAction) -> Unit) {
    ListSection(
        title = DebugCopy.SectionEconomy,
        items = listOf(
            ListSectionItem(
                headlineText = DebugCopy.ProGranted,
                supportingText = DebugCopy.ProGrantedHint,
                accessory = ListItemAccessory.Switch(
                    checked = state.proGranted,
                    onCheckedChange = { onAction(DebugAction.SetProGranted(it)) },
                ),
                onClick = { onAction(DebugAction.SetProGranted(!state.proGranted)) },
            ),
            ListSectionItem(
                headlineText = DebugCopy.ResetPurchases,
                onClick = { onAction(DebugAction.ResetPurchases) },
            ),
            ListSectionItem(
                headlineText = DebugCopy.DailyStreak,
                accessory = ListItemAccessory.Text(text = state.dailyStreak.toString()),
            ),
            ListSectionItem(
                headlineText = DebugCopy.ClearDaily,
                supportingText = DebugCopy.ClearDailyHint,
                onClick = { onAction(DebugAction.ClearDailyToday) },
            ),
        ),
    )
}

/**
 * SPEC 19's ad tools: which network, what it answers, force one, and why the
 * last one did not appear.
 *
 * The order is the order a tester needs it in. The network switch is first
 * because everything below means something different depending on it; the gate
 * inputs are directly above the force buttons because the whole point is to read
 * them, press one, and watch the reason change.
 */
@Composable
private fun AdsSection(ads: AdToolsState, onAction: (DebugAction) -> Unit) {
    ListSection(
        title = DebugCopy.SectionAds,
        items = listOf(
            ListSectionItem(
                headlineText = DebugCopy.HouseAds,
                supportingText = if (ads.houseAdsAvailable) {
                    DebugCopy.HouseAdsHint
                } else {
                    DebugCopy.HouseAdsUnavailable
                },
                accessory = ListItemAccessory.Switch(
                    checked = ads.houseAdsSelected,
                    onCheckedChange = { onAction(DebugAction.SelectHouseAds(it)) },
                ),
                enabled = ads.houseAdsAvailable,
                onClick = { onAction(DebugAction.SelectHouseAds(!ads.houseAdsSelected)) },
            ),
            ListSectionItem(
                headlineText = DebugCopy.ForcedOutcome,
                supportingText = DebugCopy.ForcedOutcomeHint,
                accessory = ListItemAccessory.Text(
                    text = DebugCopy.outcomeLabel(ads.forcedOutcome),
                ),
                enabled = ads.houseAdsAvailable,
                onClick = {
                    onAction(DebugAction.SetForcedAdOutcome(nextOutcome(ads.forcedOutcome)))
                },
            ),
            ListSectionItem(
                headlineText = DebugCopy.ReportsReady,
                supportingText = DebugCopy.ReportsReadyHint,
                accessory = ListItemAccessory.Switch(
                    checked = ads.reportsReady,
                    onCheckedChange = { onAction(DebugAction.SetAdReportsReady(it)) },
                ),
                enabled = ads.houseAdsAvailable,
                onClick = { onAction(DebugAction.SetAdReportsReady(!ads.reportsReady)) },
            ),
        ),
    )
    VerticalSpacerD500()
    Text(text = DebugCopy.AdGateInputs, typography = AppTheme.typography.Heading.H700)
    VerticalSpacerD500()
    ads.snapshot?.let { snapshot ->
        DebugCopy.gateInputs(snapshot).forEach { line ->
            Text(
                text = line,
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )
        }
        VerticalSpacerD500()
        Text(
            text = snapshot.blockedReason ?: DebugCopy.AdWouldShow,
            typography = AppTheme.typography.Body.B500,
            color = if (snapshot.blockedReason == null) {
                AppTheme.colors.text
            } else {
                AppTheme.colors.danger
            },
        )
    }
    VerticalSpacerD500()
    ListSection(
        items = listOf(
            ListSectionItem(
                headlineText = DebugCopy.NoteRunFinished,
                supportingText = DebugCopy.NoteRunFinishedHint,
                onClick = { onAction(DebugAction.NoteRunFinished) },
            ),
            ListSectionItem(
                headlineText = DebugCopy.ShowInterstitial,
                supportingText = DebugCopy.ShowInterstitialHint,
                enabled = !ads.showing,
                onClick = { onAction(DebugAction.ShowInterstitialNow) },
            ),
        ) + AdPlacement.entries.map { placement ->
            ListSectionItem(
                headlineText = DebugCopy.placementLabel(placement),
                enabled = !ads.showing,
                onClick = { onAction(DebugAction.ShowRewardedNow(placement)) },
            )
        } + listOf(
            ListSectionItem(
                headlineText = DebugCopy.LastAdResult,
                accessory = ListItemAccessory.Text(
                    text = if (ads.showing) {
                        DebugCopy.AdShowing
                    } else {
                        ads.lastResult ?: DebugCopy.PresetNone
                    },
                ),
            ),
            ListSectionItem(
                headlineText = DebugCopy.ConfigOverrides,
                supportingText = DebugCopy.ConfigOverridesHint,
                onClick = { onAction(DebugAction.OpenConfigOverrides) },
            ),
        ),
    )
}

/**
 * Cycles the forced answer through every result the real network can give.
 *
 * `null` first, because drawing the placeholder is the interesting case and a
 * dial parked on a failure is how a tester ends up reporting that house ads do
 * not work.
 */
private fun nextOutcome(current: AdShowResult?): AdShowResult? {
    if (current == null) return AdShowResult.entries.first()
    val next = AdShowResult.entries.indexOf(current) + 1
    return AdShowResult.entries.getOrNull(next)
}

@Composable
private fun DiagnosticsSection(settings: DiagnosticsSettings, onAction: (DebugAction) -> Unit) {
    ListSection(
        title = DebugCopy.SectionDiagnostics,
        items = listOf(
            switchRow(DebugCopy.ShowFrameRate, settings.showFrameRate) {
                onAction(DebugAction.ToggleFrameRate)
            },
            switchRow(DebugCopy.ShowTick, settings.showTick) { onAction(DebugAction.ToggleTick) },
            switchRow(DebugCopy.ShowCoordinates, settings.showCellCoordinates) {
                onAction(DebugAction.ToggleCoordinates)
            },
            switchRow(DebugCopy.ShowMergeArrows, settings.showMergeArrows) {
                onAction(DebugAction.ToggleMergeArrows)
            },
            switchRow(DebugCopy.ShowTranscript, settings.showTranscript) {
                onAction(DebugAction.ToggleTranscript)
            },
        ),
    )
}

/**
 * The last resolution, here as well as on the overlay.
 *
 * On the overlay it answers "what just happened" while the board is still on
 * screen; here it answers the same question after the tester has left the run to
 * come and read it, which is what actually happens at 3am.
 */
@Composable
private fun TranscriptSection(state: DebugState) {
    Text(text = DebugCopy.SectionTranscript, typography = AppTheme.typography.Heading.H700)
    VerticalSpacerD500()
    if (state.transcript.isEmpty()) {
        Text(
            text = DebugCopy.TranscriptEmpty,
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = TranscriptHeight)
            .verticalScroll(rememberScrollState()),
    ) {
        state.transcript.forEach { line ->
            Text(
                text = "${line.step}  ${line.text}  +${line.points}",
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun StateSection(state: DebugState, onAction: (DebugAction) -> Unit) {
    ListSection(
        title = DebugCopy.SectionState,
        items = listOf(
            ListSectionItem(
                headlineText = DebugCopy.ResetTutorial,
                supportingText = DebugCopy.ResetTutorialHint,
                onClick = { onAction(DebugAction.ResetTutorial) },
            ),
            ListSectionItem(
                headlineText = DebugCopy.ResetLocalData,
                supportingText = DebugCopy.ResetLocalDataHint,
                onClick = { onAction(DebugAction.ResetAllLocalData) },
            ),
            ListSectionItem(
                headlineText = DebugCopy.DumpState,
                onClick = { onAction(DebugAction.DumpState) },
            ),
            ListSectionItem(
                headlineText = DebugCopy.CopyDump,
                enabled = state.stateDump != null,
                onClick = { onAction(DebugAction.CopyDump) },
            ),
            ListSectionItem(
                headlineText = DebugCopy.ForceCrash,
                supportingText = DebugCopy.ForceCrashHint,
                onClick = { onAction(DebugAction.ForceCrash) },
            ),
        ),
    )
    state.stateDump?.let { dump ->
        VerticalSpacerD500()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = TranscriptHeight)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = dump,
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
}

/** The design system's outlined field, with the one label the menu ever needs. */
@Composable
private fun DebugTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
    )
}

private fun switchRow(label: String, checked: Boolean, onToggle: () -> Unit) = ListSectionItem(
    headlineText = label,
    accessory = ListItemAccessory.Switch(checked = checked, onCheckedChange = { onToggle() }),
    onClick = onToggle,
)

private fun tickIf(selected: Boolean): ListItemAccessory =
    if (selected) ListItemAccessory.Text(text = SelectedMark) else ListItemAccessory.None

private fun soakSummary(soak: SoakSummary): String = buildString {
    append("level ${soak.level}, ")
    append("${soak.score} pts, ")
    append("${soak.drops} drops, ")
    append("${soak.merges} merges, ")
    append("${soak.bursts} bursts, ")
    append("deepest ${soak.deepestCascade}")
    if (soak.hitDropCap) append(", HIT THE DROP CAP")
}

/**
 * Cycles rather than opens a picker.
 *
 * A level picker with thirty entries is a scroll, and the levels a tester
 * actually wants are the band boundaries in SPEC 5.3 plus the two where specials
 * unlock. Tapping through eight is faster than finding one in thirty.
 */
private fun nextLevel(current: Int?): Int? {
    val index = LevelStops.indexOf(current)
    return if (index < 0) LevelStops.first() else LevelStops.getOrNull(index + 1)
}

private fun nextTick(current: Int?): Int? {
    val index = TickStops.indexOf(current)
    return if (index < 0) TickStops.first() else TickStops.getOrNull(index + 1)
}

/** SPEC 5.3's band boundaries, plus 5 and 8 where Wildcards and Bombs unlock. */
private val LevelStops = listOf(1, 4, 5, 7, 8, 10, 12, 13, 16, 19, 25)

private val TickStops = listOf(2000, 1000, 500, 250, 120, 90)

private const val SelectedMark = "on"
private val ChipGap = 8.dp
private val TranscriptHeight = 220.dp

@Preview
@Composable
private fun DebugScreenPreview() {
    PreviewContent {
        DebugScreen(state = DebugState(appVersion = "1.0.0 (1)"), onAction = {})
    }
}
