package com.dangerfield.drop2048.libraries.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.dangerfield.drop2048.system.AppTheme

@Composable
fun FullScreenLoader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(color = AppTheme.colors.background.color),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}