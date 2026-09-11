package com.dangerfield.drop2048.features.settings.impl

import androidx.lifecycle.viewModelScope
import com.dangerfield.drop2048.features.settings.ControlScheme
import com.dangerfield.drop2048.features.settings.PlayerSettings
import com.dangerfield.drop2048.features.settings.PlayerSettingsStore
import com.dangerfield.drop2048.libraries.ads.AdConsent
import com.dangerfield.drop2048.libraries.billing.Entitlements
import com.dangerfield.drop2048.libraries.billing.PaywallCoordinator
import com.dangerfield.drop2048.libraries.billing.PaywallTrigger
import com.dangerfield.drop2048.libraries.billing.RestoreOutcome
import com.dangerfield.drop2048.libraries.core.BuildInfo
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.versionString
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import com.dangerfield.drop2048.libraries.flowroutines.collectIn
import com.dangerfield.drop2048.libraries.gameconfig.LegalPrivacyUrl
import com.dangerfield.drop2048.libraries.gameconfig.LegalTermsUrl
import com.dangerfield.drop2048.libraries.ui.system.HapticsSetting
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import me.tatarka.inject.annotations.Inject

/**
 * The player's settings (SPEC 11 and 16).
 *
 * **Nothing on this screen owns a value.** Every toggle is a `transform` handed
 * to [PlayerSettingsStore], which is also what the root of the composition reads
 * to build the theme — so flipping a palette here changes what the board draws
 * without anything on this screen knowing the board exists. That single-source
 * arrangement is the whole reason C2's five palettes were unreachable for six
 * chunks: there was a store-shaped hole where this now sits.
 *
 * The legal URLs come from remote config so a moved policy page is a config
 * change rather than an app release.
 */
@Inject
class SettingsViewModel(
    private val settingsStore: PlayerSettingsStore,
    private val eraser: PlayerDataEraser,
    private val entitlement: Entitlements,
    private val adConsent: AdConsent,
    private val paywall: PaywallCoordinator,
    private val termsUrl: LegalTermsUrl,
    private val privacyUrl: LegalPrivacyUrl,
) : SEAViewModel<SettingsState, SettingsEvent, SettingsAction>(
    initialStateArg = SettingsState(
        appVersion = BuildInfo.versionString(),
        adConsentAvailable = false,
    ),
) {

    /**
     * Taps on the version number since the last one that was not part of a run.
     *
     * A plain field rather than state: `state` lags `updateState` by a dispatch,
     * and seven taps arrive faster than seven dispatches.
     */
    private var versionTaps = 0

    init {
        takeAction(SettingsAction.Load)
        // Collected rather than read once. The store hydrates from disk
        // asynchronously at boot, so a value read in `load` can be the default
        // rather than the player's — and a settings screen that showed the wrong
        // switch positions for one frame is the one screen where that is
        // unforgivable.
        settingsStore.settings.collectIn(viewModelScope) {
            takeAction(SettingsAction.SettingsChanged(it))
        }
        entitlement.isPro.collectIn(viewModelScope) {
            takeAction(SettingsAction.ProChanged(it))
        }
    }

    override suspend fun handleAction(action: SettingsAction) {
        when (action) {
            SettingsAction.Load -> action.updateState {
                it.copy(adConsentAvailable = adConsent.isAvailable)
            }

            is SettingsAction.SettingsChanged -> action.updateState {
                it.copy(settings = action.settings)
            }

            is SettingsAction.ProChanged -> action.updateState { it.copy(isPro = action.isPro) }

            SettingsAction.Back -> sendEvent(SettingsEvent.NavigateBack)

            is SettingsAction.SetPalette -> persist { it.copy(palette = action.palette) }
            is SettingsAction.SetHaptics -> persist { it.copy(haptics = action.setting) }
            is SettingsAction.SetControlScheme -> persist { it.copy(controlScheme = action.scheme) }
            SettingsAction.ToggleReduceMotion -> persist { it.copy(reduceMotion = !it.reduceMotion) }
            SettingsAction.ToggleLargeNumbers -> persist { it.copy(largeNumbers = !it.largeNumbers) }
            SettingsAction.ToggleSound -> persist { it.copy(soundEnabled = !it.soundEnabled) }
            SettingsAction.ToggleMusic -> persist { it.copy(musicEnabled = !it.musicEnabled) }
            SettingsAction.ToggleLeftHanded -> persist { it.copy(leftHanded = !it.leftHanded) }
            SettingsAction.ToggleGhost -> persist { it.copy(ghostEnabled = !it.ghostEnabled) }
            SettingsAction.ToggleConfirmQuit -> persist {
                it.copy(confirmBeforeQuit = !it.confirmBeforeQuit)
            }

            SettingsAction.ToggleDiagnostics -> persist {
                it.copy(diagnosticsOptIn = !it.diagnosticsOptIn)
            }

            // SPEC 12's persistent Settings entry. It goes through the
            // coordinator rather than straight to a route so the one refusal
            // that matters — the player already owns Pro — lives in one place
            // instead of being a second `if` on this screen.
            SettingsAction.OpenPro -> paywall.requestOffer(PaywallTrigger.Direct)
            SettingsAction.OpenAchievements -> sendEvent(SettingsEvent.OpenAchievements)
            SettingsAction.OpenDebugMenu -> sendEvent(SettingsEvent.OpenDebugMenu)
            SettingsAction.ReplayTutorial -> sendEvent(SettingsEvent.ReplayTutorial)
            SettingsAction.OpenFeedback -> sendEvent(SettingsEvent.OpenFeedback)
            SettingsAction.OpenLicenses -> sendEvent(SettingsEvent.OpenLicenses)
            SettingsAction.OpenTerms -> sendEvent(SettingsEvent.OpenLink(termsUrl()))
            SettingsAction.OpenPrivacy -> sendEvent(SettingsEvent.OpenLink(privacyUrl()))
            SettingsAction.OpenAdConsent -> action.presentConsent()

            SettingsAction.RestorePurchases -> action.restorePurchases()
            SettingsAction.DismissRestoreMessage -> action.updateState {
                it.copy(restoreMessage = null)
            }

            SettingsAction.TapVersion -> action.tapVersion()

            is SettingsAction.ShowDialog -> action.updateState { it.copy(dialog = action.dialog) }
            SettingsAction.DismissDialog -> action.updateState { it.copy(dialog = null) }

            is SettingsAction.ConfirmResetProgress -> action.confirmReset()
            SettingsAction.ConfirmDeleteLocalData -> action.deleteLocalData()
        }
    }

    /**
     * Seven taps unlocks the debug menu (C12 builds the menu; this is the
     * gesture and the flag).
     *
     * Idempotent past the seventh: once it is unlocked, further taps do nothing
     * rather than re-announcing. The counter never resets on a timer, because a
     * time-boxed secret gesture is a secret gesture that fails on a slow phone.
     */
    private suspend fun SettingsAction.tapVersion() {
        if (state.settings.debugMenuUnlocked) return
        versionTaps += 1
        if (versionTaps < TapsToUnlockDebug) return
        versionTaps = 0
        persist { it.copy(debugMenuUnlocked = true) }
        sendEvent(SettingsEvent.DebugMenuUnlocked)
    }

    /**
     * The typed-word gate on "reset progress".
     *
     * Compared here rather than in the screen so the rule is testable without a
     * composition, and [required] travels in from the screen so the word itself
     * stays a translated string. A mismatch closes nothing and wipes nothing —
     * the dialog stays open and the player can try again.
     */
    private suspend fun SettingsAction.ConfirmResetProgress.confirmReset() {
        if (typed.trim().equals(required.trim(), ignoreCase = true).not()) return
        updateState { it.copy(dialog = null) }
        Catching { eraser.resetProgress() }
            .logOnFailure { "Reset progress failed" }
        sendEvent(SettingsEvent.ProgressReset)
    }

    private suspend fun SettingsAction.deleteLocalData() {
        updateState { it.copy(dialog = null) }
        Catching { eraser.deleteLocalData() }
            .logOnFailure { "Delete local data failed" }
        sendEvent(SettingsEvent.LocalDataDeleted)
    }

    private suspend fun SettingsAction.presentConsent() {
        Catching { adConsent.present() }
            .logOnFailure { "Failed to present the ad consent form" }
    }

    /**
     * Asks the store what this device already owns.
     *
     * The outcome travels as a value into one `updateState` rather than being
     * read back off `state`, which lags by a dispatch. Every branch says
     * something: a restore that silently does nothing is the single most common
     * reason this control gets reported as broken, because the player cannot
     * tell "you never bought it" from "we could not ask".
     */
    private suspend fun SettingsAction.restorePurchases() {
        updateState { it.copy(restoreMessage = RestoreMessage.Working) }
        val outcome = Catching { entitlement.restore() }
            .logOnFailure { "Restore failed" }
            .getOrNull()
        updateState {
            it.copy(
                restoreMessage = when (outcome) {
                    RestoreOutcome.Restored -> RestoreMessage.Restored
                    RestoreOutcome.NothingToRestore -> RestoreMessage.NothingToRestore
                    else -> RestoreMessage.Unavailable
                },
            )
        }
    }

    private suspend fun persist(transform: (PlayerSettings) -> PlayerSettings) {
        settingsStore.update(transform)
    }
}

private const val TapsToUnlockDebug = 7

data class SettingsState(
    val settings: PlayerSettings = PlayerSettings(),

    /** Whether this device owns Pro (SPEC 12). */
    val isPro: Boolean = false,

    /** Set while a restore is in flight, and to its result afterwards. */
    val restoreMessage: RestoreMessage? = null,

    /**
     * Whether there is a consent form to present. False takes the row off the
     * screen entirely rather than drawing one that opens nothing.
     */
    val adConsentAvailable: Boolean = false,

    val appVersion: String = "",

    /** The one destructive dialog on screen, if any. */
    val dialog: SettingsDialog? = null,
)

/** The two destructive flows, each in its own step. */
sealed interface SettingsDialog {
    /** Step one of "reset progress": are you sure. */
    data object ResetProgressWarn : SettingsDialog

    /** Step two: type the word. Nothing is deleted until this one is satisfied. */
    data object ResetProgressConfirm : SettingsDialog

    data object DeleteLocalDataWarn : SettingsDialog
}

/** What a restore attempt has to say for itself. */
enum class RestoreMessage { Working, Restored, NothingToRestore, Unavailable }

sealed interface SettingsEvent {
    data object NavigateBack : SettingsEvent
    data object OpenFeedback : SettingsEvent
    data object OpenLicenses : SettingsEvent

    /** `GameRoute(replayTutorial = true)` — SPEC 13's replay, from its only caller. */
    data object OpenAchievements : SettingsEvent

    data object ReplayTutorial : SettingsEvent

    data object ProgressReset : SettingsEvent
    data object LocalDataDeleted : SettingsEvent
    data object DebugMenuUnlocked : SettingsEvent

    /** SPEC 19's menu (C12). Drawn only once [PlayerSettings.debugMenuUnlocked] is set. */
    data object OpenDebugMenu : SettingsEvent

    /** Terms and privacy are hosted pages, so they open in a browser. */
    data class OpenLink(val url: String) : SettingsEvent
}

sealed interface SettingsAction {
    data object Load : SettingsAction
    data class SettingsChanged(val settings: PlayerSettings) : SettingsAction
    data class ProChanged(val isPro: Boolean) : SettingsAction
    data object Back : SettingsAction

    data class SetPalette(val palette: BlockPaletteChoice) : SettingsAction
    data class SetHaptics(val setting: HapticsSetting) : SettingsAction
    data class SetControlScheme(val scheme: ControlScheme) : SettingsAction
    data object ToggleReduceMotion : SettingsAction
    data object ToggleLargeNumbers : SettingsAction
    data object ToggleSound : SettingsAction
    data object ToggleMusic : SettingsAction
    data object ToggleLeftHanded : SettingsAction
    data object ToggleGhost : SettingsAction
    data object ToggleConfirmQuit : SettingsAction
    data object ToggleDiagnostics : SettingsAction

    /** SPEC 15's badge grid. Settings is its only entry point. */
    /** SPEC 12's "small persistent entry in Settings". */
    data object OpenPro : SettingsAction

    data object OpenAchievements : SettingsAction

    data object ReplayTutorial : SettingsAction
    data object OpenFeedback : SettingsAction
    data object OpenLicenses : SettingsAction
    data object OpenTerms : SettingsAction
    data object OpenPrivacy : SettingsAction
    data object OpenAdConsent : SettingsAction

    /**
     * Apple requires a visible control that restores a non-consumable purchase
     * and rejects for its absence. It is also the right thing on a device with
     * no account: a reinstall is the only way a paying player gets it back.
     */
    data object RestorePurchases : SettingsAction
    data object DismissRestoreMessage : SettingsAction

    data object TapVersion : SettingsAction
    data object OpenDebugMenu : SettingsAction

    data class ShowDialog(val dialog: SettingsDialog) : SettingsAction
    data object DismissDialog : SettingsAction

    /** [required] is the translated word, so the gate survives localisation. */
    data class ConfirmResetProgress(val typed: String, val required: String) : SettingsAction
    data object ConfirmDeleteLocalData : SettingsAction
}
