package com.dangerfield.drop2048.features.settings.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.dangerfield.drop2048.features.debug.DebugMenuLabels
import com.dangerfield.drop2048.features.debug.QaToolsLabels
import com.dangerfield.drop2048.features.settings.ControlScheme
import com.dangerfield.drop2048.features.settings.PlayerSettings
import com.dangerfield.drop2048.libraries.core.BuildInfo
import com.dangerfield.drop2048.libraries.core.isTesterBuild
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.ListItemAccessory
import com.dangerfield.drop2048.libraries.ui.components.ListSection
import com.dangerfield.drop2048.libraries.ui.components.ListSectionItem
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.game.BlockPaletteStrip
import com.dangerfield.drop2048.libraries.ui.components.header.TopBar
import com.dangerfield.drop2048.libraries.ui.components.icon.Icons
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.screenContentPadding
import com.dangerfield.drop2048.libraries.ui.system.HapticsSetting
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.VerticalSpacerD1000
import com.dangerfield.drop2048.system.VerticalSpacerD500
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.settings_achievements
import drop2048.libraries.resources.generated.resources.settings_achievements_hint
import drop2048.libraries.resources.generated.resources.settings_ad_consent
import drop2048.libraries.resources.generated.resources.settings_arrow_buttons
import drop2048.libraries.resources.generated.resources.settings_arrow_buttons_hint
import drop2048.libraries.resources.generated.resources.settings_credits
import drop2048.libraries.resources.generated.resources.settings_delete_data
import drop2048.libraries.resources.generated.resources.settings_delete_data_hint
import drop2048.libraries.resources.generated.resources.settings_feedback
import drop2048.libraries.resources.generated.resources.settings_ghost
import drop2048.libraries.resources.generated.resources.settings_ghost_hint
import drop2048.libraries.resources.generated.resources.settings_haptics
import drop2048.libraries.resources.generated.resources.settings_haptics_light
import drop2048.libraries.resources.generated.resources.settings_haptics_off
import drop2048.libraries.resources.generated.resources.settings_haptics_strong
import drop2048.libraries.resources.generated.resources.settings_install_id
import drop2048.libraries.resources.generated.resources.settings_install_id_copied
import drop2048.libraries.resources.generated.resources.settings_install_id_hint
import drop2048.libraries.resources.generated.resources.settings_large_numbers
import drop2048.libraries.resources.generated.resources.settings_large_numbers_hint
import drop2048.libraries.resources.generated.resources.settings_licenses
import drop2048.libraries.resources.generated.resources.settings_palette
import drop2048.libraries.resources.generated.resources.settings_palette_default
import drop2048.libraries.resources.generated.resources.settings_palette_deuteranopia
import drop2048.libraries.resources.generated.resources.settings_palette_high_contrast
import drop2048.libraries.resources.generated.resources.settings_palette_protanopia
import drop2048.libraries.resources.generated.resources.settings_palette_tritanopia
import drop2048.libraries.resources.generated.resources.settings_privacy_policy
import drop2048.libraries.resources.generated.resources.settings_pro_active
import drop2048.libraries.resources.generated.resources.settings_pro_hint
import drop2048.libraries.resources.generated.resources.settings_pro_inactive
import drop2048.libraries.resources.generated.resources.settings_pro_status
import drop2048.libraries.resources.generated.resources.settings_pro_upgrade
import drop2048.libraries.resources.generated.resources.settings_replay_tutorial
import drop2048.libraries.resources.generated.resources.settings_replay_tutorial_hint
import drop2048.libraries.resources.generated.resources.settings_reset_progress
import drop2048.libraries.resources.generated.resources.settings_reset_progress_hint
import drop2048.libraries.resources.generated.resources.settings_restore
import drop2048.libraries.resources.generated.resources.settings_restore_nothing
import drop2048.libraries.resources.generated.resources.settings_restore_restored
import drop2048.libraries.resources.generated.resources.settings_restore_unavailable
import drop2048.libraries.resources.generated.resources.settings_restore_working
import drop2048.libraries.resources.generated.resources.settings_section_about
import drop2048.libraries.resources.generated.resources.settings_section_accessibility
import drop2048.libraries.resources.generated.resources.settings_section_controls
import drop2048.libraries.resources.generated.resources.settings_section_data
import drop2048.libraries.resources.generated.resources.settings_section_game
import drop2048.libraries.resources.generated.resources.settings_section_privacy
import drop2048.libraries.resources.generated.resources.settings_section_pro
import drop2048.libraries.resources.generated.resources.settings_section_sound
import drop2048.libraries.resources.generated.resources.settings_sound_effects
import drop2048.libraries.resources.generated.resources.settings_terms
import drop2048.libraries.resources.generated.resources.settings_title
import drop2048.libraries.resources.generated.resources.settings_version
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * SPEC 11's settings list and SPEC 16's accessibility block.
 *
 * A pure render of [SettingsState]. Nothing here holds a value except the word
 * typed into the reset dialog, which is deliberately not persisted anywhere.
 *
 * **The design language is derived, not drawn.** The handoff lists settings under
 * "not designed yet — ask before inventing", so this is the design system's own
 * `ListSection` grouping, in the order a player looks for things: what they can
 * hear and feel, how they steer, what they can see, then the game, then the
 * paperwork, then the two controls that destroy something, then the version.
 * Destructive rows sit last and alone, which is the one placement decision here
 * that is not arbitrary.
 *
 * Every row is a `ListItem`, whose minimum height is 58dp — comfortably past the
 * 44dp floor, and the reason there is no touch-target special-casing on this
 * screen (SPEC 16's last bullet is about the three bottom buttons, which are
 * 60dp and live in `GameControlRow`).
 */
@Composable
fun SettingsScreen(
    state: SettingsState,
    onAction: (SettingsAction) -> Unit,
) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.settings_title),
                onNavigateBack = { onAction(SettingsAction.Back) },
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
            SoundSection(state.settings, onAction)
            VerticalSpacerD1000()
            ControlsSection(state.settings, onAction)
            VerticalSpacerD1000()
            AccessibilitySection(state.settings, onAction)
            VerticalSpacerD1000()
            GameSection(onAction)
            VerticalSpacerD1000()
            ProSection(state, onAction)
            VerticalSpacerD1000()
            LegalSection(state, onAction)
            VerticalSpacerD1000()
            DataSection(state, onAction)
            VerticalSpacerD1000()
            AboutSection(state, onAction)
            // On any tester build, with no seven taps and no passphrase. The
            // switch behind it hides the floating directive button, and a
            // control that is only reachable from behind the thing it controls
            // is not a control. See `QaToolsRoute`.
            if (BuildInfo.isTesterBuild) {
                VerticalSpacerD1000()
                QaSection(onAction)
            }
            if (state.settings.debugMenuUnlocked) {
                VerticalSpacerD1000()
                DebugSection(onAction)
            }
            VerticalSpacerD1000()
        }
    }

    SettingsDialogs(state = state, onAction = onAction)
}

@Composable
private fun SoundSection(settings: PlayerSettings, onAction: (SettingsAction) -> Unit) {
    ListSection(
        title = stringResource(Res.string.settings_section_sound),
        items = listOf(
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_sound_effects),
                accessory = ListItemAccessory.Switch(
                    checked = settings.soundEnabled,
                    onCheckedChange = { onAction(SettingsAction.ToggleSound) },
                ),
                onClick = { onAction(SettingsAction.ToggleSound) },
            ),
        ),
    )
    VerticalSpacerD500()
    ChoiceSection(
        title = stringResource(Res.string.settings_haptics),
        options = HapticsSetting.entries,
        selected = settings.haptics,
        label = { hapticsLabel(it) },
        onSelect = { onAction(SettingsAction.SetHaptics(it)) },
    )
}

/**
 * SPEC 6, as one switch rather than as three named schemes.
 *
 * Owner ruling, 2026-09-20: the game ships with no arrows and a way to bring
 * them back. [ControlScheme] already says all of that — `Drag` is the default
 * and `Both` is the arrows restored — so this is a presentation change and not a
 * fourth state. What the player is choosing is whether a row of buttons is on
 * their screen, which is a yes-or-no question and reads terribly as a list of
 * three nouns two of which are "drag the board" and "both".
 *
 * [ControlScheme.Buttons] has no control of its own and is deliberately still
 * honoured: it is drag *off*, it is the one scheme a player cannot get back to
 * by accident, and a stored one keeps working and shows this switch on. Adding a
 * second switch to reach it would put "turn dragging off" in front of every
 * player to serve the ones who have already stopped using it.
 *
 * The left-handed mirror used to hang off this switch and went with the owner
 * ruling of 2026-09-20. So did "ask before quitting" — quitting a run now always
 * confirms, which is a behaviour rather than a preference.
 */
@Composable
private fun ControlsSection(settings: PlayerSettings, onAction: (SettingsAction) -> Unit) {
    val arrowsShown = settings.controlScheme != ControlScheme.Drag
    val setArrows = { shown: Boolean ->
        onAction(SettingsAction.SetControlScheme(schemeWithArrows(shown, settings.controlScheme)))
    }
    ListSection(
        title = stringResource(Res.string.settings_section_controls),
        items = listOf(
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_arrow_buttons),
                supportingText = stringResource(Res.string.settings_arrow_buttons_hint),
                accessory = ListItemAccessory.Switch(
                    checked = arrowsShown,
                    onCheckedChange = setArrows,
                ),
                onClick = { setArrows(!arrowsShown) },
            ),
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_ghost),
                supportingText = stringResource(Res.string.settings_ghost_hint),
                accessory = ListItemAccessory.Switch(
                    checked = settings.ghostEnabled,
                    onCheckedChange = { onAction(SettingsAction.ToggleGhost) },
                ),
                onClick = { onAction(SettingsAction.ToggleGhost) },
            ),
        ),
    )
}

/**
 * Which scheme "arrows on screen" means, keeping [ControlScheme.Buttons] intact.
 *
 * A player who has drag switched off entirely is not told they have it back by a
 * switch that says "arrow buttons", so turning arrows on from `Buttons` leaves
 * `Buttons` alone. Turning them off is always `Drag`, because that is the one
 * scheme with no arrows in it.
 */
private fun schemeWithArrows(shown: Boolean, current: ControlScheme): ControlScheme = when {
    !shown -> ControlScheme.Drag
    current == ControlScheme.Buttons -> ControlScheme.Buttons
    else -> ControlScheme.Both
}

/**
 * SPEC 16, and the section this whole chunk exists for.
 *
 * The palette rows carry a live swatch of the ramp they select, because the name
 * of a colour-vision deficiency tells a player nothing about what their board is
 * about to look like.
 */
@Composable
private fun AccessibilitySection(settings: PlayerSettings, onAction: (SettingsAction) -> Unit) {
    ListSection(
        title = stringResource(Res.string.settings_section_accessibility),
        supportingText = stringResource(Res.string.settings_palette),
        items = BlockPaletteChoice.entries.map { choice ->
            ListSectionItem(
                headlineText = paletteLabel(choice),
                leadingContent = { BlockPaletteStrip(choice) },
                accessory = if (choice == settings.palette) {
                    ListItemAccessory.Icon(
                        icon = Icons.Check(paletteLabel(choice)),
                        tint = AppTheme.colors.accentPrimary,
                    )
                } else {
                    ListItemAccessory.None
                },
                onClick = { onAction(SettingsAction.SetPalette(choice)) },
            )
        },
    )
    VerticalSpacerD500()
    ListSection(
        items = listOf(
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_large_numbers),
                supportingText = stringResource(Res.string.settings_large_numbers_hint),
                accessory = ListItemAccessory.Switch(
                    checked = settings.largeNumbers,
                    onCheckedChange = { onAction(SettingsAction.ToggleLargeNumbers) },
                ),
                onClick = { onAction(SettingsAction.ToggleLargeNumbers) },
            ),
        ),
    )
}

@Composable
private fun GameSection(onAction: (SettingsAction) -> Unit) {
    ListSection(
        title = stringResource(Res.string.settings_section_game),
        items = listOf(
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_achievements),
                supportingText = stringResource(Res.string.settings_achievements_hint),
                onClick = { onAction(SettingsAction.OpenAchievements) },
            ),
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_replay_tutorial),
                supportingText = stringResource(Res.string.settings_replay_tutorial_hint),
                onClick = { onAction(SettingsAction.ReplayTutorial) },
            ),
        ),
    )
}

@Composable
private fun ProSection(state: SettingsState, onAction: (SettingsAction) -> Unit) {
    ListSection(
        title = stringResource(Res.string.settings_section_pro),
        items = buildList {
            add(
                ListSectionItem(
                    headlineText = stringResource(Res.string.settings_pro_status),
                    accessory = ListItemAccessory.Text(
                        text = if (state.isPro) {
                            stringResource(Res.string.settings_pro_active)
                        } else {
                            stringResource(Res.string.settings_pro_inactive)
                        },
                    ),
                ),
            )
            // SPEC 12's "small persistent entry in Settings", and it is absent
            // for a player who already owns Pro. A shop row that sells something
            // you have is the one upsell nobody has an excuse for.
            if (!state.isPro) {
                add(
                    ListSectionItem(
                        headlineText = stringResource(Res.string.settings_pro_upgrade),
                        supportingText = stringResource(Res.string.settings_pro_hint),
                        onClick = { onAction(SettingsAction.OpenPro) },
                    ),
                )
            }
            // Restore stays visible either way. Apple requires a visible control
            // for it, and a player whose entitlement failed to load is exactly
            // the person who needs the button and would be shown "Not active".
            add(
                ListSectionItem(
                    headlineText = stringResource(Res.string.settings_restore),
                    supportingText = state.restoreMessage?.let { restoreLabel(it) },
                    onClick = { onAction(SettingsAction.RestorePurchases) },
                ),
            )
        },
    )
}

/**
 * The paperwork.
 *
 * **Open source licences stay** (owner ruling, 2026-09-20). The app ships
 * Apache-2.0, MIT, BSD, MPL and EPL dependencies, and MIT and BSD both require
 * the notice to travel with the binary — so this row is a licence obligation
 * rather than a courtesy.
 *
 * The diagnostics switch used to sit here and now lives on the feedback form,
 * which is the screen a player is on when the question is live. It is the same
 * stored preference either way; see `FeedbackViewModel`.
 */
@Composable
private fun LegalSection(state: SettingsState, onAction: (SettingsAction) -> Unit) {
    ListSection(
        title = stringResource(Res.string.settings_section_privacy),
        items = buildList {
            add(
                ListSectionItem(
                    headlineText = stringResource(Res.string.settings_privacy_policy),
                    onClick = { onAction(SettingsAction.OpenPrivacy) },
                ),
            )
            add(
                ListSectionItem(
                    headlineText = stringResource(Res.string.settings_terms),
                    onClick = { onAction(SettingsAction.OpenTerms) },
                ),
            )
            // Drawn only when there is a form behind it. A row that opens
            // nothing reads as broken rather than as absent, and it is the first
            // thing a store reviewer taps.
            if (state.adConsentAvailable) {
                add(
                    ListSectionItem(
                        headlineText = stringResource(Res.string.settings_ad_consent),
                        onClick = { onAction(SettingsAction.OpenAdConsent) },
                    ),
                )
            }
            add(
                ListSectionItem(
                    headlineText = stringResource(Res.string.settings_licenses),
                    onClick = { onAction(SettingsAction.OpenLicenses) },
                ),
            )
        },
    )
}

/**
 * Reset, delete, and the id that makes the web form work.
 *
 * The install id sits here rather than under About because its only purpose is
 * the deletion request two rows above it. `PlayerDataEraser` handles everything
 * still on the device; what it cannot reach is the records already sent to
 * Sentry and Grafana, and `nightjarlabs.llc/delete-data` finds those by this id
 * and nothing else.
 */
@Composable
private fun DataSection(state: SettingsState, onAction: (SettingsAction) -> Unit) {
    val installIdCopied = stringResource(Res.string.settings_install_id_copied)
    ListSection(
        title = stringResource(Res.string.settings_section_data),
        items = buildList {
            add(
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_reset_progress),
                supportingText = stringResource(Res.string.settings_reset_progress_hint),
                accessory = ListItemAccessory.Icon(
                    icon = Icons.Refresh(null),
                    tint = AppTheme.colors.danger,
                ),
                onClick = {
                    onAction(SettingsAction.ShowDialog(SettingsDialog.ResetProgressWarn))
                },
            ),
            )
            add(
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_delete_data),
                supportingText = stringResource(Res.string.settings_delete_data_hint),
                accessory = ListItemAccessory.Icon(
                    icon = Icons.Delete(null),
                    tint = AppTheme.colors.danger,
                ),
                onClick = {
                    onAction(SettingsAction.ShowDialog(SettingsDialog.DeleteLocalDataWarn))
                },
            ),
            )
            // Absent, not blank, until AppCache has hydrated. A row showing
            // nothing where an identifier belongs is one the player copies and
            // files anyway.
            state.installId?.let { id ->
                add(
                    ListSectionItem(
                        headlineText = stringResource(Res.string.settings_install_id),
                        supportingText = if (state.installIdCopied) installIdCopied else id,
                        onClick = { onAction(SettingsAction.CopyInstallId) },
                    ),
                )
            }
        },
    )
    state.installId?.let {
        VerticalSpacerD500()
        Text(
            text = stringResource(Res.string.settings_install_id_hint),
            typography = AppTheme.typography.Body.B500,
            color = AppTheme.colors.textSecondary,
        )
    }
}

/**
 * The version row is also the debug-menu gesture (C12): seven taps unlocks it.
 *
 * A plain row rather than anything that looks tappable, because a secret gesture
 * that advertises itself is not one. It is a `ListItem` regardless, so it has the
 * same 58dp target as everything above it.
 */
@Composable
private fun AboutSection(state: SettingsState, onAction: (SettingsAction) -> Unit) {
    ListSection(
        title = stringResource(Res.string.settings_section_about),
        items = listOf(
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_feedback),
                onClick = { onAction(SettingsAction.OpenFeedback) },
            ),
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_version),
                accessory = ListItemAccessory.Text(text = state.appVersion),
                onClick = { onAction(SettingsAction.TapVersion) },
            ),
        ),
    )
    VerticalSpacerD500()
    Text(
        text = stringResource(Res.string.settings_credits),
        typography = AppTheme.typography.Body.B500,
        color = AppTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The tester's own row, and the one developer affordance here that is **not**
 * behind the seven taps.
 *
 * It has to be reachable on a TestFlight build, where the debug menu is behind a
 * passphrase, because the switch it leads to hides the floating directive
 * button — and a control reachable only from behind the thing it controls is not
 * a control. Nothing on the screen behind it is destructive, which is what makes
 * that safe. See `QaToolsRoute`.
 */
@Composable
private fun QaSection(onAction: (SettingsAction) -> Unit) {
    ListSection(
        items = listOf(
            ListSectionItem(
                headlineText = QaToolsLabels.SettingsRow,
                supportingText = QaToolsLabels.SettingsRowHint,
                onClick = { onAction(SettingsAction.OpenQaTools) },
            ),
        ),
    )
}

/**
 * SPEC 19's menu, and the row C11 deliberately did not draw because the screen
 * behind it did not exist yet.
 *
 * Last on the screen, under the version number it is unlocked from, and only
 * once it *is* unlocked — a permanently visible row would make the seven taps
 * pointless. The copy is a plain constant rather than a string resource: nobody
 * who does not work on this game will ever see this row. See `DebugMenuLabels`.
 */
@Composable
private fun DebugSection(onAction: (SettingsAction) -> Unit) {
    ListSection(
        items = listOf(
            ListSectionItem(
                headlineText = DebugMenuLabels.SettingsRow,
                supportingText = DebugMenuLabels.SettingsRowHint,
                onClick = { onAction(SettingsAction.OpenDebugMenu) },
            ),
        ),
    )
}

/**
 * A one-of-N section drawn as list rows with a tick, rather than as a radio
 * group.
 *
 * The design system has `RadioGroup`, and it is the wrong shape here: it draws
 * its own column of loose rows, which would make three of the eight sections on
 * this screen look like they belong to a different app. One row idiom, one
 * grouped card, and the selected row carries a tick.
 */
@Composable
private fun <T> ChoiceSection(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    supportingText: String? = null,
) {
    ListSection(
        title = title,
        supportingText = supportingText,
        items = options.map { option ->
            val text = label(option)
            ListSectionItem(
                headlineText = text,
                accessory = if (option == selected) {
                    ListItemAccessory.Icon(
                        icon = Icons.Check(text),
                        tint = AppTheme.colors.accentPrimary,
                    )
                } else {
                    ListItemAccessory.None
                },
                onClick = { onSelect(option) },
            )
        },
    )
}

@Composable
private fun paletteLabel(choice: BlockPaletteChoice): String = when (choice) {
    BlockPaletteChoice.Default -> stringResource(Res.string.settings_palette_default)
    BlockPaletteChoice.Deuteranopia -> stringResource(Res.string.settings_palette_deuteranopia)
    BlockPaletteChoice.Protanopia -> stringResource(Res.string.settings_palette_protanopia)
    BlockPaletteChoice.Tritanopia -> stringResource(Res.string.settings_palette_tritanopia)
    BlockPaletteChoice.HighContrast -> stringResource(Res.string.settings_palette_high_contrast)
}

@Composable
private fun hapticsLabel(setting: HapticsSetting): String = when (setting) {
    HapticsSetting.Off -> stringResource(Res.string.settings_haptics_off)
    HapticsSetting.Light -> stringResource(Res.string.settings_haptics_light)
    HapticsSetting.Strong -> stringResource(Res.string.settings_haptics_strong)
}

@Composable
private fun restoreLabel(message: RestoreMessage): String = when (message) {
    RestoreMessage.Working -> stringResource(Res.string.settings_restore_working)
    RestoreMessage.Restored -> stringResource(Res.string.settings_restore_restored)
    RestoreMessage.NothingToRestore -> stringResource(Res.string.settings_restore_nothing)
    RestoreMessage.Unavailable -> stringResource(Res.string.settings_restore_unavailable)
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    PreviewContent {
        SettingsScreen(state = SettingsState(appVersion = "1.0.0 (12)"), onAction = {})
    }
}

@Preview
@Composable
private fun SettingsScreenAccessiblePreview() {
    PreviewContent {
        var settings by remember {
            mutableStateOf(
                PlayerSettings(
                    palette = BlockPaletteChoice.HighContrast,
                    largeNumbers = true,
                    haptics = HapticsSetting.Strong,
                ),
            )
        }
        SettingsScreen(
            state = SettingsState(settings = settings, appVersion = "1.0.0 (12)"),
            onAction = { if (it is SettingsAction.SetPalette) settings = settings.copy(palette = it.palette) },
        )
    }
}
