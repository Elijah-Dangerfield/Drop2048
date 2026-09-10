package com.dangerfield.drop2048.libraries.ui.system

import androidx.compose.runtime.Composable

/** Desktop ships no sample bank. This target is preview and tooling only. */
@Composable
actual fun rememberPlatformSoundPlayer(enabled: Boolean): SoundPlayer = SoundPlayer.Silent
