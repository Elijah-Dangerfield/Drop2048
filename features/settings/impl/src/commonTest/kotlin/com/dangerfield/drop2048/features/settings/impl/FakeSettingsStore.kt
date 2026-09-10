package com.dangerfield.drop2048.features.settings.impl

import com.dangerfield.drop2048.features.settings.PlayerSettings
import com.dangerfield.drop2048.features.settings.PlayerSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An in-memory [PlayerSettingsStore].
 *
 * Deliberately not a stub that swallows writes: half the point of this chunk is
 * that a setting written here is the same value the theme reads, so a fake that
 * did not publish would test nothing worth testing.
 */
internal class FakeSettingsStore(
    initial: PlayerSettings = PlayerSettings(),
) : PlayerSettingsStore {

    private val _settings = MutableStateFlow(initial)
    override val settings: StateFlow<PlayerSettings> = _settings.asStateFlow()

    override suspend fun update(transform: (PlayerSettings) -> PlayerSettings) {
        _settings.value = transform(_settings.value)
    }
}
