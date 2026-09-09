package com.dangerfield.drop2048.libraries.ui

import androidx.compose.runtime.MutableState

fun MutableState<Boolean>.toggle() {
    value = !value
}