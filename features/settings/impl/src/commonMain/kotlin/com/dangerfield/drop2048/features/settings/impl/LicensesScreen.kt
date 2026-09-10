package com.dangerfield.drop2048.features.settings.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dangerfield.drop2048.libraries.ui.PreviewContent
import com.dangerfield.drop2048.libraries.ui.components.Screen
import com.dangerfield.drop2048.libraries.ui.components.header.TopBar
import com.dangerfield.drop2048.libraries.ui.components.text.Text
import com.dangerfield.drop2048.libraries.ui.screenContentPadding
import com.dangerfield.drop2048.system.AppTheme
import com.dangerfield.drop2048.system.VerticalSpacerD1000
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.licenses_body
import drop2048.libraries.resources.generated.resources.licenses_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Third-party attribution.
 *
 * Static copy rather than a generated report, and that is a deliberate v1 call
 * with a cost worth naming: a hand-written list goes stale the moment a
 * dependency is added. It is here because every dependency this app ships is
 * Apache 2.0 or MIT, both of which are satisfied by attribution plus the licence
 * text in the store listing, and because wiring a licence-report plugin into a
 * Kotlin Multiplatform build is its own afternoon. C13 (store prep) is where a
 * generated report belongs, alongside the listing that carries the full text.
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()

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
            VerticalSpacerD1000()
        }
    }
}

@Preview
@Composable
private fun LicensesScreenPreview() {
    PreviewContent {
        LicensesScreen(onBack = {})
    }
}
