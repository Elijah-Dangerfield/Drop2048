package com.dangerfield.drop2048.features.settings.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.header.TopBar
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.screenContentPadding
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.VerticalSpacerD500
import com.dangerfield.drop2048.system.VerticalSpacerD1000
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.licenses_body
import drop2048.libraries.resources.generated.resources.licenses_count
import drop2048.libraries.resources.generated.resources.licenses_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Third-party attribution, read from a generated list rather than written by
 * hand.
 *
 * ## Why it is generated now, and what a curated list cost
 *
 * C11 wrote this screen's copy as a sentence, and the sentence was true: every
 * dependency the app shipped at that moment was Apache 2.0 or MIT. C10 then
 * added AdMob, UMP, Play Billing and Play Review, and **17 modules arrived on
 * proprietary Google SDK terms** — which are fine to ship and are not satisfied
 * by an Apache-2.0 attribution block. Nothing failed. The screen went on saying
 * the old thing for two chunks, and only a store-prep audit that ran a report
 * over the actual classpath noticed (ORCHESTRATION L73).
 *
 * That is the shape of the bug, not the instance of it: **a claim about a
 * dependency set has a shelf life measured in chunks**, and a screen that states
 * the claim rather than reading the set will always eventually be wrong. So the
 * screen now reads `files/licenses.txt`, generated from
 * `:apps:compose`'s `releaseRuntimeClasspath` by
 * `scripts/licenses/license-report.init.gradle` — the same run that writes
 * `docs/store/licenses.md`, from the same POMs, so the screen and the document
 * cannot disagree.
 *
 * ## What this still does not do
 *
 * Regeneration is a release-checklist step, not a build step. C13's reasoning
 * for that stands and is worth not re-litigating: a licence report is a release
 * artefact rather than a build input, so a licence plugin on the shared build
 * classpath would put a configuration-time dependency on every detekt run and
 * every screenshot test for a file regenerated a few times a year.
 *
 * The consequence is honest and bounded: adding a dependency does not fail
 * anything, and the list is as fresh as the last regeneration. That is strictly
 * better than the sentence it replaces, which could not be refreshed at all
 * because nothing generated it. `docs/store/release-checklist.md` §6 owns the
 * step.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    var groups by remember { mutableStateOf<List<LicenceGroup>>(emptyList()) }

    LaunchedEffect(Unit) {
        groups = Catching { Res.readBytes(LicencesPath).decodeToString() }
            .logOnFailure { "Could not read the generated licence list" }
            .getOrNull()
            ?.let(::parseLicences)
            .orEmpty()
    }

    Screen(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar(
                title = stringResource(Res.string.licenses_title),
                onNavigateBack = onBack,
                scrollState = scrollState,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .screenContentPadding(paddingValues = padding),
        ) {
            VerticalSpacerD1000()
            Text(
                text = stringResource(Res.string.licenses_body),
                typography = AppTheme.typography.Body.B500,
                color = AppTheme.colors.textSecondary,
            )

            groups.forEach { group ->
                VerticalSpacerD1000()
                Text(
                    text = group.licence,
                    typography = AppTheme.typography.Heading.H500,
                    color = AppTheme.colors.text,
                )
                if (group.url.isNotBlank()) {
                    Text(
                        text = group.url,
                        typography = AppTheme.typography.Body.B400,
                        color = AppTheme.colors.textSecondary,
                    )
                }
                VerticalSpacerD500()
                Text(
                    text = stringResource(Res.string.licenses_count, group.modules.size),
                    typography = AppTheme.typography.Label.L400,
                    color = AppTheme.colors.textSecondary,
                )
                Text(
                    text = group.modules.joinToString(ModuleSeparator),
                    typography = AppTheme.typography.Body.B400,
                    color = AppTheme.colors.textSecondary,
                )
            }

            VerticalSpacerD1000()
        }
    }
}

/** One licence and every shipped module published under it. */
data class LicenceGroup(
    val licence: String,
    val url: String,
    val modules: List<String>,
)

/**
 * Parses the generated list: `coordinate\tversion\tlicence\turl`, one per line.
 *
 * Grouped by licence **name**, ordered by how many modules carry it, so the two
 * lines that matter — the proprietary Google SDK terms — are visible without
 * reading 361 coordinates to find them.
 *
 * By name and not by name-and-URL, which was the first attempt: modules
 * published under the same licence cite it at different addresses, so pairing
 * the two split "The Apache Software License, Version 2.0" into 243 modules
 * under one heading and 157 under another identical one. The first address any
 * member cites is used for the group, which is the one a reader needs.
 *
 * A malformed line is dropped rather than crashing a screen whose whole job is
 * to be readable; the generator writes this file, and a malformed line means the
 * generator is broken, which is a build-time problem.
 */
internal fun parseLicences(raw: String): List<LicenceGroup> = raw.lineSequence()
    .map { it.split(FieldSeparator) }
    .filter { it.size >= FieldCount && it[0].isNotBlank() }
    .groupBy { it[2].ifBlank { Undeclared } }
    .map { (licence, rows) ->
        LicenceGroup(
            licence = licence,
            url = rows.firstNotNullOfOrNull { row -> row[3].takeIf { it.isNotBlank() } }.orEmpty(),
            modules = rows.map { it[0] }.sorted(),
        )
    }
    .sortedWith(compareByDescending<LicenceGroup> { it.modules.size }.thenBy { it.licence })

private const val LicencesPath = "files/licenses.txt"
private const val FieldSeparator = '\t'
private const val FieldCount = 4
private const val Undeclared = "Undeclared"
private const val ModuleSeparator = ", "

@Preview
@Composable
private fun LicensesScreenPreview() {
    PreviewContent {
        LicensesScreen(onBack = {})
    }
}
