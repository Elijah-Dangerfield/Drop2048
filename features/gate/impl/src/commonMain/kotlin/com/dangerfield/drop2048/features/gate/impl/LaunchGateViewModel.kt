package com.dangerfield.drop2048.features.gate.impl

import androidx.lifecycle.viewModelScope
import com.dangerfield.drop2048.features.gate.BlockingGate
import com.dangerfield.drop2048.features.gate.LaunchGates
import com.dangerfield.drop2048.features.gate.LegalInputs
import com.dangerfield.drop2048.features.gate.MaintenanceInputs
import com.dangerfield.drop2048.features.gate.NoticeGate
import com.dangerfield.drop2048.features.gate.StoreListing
import com.dangerfield.drop2048.features.gate.UpgradeInputs
import com.dangerfield.drop2048.features.gate.resolveLaunchGates
import com.dangerfield.drop2048.libraries.config.AppConfigRepository
import com.dangerfield.drop2048.libraries.core.BuildInfo
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import com.dangerfield.drop2048.libraries.gameconfig.LegalForceReacceptBelow
import com.dangerfield.drop2048.libraries.gameconfig.LegalPrivacyUrl
import com.dangerfield.drop2048.libraries.gameconfig.LegalPrivacyVersion
import com.dangerfield.drop2048.libraries.gameconfig.LegalTermsUrl
import com.dangerfield.drop2048.libraries.gameconfig.LegalTermsVersion
import com.dangerfield.drop2048.libraries.gameconfig.MaintenanceMessage
import com.dangerfield.drop2048.libraries.gameconfig.MaintenanceMode
import com.dangerfield.drop2048.libraries.gameconfig.MinSupportedVersionCode
import com.dangerfield.drop2048.libraries.gameconfig.SoftUpdateVersionCode
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock

/**
 * The launch gates, resolved live.
 *
 * **Every config value is read at the moment the gate is decided, never captured
 * at construction.** That is the difference between an operator's change landing
 * on the next foreground and landing after a force-quit nobody is going to
 * perform mid-incident. The trigger is `configStream()` combined with the
 * `AppData` the legal record lives in, so a config refresh *or* an acceptance
 * re-runs the whole decision.
 *
 * **Nothing thrown in here can raise a gate.** The resolution is wrapped in
 * [Catching] and a failure resolves to [LaunchGates] with nothing gated — a
 * malformed value on an `Int` key throws rather than falls back on debug builds
 * (`getValueRecursive` calls `throwIfDebug`), and an exception escaping
 * `handleAction` would also kill the action loop for the rest of the process.
 * Both failure modes end at "the player plays".
 *
 * Scoped as a singleton and driven from `App.kt` rather than from a nav
 * destination: a blocking gate is rendered *instead of* the nav host, so there is
 * no back stack entry to pop and no deep link that can land behind it.
 */
@SingleIn(AppScope::class)
@Inject
class LaunchGateViewModel(
    private val appCache: AppCache,
    private val appConfigRepository: AppConfigRepository,
    private val minSupportedVersion: MinSupportedVersionCode,
    private val softUpdateVersion: SoftUpdateVersionCode,
    private val maintenanceMode: MaintenanceMode,
    private val maintenanceMessage: MaintenanceMessage,
    private val termsVersion: LegalTermsVersion,
    private val privacyVersion: LegalPrivacyVersion,
    private val forceReacceptBelow: LegalForceReacceptBelow,
    private val termsUrl: LegalTermsUrl,
    private val privacyUrl: LegalPrivacyUrl,
    private val clock: Clock,
) : SEAViewModel<LaunchGateState, LaunchGateEvent, LaunchGateAction>(
    initialStateArg = LaunchGateState(),
) {

    /**
     * The maintenance banner the player has closed this session, by its text.
     *
     * A plain field rather than state: `state` lags `updateState` by a dispatch,
     * and this is read on the very next resolve, which an `AppData` write can
     * trigger a millisecond later. Deliberately not persisted — an incident that
     * is still running should say so again on the next launch.
     */
    private var dismissedBanner: String? = null

    /** The last gate reported, so `gate.raised` fires on edges only. */
    private var lastReported: String? = null

    private val logger = KLog.withTag("LaunchGate")

    init {
        viewModelScope.launch {
            combine(appConfigRepository.configStream(), appCache.updates) { _, data -> data }
                .collect { data -> takeAction(LaunchGateAction.Resolve(data)) }
        }
        viewModelScope.launch {
            Catching { takeAction(LaunchGateAction.Resolve(appCache.get())) }
                .logOnFailure { "Failed the first launch-gate resolve" }
        }
    }

    override suspend fun handleAction(action: LaunchGateAction) {
        when (action) {
            is LaunchGateAction.Resolve -> action.resolve(action.appData)
            is LaunchGateAction.AcceptLegal -> record(action.termsVersion, action.privacyVersion)
            is LaunchGateAction.DismissNotice -> action.dismiss(action.notice)
            LaunchGateAction.OpenStore -> sendEvent(LaunchGateEvent.OpenLink(StoreListing.url()))
            LaunchGateAction.OpenTerms -> sendEvent(LaunchGateEvent.OpenLink(termsUrl()))
            LaunchGateAction.OpenPrivacy -> sendEvent(LaunchGateEvent.OpenLink(privacyUrl()))
        }
    }

    private suspend fun LaunchGateAction.resolve(data: AppData) {
        if (data.legalAcceptedAt == 0L) {
            // A first launch has no earlier acceptance for a version bump to be
            // measured against, so the versions in hand are recorded and nothing
            // is gated. The write re-enters this flow with the record in place.
            // This is also what makes the blocking re-accept sheet unreachable on
            // a fresh install, whatever `forceReacceptBelow` says.
            record(termsVersion(), privacyVersion())
            return
        }

        val gates = Catching { gatesFor(data) }
            .logOnFailure { "Failed to resolve the launch gates; letting the player play" }
            .getOrNull()
            ?: LaunchGates()

        report(gates)
        updateState { it.copy(blocking = gates.blocking, notice = gates.notice) }
    }

    /**
     * `gate.raised`, on the edge only.
     *
     * How many installs are behind the wall is the first number anyone wants
     * during an incident, and it is one the backend cannot answer — the whole
     * point of a maintenance gate is that our server is the thing that is down.
     *
     * Keyed on the gate's identity rather than the whole object, so an operator
     * rewording a maintenance message does not read as a second incident, and
     * every resolve (which is every `AppData` write) does not emit.
     */
    private fun report(gates: LaunchGates) {
        val raised = gates.blocking?.eventName() ?: gates.notice?.eventName()
        if (raised == lastReported) return
        lastReported = raised
        if (raised == null) return

        logger.logEvent(
            "gate.raised",
            "gate" to raised,
            "blocking" to (gates.blocking != null),
        )
    }

    private fun gatesFor(data: AppData): LaunchGates = resolveLaunchGates(
        upgrade = UpgradeInputs(
            installedVersionCode = BuildInfo.versionCode,
            minSupportedVersionCode = minSupportedVersion(),
            softUpdateVersionCode = softUpdateVersion(),
            softUpdateDismissedFor = data.softUpdateDismissedFor,
        ),
        maintenance = MaintenanceInputs(
            mode = maintenanceMode(),
            message = maintenanceMessage(),
            dismissedBanner = dismissedBanner,
        ),
        legal = LegalInputs(
            termsVersion = termsVersion(),
            privacyVersion = privacyVersion(),
            acceptedTermsVersion = data.acceptedTermsVersion,
            acceptedPrivacyVersion = data.acceptedPrivacyVersion,
            forceReacceptBelow = forceReacceptBelow(),
            hasEverAccepted = true,
        ),
    )

    private suspend fun LaunchGateAction.dismiss(notice: NoticeGate) {
        when (notice) {
            // Persisted, so it does not come back every launch — but keyed on the
            // version it was dismissed at, so raising the soft-update target asks
            // again.
            is NoticeGate.SoftUpdate -> persist {
                it.copy(softUpdateDismissedFor = notice.versionCode)
            }

            // Closing the "terms have moved" banner is the acceptance, and the
            // copy on the banner says so. That is the non-material half of the
            // legal rule: continuing to play is consent, and the blocking sheet
            // is what a material change gets instead.
            is NoticeGate.LegalUpdated -> record(notice.termsVersion, notice.privacyVersion)

            is NoticeGate.Maintenance -> {
                dismissedBanner = notice.message
                updateState { it.copy(notice = null) }
            }
        }
    }

    /**
     * Records acceptance of exactly the versions that were on screen.
     *
     * Both are parameters rather than a re-read of the config: a refresh landing
     * between the prompt and the tap would otherwise write consent to a version
     * nobody was ever shown. `maxOf` because the record only moves forward — a
     * config that regressed `termsVersion` must not un-accept anything.
     */
    private suspend fun record(terms: Int, privacy: Int) {
        val now = clock.now().toEpochMilliseconds()
        persist {
            it.copy(
                acceptedTermsVersion = maxOf(it.acceptedTermsVersion, terms),
                acceptedPrivacyVersion = maxOf(it.acceptedPrivacyVersion, privacy),
                legalAcceptedAt = now,
            )
        }
    }

    private suspend fun persist(transform: (AppData) -> AppData) {
        Catching { appCache.update(transform) }
            .logOnFailure { "Failed to persist a launch-gate decision" }
    }
}

data class LaunchGateState(
    val blocking: BlockingGate? = null,
    val notice: NoticeGate? = null,
)

/**
 * Stable, low-cardinality names for the dashboard. Not the class name: these are
 * dashboard keys, and a rename in Kotlin should not silently start a new series.
 */
private fun BlockingGate.eventName(): String = when (this) {
    BlockingGate.ForceUpdate -> "force_update"
    is BlockingGate.Maintenance -> "maintenance"
    is BlockingGate.ReacceptLegal -> "legal_reaccept"
}

private fun NoticeGate.eventName(): String = when (this) {
    is NoticeGate.Maintenance -> "maintenance_banner"
    is NoticeGate.LegalUpdated -> "legal_updated"
    is NoticeGate.SoftUpdate -> "soft_update"
}

sealed interface LaunchGateEvent {
    /** Terms, privacy and the store listing are all pages, so they open in a browser. */
    data class OpenLink(val url: String) : LaunchGateEvent
}

sealed interface LaunchGateAction {
    /** Config or the record changed; re-decide. */
    data class Resolve(val appData: AppData) : LaunchGateAction

    /**
     * The versions travel on the action rather than being read back off `state`,
     * which lags `updateState` by a dispatch.
     */
    data class AcceptLegal(val termsVersion: Int, val privacyVersion: Int) : LaunchGateAction

    data class DismissNotice(val notice: NoticeGate) : LaunchGateAction

    data object OpenStore : LaunchGateAction
    data object OpenTerms : LaunchGateAction
    data object OpenPrivacy : LaunchGateAction
}
