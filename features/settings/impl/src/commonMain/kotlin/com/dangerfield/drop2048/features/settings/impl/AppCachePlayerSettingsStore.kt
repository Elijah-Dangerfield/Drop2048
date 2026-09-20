package com.dangerfield.drop2048.features.settings.impl

import com.dangerfield.drop2048.features.settings.ControlScheme
import com.dangerfield.drop2048.features.settings.asEnumOr
import com.dangerfield.drop2048.features.settings.PlayerSettings
import com.dangerfield.drop2048.features.settings.PlayerSettingsStore
import com.dangerfield.drop2048.libraries.core.AutoInit
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.ui.system.HapticsSetting
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * [PlayerSettingsStore] over `AppData`.
 *
 * [AutoInit] because the disk read has to be in flight before the first
 * composition rather than on first injection: the root of the tree hands these
 * values to `AppThemeProvider`, and a palette that arrives after the first frame
 * is a visible flash of the wrong colours. Boot resolves the `Set<AutoInit>`
 * ahead of the nav host, so in practice the read lands during the boot gate.
 *
 * The flow is also fed by `appCache.updates`, so a write from anywhere else —
 * the pause overlay's ghost toggle, a reset, a gate's acceptance — reaches the
 * theme without the settings screen being on screen.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = PlayerSettingsStore::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class AppCachePlayerSettingsStore(
    private val appCache: AppCache,
    private val appScope: AppCoroutineScope,
) : PlayerSettingsStore, AutoInit {

    private val _settings = MutableStateFlow(PlayerSettings())
    override val settings: StateFlow<PlayerSettings> = _settings.asStateFlow()

    init {
        appScope.launch {
            Catching { _settings.value = appCache.get().toPlayerSettings() }
                .logOnFailure { "Failed to read player settings; using defaults" }

            appCache.updates.collect { data -> _settings.value = data.toPlayerSettings() }
        }
    }

    override suspend fun update(transform: (PlayerSettings) -> PlayerSettings) {
        val next = transform(_settings.value)
        // Optimistic, so the switch under the player's thumb moves on the same
        // frame as the tap. The cache write re-publishes the same value through
        // `updates` a moment later.
        _settings.value = next
        Catching { appCache.update { data -> data.merge(next) } }
            .logOnFailure { "Failed to persist a player setting" }
    }
}

private fun AppData.toPlayerSettings() = PlayerSettings(
    palette = blockPalette.asEnumOr(BlockPaletteChoice.Default),
    largeNumbers = largeBlockNumbers,
    haptics = hapticsSetting.asEnumOr(HapticsSetting.Light),
    soundEnabled = soundEnabled,
    controlScheme = controlScheme.asEnumOr(ControlScheme.Drag),
    ghostEnabled = ghostEnabled,
    debugMenuUnlocked = debugMenuUnlocked,
)

private fun AppData.merge(settings: PlayerSettings) = copy(
    blockPalette = settings.palette.name,
    largeBlockNumbers = settings.largeNumbers,
    hapticsSetting = settings.haptics.name,
    soundEnabled = settings.soundEnabled,
    controlScheme = settings.controlScheme.name,
    ghostEnabled = settings.ghostEnabled,
    debugMenuUnlocked = settings.debugMenuUnlocked,
)
