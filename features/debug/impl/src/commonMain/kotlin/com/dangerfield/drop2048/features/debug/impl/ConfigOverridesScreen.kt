package com.dangerfield.drop2048.features.debug.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
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
import com.dangerfield.drop2048.system.VerticalSpacerD500
import com.dangerfield.drop2048.system.VerticalSpacerD1000
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Every config key the app has, grouped, with what it is resolving now.
 *
 * Drawn out of `ListSection` like the rest of the menu. The one thing the layout
 * insists on is that a row says **three** numbers rather than one: the resolved
 * value, the compiled default, and whether an override is what is making them
 * differ. A screen that showed only the current value could not tell a key the
 * console changed apart from a key this screen changed, and the second one
 * follows the tester to every other build they install.
 *
 * A pure render of [ConfigOverridesState]. The text being typed into a number
 * field is the only thing held here, and it is deliberately not committed until
 * the row's Set is pressed — a field that wrote on every keystroke would persist
 * `1`, `18` and `180` on the way to typing 180, and the first two are real
 * overrides that really applied.
 */
@Composable
fun ConfigOverridesScreen(
    state: ConfigOverridesState,
    onAction: (ConfigOverridesAction) -> Unit,
) {
    val scrollState = rememberScrollState()

    Screen(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = DebugCopy.ConfigTitle,
                onNavigateBack = { onAction(ConfigOverridesAction.Back) },
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
            Text(
                text = DebugCopy.ConfigBody,
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )
            VerticalSpacerD500()
            Row(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
                ButtonPrimary(
                    onClick = { onAction(ConfigOverridesAction.ClearAll) },
                    size = ButtonSize.Small,
                    enabled = state.overriddenCount > 0,
                ) { Text(DebugCopy.ConfigClearAll) }
            }
            VerticalSpacerD500()
            Text(
                text = DebugCopy.overriddenCount(state.overriddenCount),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )
            VerticalSpacerD1000()

            state.rows.groupBy { it.group }.forEach { (group, rows) ->
                ConfigGroup(group, rows, onAction)
                VerticalSpacerD1000()
            }
        }
    }
}

@Composable
private fun ConfigGroup(
    group: String,
    rows: List<ConfigRow>,
    onAction: (ConfigOverridesAction) -> Unit,
) {
    Text(text = group, typography = AppTheme.typography.Heading.H700)
    VerticalSpacerD500()
    rows.forEach { row ->
        ConfigRowItem(row, onAction)
        VerticalSpacerD500()
    }
}

@Composable
private fun ConfigRowItem(row: ConfigRow, onAction: (ConfigOverridesAction) -> Unit) {
    ListSection(
        items = listOfNotNull(
            ListSectionItem(
                headlineText = row.name,
                supportingText = row.path,
                accessory = when (row.kind) {
                    ConfigKind.Flag -> ListItemAccessory.Switch(
                        checked = row.current.toBooleanStrictOrNull() ?: false,
                        onCheckedChange = {
                            onAction(
                                ConfigOverridesAction.Toggle(
                                    path = row.path,
                                    current = row.current.toBooleanStrictOrNull() ?: false,
                                )
                            )
                        },
                    )

                    else -> ListItemAccessory.Text(text = row.current)
                },
            ),
            ListSectionItem(
                headlineText = DebugCopy.configDefault(row.defaultValue),
                supportingText = if (row.isOverridden) DebugCopy.ConfigOverridden else null,
                accessory = ListItemAccessory.None,
            ),
            if (row.isOverridden) {
                ListSectionItem(
                    headlineText = DebugCopy.ConfigReset,
                    onClick = { onAction(ConfigOverridesAction.Reset(row.path)) },
                )
            } else {
                null
            },
        ),
    )
    if (row.kind != ConfigKind.Flag) {
        VerticalSpacerD500()
        ConfigValueField(row, onAction)
    }
}

/**
 * Local to the row, and keyed on what the app currently resolves.
 *
 * The key is what makes "clear all" visible: without it the field keeps the
 * text the tester typed after the override behind it has been removed, and the
 * screen shows a value that is no longer in force.
 *
 * The Set button sits **under** the field rather than beside it. Some of these
 * names run to two lines at 360dp — `ads.interstitial.suppressDaysSinceInstall`
 * is one — and a Set button squeezed off the edge of the row a tester came here
 * for is the screen failing at its only job.
 */
@Composable
private fun ConfigValueField(row: ConfigRow, onAction: (ConfigOverridesAction) -> Unit) {
    var typed by remember(row.current) { mutableStateOf(row.current) }
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(row.name) },
        singleLine = true,
    )
    VerticalSpacerD500()
    Row(horizontalArrangement = Arrangement.spacedBy(ChipGap)) {
        ButtonPrimary(
            onClick = { onAction(ConfigOverridesAction.Submit(row.path, typed)) },
            size = ButtonSize.Small,
            enabled = typed != row.current,
        ) { Text(DebugCopy.ConfigSet) }
        row.allowedValues.orEmpty().forEach { option ->
            ButtonPrimary(
                onClick = { onAction(ConfigOverridesAction.Submit(row.path, option)) },
                size = ButtonSize.Small,
            ) { Text(option) }
        }
    }
}

private val ChipGap = 8.dp

@Preview
@Composable
private fun ConfigOverridesScreenPreview() {
    PreviewContent {
        ConfigOverridesScreen(state = ConfigOverridesState(rows = SampleConfigRows), onAction = {})
    }
}

/**
 * The four rows that turn a three-day wait into four taps, plus the kill switch.
 *
 * Used by the preview and by the golden, so the frame that gets checked is the
 * one the chunk is actually about.
 */
internal val SampleConfigRows = listOf(
    ConfigRow(
        path = "ads.enabled",
        name = "Ads enabled",
        description = "Master kill switch for all advertising, rewarded included.",
        group = "ads",
        kind = ConfigKind.Flag,
        current = "true",
        defaultValue = "true",
        isOverridden = false,
        allowedValues = null,
    ),
    ConfigRow(
        path = "ads.interstitial.cooldownSeconds",
        name = "Interstitial: cooldown (seconds)",
        description = "Minimum gap since the last interstitial. SPEC 12: 180s.",
        group = "ads",
        kind = ConfigKind.Number,
        current = "0",
        defaultValue = "180",
        isOverridden = true,
        allowedValues = null,
    ),
    ConfigRow(
        path = "ads.interstitial.minSessionRuns",
        name = "Interstitial: earliest run of a session",
        description = "SPEC 12: never before the 4th run of a session.",
        group = "ads",
        kind = ConfigKind.Number,
        current = "0",
        defaultValue = "4",
        isOverridden = true,
        allowedValues = null,
    ),
    ConfigRow(
        path = "ads.interstitial.suppressDaysSinceInstall",
        name = "Interstitial: suppress for days after install",
        description = "Suppressed entirely for this many days after install. SPEC 12: 3.",
        group = "ads",
        kind = ConfigKind.Number,
        current = "0",
        defaultValue = "3",
        isOverridden = true,
        allowedValues = null,
    ),
    ConfigRow(
        path = "upgrade.maintenanceMode",
        name = "Maintenance mode",
        description = "off | banner | blocking. Blocking locks every player out.",
        group = "upgrade",
        kind = ConfigKind.Text,
        current = "off",
        defaultValue = "off",
        isOverridden = false,
        allowedValues = listOf("off", "banner", "blocking"),
    ),
)
