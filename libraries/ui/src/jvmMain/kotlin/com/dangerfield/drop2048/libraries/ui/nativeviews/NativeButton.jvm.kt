package com.dangerfield.drop2048.libraries.ui.nativeviews

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// The JVM target exists for previews and the design-system catalog, so this
// label is dev-facing and stays a constant rather than a string resource.
private const val NativeButtonLabel = "Native Button"

@Composable
actual fun NativeButton(
    onClick: () -> Unit,
    modifier: Modifier
) {
    Button(onClick = onClick, modifier = modifier) {
        Text(text = NativeButtonLabel)
    }
}
