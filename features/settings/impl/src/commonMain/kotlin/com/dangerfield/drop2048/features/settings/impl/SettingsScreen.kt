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
import com.dangerfield.drop2048.features.settings.ControlScheme
import com.dangerfield.drop2048.features.settings.PlayerSettings
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
import drop2048.libraries.resources.generated.resources.settings_ad_consent
import drop2048.libraries.resources.generated.resources.settings_confirm_quit
import drop2048.libraries.resources.generated.resources.settings_confirm_quit_hint
import drop2048.libraries.resources.generated.resources.settings_control_scheme
import drop2048.libraries.resources.generated.resources.settings_credits
import drop2048.libraries.resources.generated.resources.settings_delete_data
import drop2048.libraries.resources.generated.resources.settings_delete_data_hint
import drop2048.libraries.resources.generated.resources.settings_diagnostics
import drop2048.libraries.resources.generated.resources.settings_diagnostics_hint
import drop2048.libraries.resources.generated.resources.settings_feedback
import drop2048.libraries.resources.generated.resources.settings_ghost
import drop2048.libraries.resources.generated.resources.settings_ghost_hint
import drop2048.libraries.resources.generated.resources.settings_haptics
import drop2048.libraries.resources.generated.resources.settings_haptics_light
import drop2048.libraries.resources.generated.resources.settings_haptics_off
import drop2048.libraries.resources.generated.resources.settings_haptics_strong
import drop2048.libraries.resources.generated.resources.settings_large_numbers
import drop2048.libraries.resources.generated.resources.settings_large_numbers_hint
import drop2048.libraries.resources.generated.resources.settings_left_handed
import drop2048.libraries.resources.generated.resources.settings_left_handed_hint
import drop2048.libraries.resources.generated.resources.settings_licenses
import drop2048.libraries.resources.generated.resources.settings_music
import drop2048.libraries.resources.generated.resources.settings_palette
import drop2048.libraries.resources.generated.resources.settings_palette_default
import drop2048.libraries.resources.generated.resources.settings_palette_deuteranopia
import drop2048.libraries.resources.generated.resources.settings_palette_high_contrast
import drop2048.libraries.resources.generated.resources.settings_palette_protanopia
import drop2048.libraries.resources.generated.resources.settings_palette_tritanopia
import drop2048.libraries.resources.generated.resources.settings_privacy_policy
import drop2048.libraries.resources.generated.resources.settings_pro_active
import drop2048.libraries.resources.generated.resources.settings_pro_inactive
import drop2048.libraries.resources.generated.resources.settings_pro_status
import drop2048.libraries.resources.generated.resources.settings_reduce_motion
import drop2048.libraries.resources.generated.resources.settings_reduce_motion_hint
import drop2048.libraries.resources.generated.resources.settings_replay_tutorial
import drop2048.libraries.resources.generated.resources.settings_replay_tutorial_hint
import drop2048.libraries.resources.generated.resources.settings_reset_progress
import drop2048.libraries.resources.generated.resources.settings_reset_progress_hint
import drop2048.libraries.resources.generated.resources.settings_restore
import drop2048.libraries.resources.generated.resources.settings_restore_nothing
import drop2048.libraries.resources.generated.resources.settings_restore_restored
import drop2048.libraries.resources.generated.resources.settings_restore_unavailable
import drop2048.libraries.resources.generated.resources.settings_restore_working
import drop2048.libraries.resources.generated.resources.settings_scheme_both
import drop2048.libraries.resources.generated.resources.settings_scheme_buttons
import drop2048.libraries.resources.generated.resources.settings_scheme_drag
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
            DataSection(onAction)
            VerticalSpacerD1000()
            AboutSection(state, onAction)
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
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_music),
                accessory = ListItemAccessory.Switch(
                    checked = settings.musicEnabled,
                    onCheckedChange = { onAction(SettingsAction.ToggleMusic) },
                ),
                onClick = { onAction(SettingsAction.ToggleMusic) },
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

@Composable
private fun ControlsSection(settings: PlayerSettings, onAction: (SettingsAction) -> Unit) {
    ChoiceSection(
        title = stringResource(Res.string.settings_section_controls),
        supportingText = stringResource(Res.string.settings_control_scheme),
        options = ControlScheme.entries,
        selected = settings.controlScheme,
        label = { schemeLabel(it) },
        onSelect = { onAction(SettingsAction.SetControlScheme(it)) },
    )
    VerticalSpacerD500()
    ListSection(
        items = listOf(
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_left_handed),
                supportingText = stringResource(Res.string.settings_left_handed_hint),
                accessory = ListItemAccessory.Switch(
                    checked = settings.leftHanded,
                    onCheckedChange = { onAction(SettingsAction.ToggleLeftHanded) },
                ),
                onClick = { onAction(SettingsAction.ToggleLeftHanded) },
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
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_confirm_quit),
                supportingText = stringResource(Res.string.settings_confirm_quit_hint),
                accessory = ListItemAccessory.Switch(
                    checked = settings.confirmBeforeQuit,
                    onCheckedChange = { onAction(SettingsAction.ToggleConfirmQuit) },
                ),
                onClick = { onAction(SettingsAction.ToggleConfirmQuit) },
            ),
        ),
    )
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
                headlineText = stringResource(Res.string.settings_reduce_motion),
                supportingText = stringResource(Res.string.settings_reduce_motion_hint),
                accessory = ListItemAccessory.Switch(
                    checked = settings.reduceMotion,
                    onCheckedChange = { onAction(SettingsAction.ToggleReduceMotion) },
                ),
                onClick = { onAction(SettingsAction.ToggleReduceMotion) },
            ),
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
        items = listOf(
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
            ListSectionItem(
                headlineText = stringResource(Res.string.settings_restore),
                supportingText = state.restoreMessage?.let { restoreLabel(it) },
                onClick = { onAction(SettingsAction.RestorePurchases) },
            ),
        ),
    )
}

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
            add(
                ListSectionItem(
                    headlineText = stringResource(Res.string.settings_diagnostics),
                    supportingText = stringResource(Res.string.settings_diagnostics_hint),
                    accessory = ListItemAccessory.Switch(
                        checked = state.settings.diagnosticsOptIn,
                        onCheckedChange = { onAction(SettingsAction.ToggleDiagnostics) },
                    ),
                    onClick = { onAction(SettingsAction.ToggleDiagnostics) },
                ),
            )
        },
    )
}

@Composable
private fun DataSection(onAction: (SettingsAction) -> Unit) {
    ListSection(
        title = stringResource(Res.string.settings_section_data),
        items = listOf(
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
        ),
    )
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
private fun schemeLabel(scheme: ControlScheme): String = when (scheme) {
    ControlScheme.Buttons -> stringResource(Res.string.settings_scheme_buttons)
    ControlScheme.Drag -> stringResource(Res.string.settings_scheme_drag)
    ControlScheme.Both -> stringResource(Res.string.settings_scheme_both)
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
                    reduceMotion = true,
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
