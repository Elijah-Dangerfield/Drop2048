package com.dangerfield.drop2048.features.debug.impl

import androidx.lifecycle.viewModelScope
import com.dangerfield.drop2048.features.debug.DebugController
import com.dangerfield.drop2048.libraries.ads.AdDiagnostics
import com.dangerfield.drop2048.libraries.ads.AdGate
import com.dangerfield.drop2048.libraries.ads.AdGateSnapshot
import com.dangerfield.drop2048.libraries.ads.AdPlacement
import com.dangerfield.drop2048.libraries.ads.AdShowResult
import com.dangerfield.drop2048.libraries.ads.HouseAds
import com.dangerfield.drop2048.libraries.ads.InterstitialGate
import com.dangerfield.drop2048.libraries.billing.ProGrant
import com.dangerfield.drop2048.features.debug.DebugMenuGate
import com.dangerfield.drop2048.features.debug.DebugOverrides
import com.dangerfield.drop2048.features.debug.Diagnostics
import com.dangerfield.drop2048.features.debug.DiagnosticsSettings
import com.dangerfield.drop2048.features.debug.PresetBoard
import com.dangerfield.drop2048.features.debug.TranscriptLine
import com.dangerfield.drop2048.features.debug.describe
import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.Special
import com.dangerfield.drop2048.libraries.cascade.autoplay.Autoplay
import com.dangerfield.drop2048.libraries.cascade.autoplay.Policy
import com.dangerfield.drop2048.libraries.cascade.blockOf
import com.dangerfield.drop2048.libraries.core.BuildInfo
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.versionString
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ClearableDao
import com.dangerfield.drop2048.libraries.flowroutines.DispatcherProvider
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import com.dangerfield.drop2048.libraries.flowroutines.collectIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.random.Random
import me.tatarka.inject.annotations.Inject

/**
 * SPEC 19's QA menu.
 *
 * **Most of this is a formatter over things that already exist**, which is the
 * point SPEC 4.1 was making six chunks ago: the engine is a pure function of
 * state and a seed, so "load a preset board" is a `GameState`, "replay the last
 * run" is a seed already sitting in `run_record`, "force an N-step cascade" is a
 * board arranged to cascade, and the transcript log is `Transcript.describe()`.
 * Almost nothing here is new machinery, and the pieces that are new — the
 * session flag and the passphrase — exist to stop the menu lying about the
 * player rather than to make it work.
 *
 * ### Everything is applied to the *next* run
 *
 * There is no live-board editing here and that is deliberate rather than
 * unfinished. A menu that mutates a running `GameState` has to reach across the
 * ViewModel that owns it, and every one of those reaches is a place where a
 * cascade in flight, a lock delay and a saved-run write can disagree with each
 * other. Starting a run from a prepared state is the same capability with none
 * of that: the engine cannot tell the difference, because a `GameState` is a
 * `GameState`.
 */
@Suppress("TooManyFunctions", "LongParameterList")
@Inject
class DebugViewModel(
    private val controller: DebugController,
    private val diagnostics: Diagnostics,
    private val gate: DebugMenuGate,
    private val proGrant: ProGrant,
    private val adGate: AdGate,
    private val interstitials: InterstitialGate,
    private val adDiagnostics: AdDiagnostics,
    private val houseAds: HouseAds,
    private val appCache: AppCache,
    private val clearableDaos: Set<ClearableDao>,
    private val dispatchers: DispatcherProvider,
) : SEAViewModel<DebugState, DebugEvent, DebugAction>(
    initialStateArg = DebugState(
        appVersion = BuildInfo.versionString(),
        needsPassphrase = !BuildInfo.isDebug,
    ),
) {

    init {
        // Opening the menu is what marks the session, not touching a control in
        // it. See `DebugController` for why the session rather than the run is
        // the unit.
        controller.markDebugSession()
        takeAction(DebugAction.Load)
        controller.overrides.collectIn(viewModelScope) {
            takeAction(DebugAction.OverridesChanged(it))
        }
        diagnostics.settings.collectIn(viewModelScope) {
            takeAction(DebugAction.DiagnosticsChanged(it))
        }
        diagnostics.lastResolution
            .map { it.describe() }
            .collectIn(viewModelScope) { takeAction(DebugAction.TranscriptChanged(it)) }
        gate.isUnlocked.collectIn(viewModelScope) { takeAction(DebugAction.UnlockChanged(it)) }
        proGrant.granted.collectIn(viewModelScope) { takeAction(DebugAction.ProChanged(it)) }
        houseAds.isSelected.collectIn(viewModelScope) { takeAction(DebugAction.RefreshAds) }
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun handleAction(action: DebugAction) {
        when (action) {
            DebugAction.Load -> action.load()
            is DebugAction.OverridesChanged -> action.updateState {
                it.copy(overrides = action.overrides)
            }

            is DebugAction.DiagnosticsChanged -> action.updateState {
                it.copy(diagnostics = action.settings)
            }

            is DebugAction.TranscriptChanged -> action.updateState {
                it.copy(transcript = action.lines)
            }

            is DebugAction.UnlockChanged -> action.updateState {
                it.copy(needsPassphrase = !action.unlocked)
            }

            is DebugAction.ProChanged -> action.updateState { it.copy(proGranted = action.granted) }

            DebugAction.Back -> sendEvent(DebugEvent.NavigateBack)

            is DebugAction.SubmitPassphrase -> action.submitPassphrase()

            is DebugAction.SetStartLevel -> override { it.copy(startLevel = action.level) }
            is DebugAction.SetTickInterval -> override { it.copy(tickIntervalMs = action.ms) }
            is DebugAction.SetPreset -> override { it.copy(preset = action.preset) }
            is DebugAction.QueueBlock -> override {
                it.copy(forcedBlocks = it.forcedBlocks + action.block)
            }

            DebugAction.ClearQueue -> override { it.copy(forcedBlocks = emptyList()) }
            DebugAction.ToggleInvincible -> override { it.copy(invincible = !it.invincible) }
            DebugAction.ToggleFreezeTimer -> override { it.copy(freezeTimer = !it.freezeTimer) }
            is DebugAction.SetSeed -> override { it.copy(seed = action.seed) }
            DebugAction.ClearOverrides -> action.clearOverrides()
            DebugAction.StartForcedRun -> action.startForcedRun()

            is DebugAction.RunSoak -> action.runSoak()

            DebugAction.ToggleFrameRate -> diagnose { it.copy(showFrameRate = !it.showFrameRate) }
            DebugAction.ToggleTick -> diagnose { it.copy(showTick = !it.showTick) }
            DebugAction.ToggleCoordinates -> diagnose {
                it.copy(showCellCoordinates = !it.showCellCoordinates)
            }

            DebugAction.ToggleMergeArrows -> diagnose { it.copy(showMergeArrows = !it.showMergeArrows) }
            DebugAction.ToggleTranscript -> diagnose { it.copy(showTranscript = !it.showTranscript) }

            is DebugAction.SetProGranted -> proGrant.setGranted(action.granted)
            DebugAction.ResetPurchases -> proGrant.setGranted(false)

            DebugAction.ResetTutorial -> action.resetTutorial()
            DebugAction.ResetAllLocalData -> action.resetAllLocalData()
            DebugAction.DumpState -> action.dumpState()
            DebugAction.CopySeed -> action.copySeed()
            DebugAction.CopyDump -> action.copyDump()
            DebugAction.RollSeed -> override { it.copy(seed = Random.nextLong()) }
            DebugAction.ForceCrash -> throw DebugTestCrash()

            DebugAction.RefreshAds -> action.refreshAds()
            is DebugAction.SelectHouseAds -> action.selectHouseAds()
            is DebugAction.SetForcedAdOutcome -> action.setForcedAdOutcome()
            is DebugAction.SetAdReportsReady -> action.setAdReportsReady()
            DebugAction.NoteRunFinished -> action.noteRunFinished()
            is DebugAction.ShowRewardedNow -> action.showRewardedNow()
            DebugAction.ShowInterstitialNow -> action.showInterstitialNow()
            DebugAction.OpenConfigOverrides -> sendEvent(DebugEvent.OpenConfigOverrides)
        }
    }

    /**
     * The gate's own inputs, read rather than guessed.
     *
     * Called after everything that can move one of them, because the value of
     * this panel is that it agrees with the gate — a readout refreshed only on
     * open would show a cooldown that expired two taps ago and send the tester
     * looking for a bug in the policy.
     */
    private suspend fun DebugAction.refreshAds() {
        val snapshot = Catching { adDiagnostics.snapshot() }
            .logOnFailure { "Could not read the ad gate snapshot" }
            .getOrNull()
        updateState {
            it.copy(
                ads = it.ads.copy(
                    houseAdsAvailable = houseAds.network != null,
                    houseAdsSelected = houseAds.isSelected.value,
                    forcedOutcome = houseAds.forcedOutcome.value,
                    reportsReady = houseAds.reportsReady.value,
                    snapshot = snapshot,
                )
            )
        }
    }

    private suspend fun DebugAction.SelectHouseAds.selectHouseAds() {
        houseAds.select(useHouseAds)
        takeAction(DebugAction.RefreshAds)
    }

    private suspend fun DebugAction.SetForcedAdOutcome.setForcedAdOutcome() {
        houseAds.forceOutcome(result)
        takeAction(DebugAction.RefreshAds)
    }

    private suspend fun DebugAction.SetAdReportsReady.setAdReportsReady() {
        houseAds.reportReady(ready)
        takeAction(DebugAction.RefreshAds)
    }

    /**
     * SPEC 12's fourth-run rule, satisfied without playing four runs.
     *
     * `noteRunFinished` is the same call the game makes when a board stacks out
     * and it shows nothing by construction, so this is the counter and only the
     * counter. It is here beside the config overrides rather than instead of
     * them: overriding `minSessionRuns` proves the key is wired, and tapping
     * this proves the counter is.
     */
    private suspend fun DebugAction.noteRunFinished() {
        interstitials.noteRunFinished()
        takeAction(DebugAction.RefreshAds)
    }

    /**
     * SPEC 19's "force show: rewarded video".
     *
     * Straight through [AdGate], not around it, so what the tester sees is the
     * real path: the rewarded clock held for the length of the ad, the timestamp
     * written on the way out, and — on anything but a deliberate dismissal — the
     * grant the call sites would have made.
     */
    private suspend fun DebugAction.ShowRewardedNow.showRewardedNow() {
        updateState { it.copy(ads = it.ads.copy(lastResult = null, showing = true)) }
        val outcome = adGate.showRewarded(placement)
        updateState {
            it.copy(ads = it.ads.copy(lastResult = outcome.toString(), showing = false))
        }
        takeAction(DebugAction.RefreshAds)
    }

    /**
     * SPEC 19's "force show: interstitial", and it is deliberately not a force.
     *
     * It calls [InterstitialGate.showIfReady] — the one method that can show an
     * interstitial, with the one caller it has always had — so every gate in
     * SPEC 12.3 still runs. A button that reached past the policy to the network
     * would prove the SDK works and prove nothing about the app, and the thing
     * that has never been seen here is not an AdMob interstitial, it is this
     * app's decision to serve one.
     *
     * When it refuses, [AdGateSnapshot.blockedReason] on the panel above says
     * which rule did it.
     */
    private suspend fun DebugAction.showInterstitialNow() {
        updateState { it.copy(ads = it.ads.copy(lastResult = null, showing = true)) }
        val shown = interstitials.showIfReady()
        val snapshot = Catching { adDiagnostics.snapshot() }
            .logOnFailure { "Could not read the ad gate snapshot" }
            .getOrNull()
        updateState {
            it.copy(
                ads = it.ads.copy(
                    showing = false,
                    lastResult = if (shown) {
                        DebugCopy.AdShown
                    } else {
                        snapshot?.blockedReason ?: DebugCopy.AdNotShown
                    },
                )
            )
        }
        takeAction(DebugAction.RefreshAds)
    }

    private fun DebugAction.load() {
        takeAction(DebugAction.RefreshAds)
    }

    private suspend fun DebugAction.SubmitPassphrase.submitPassphrase() {
        val accepted = gate.unlock(typed)
        updateState { it.copy(passphraseRejected = !accepted) }
    }

    /**
     * Clears the run overrides and nothing else.
     *
     * The diagnostics switches survive on purpose: they change what is drawn and
     * not what is played, so clearing them here would take a tester's overlay
     * away every time they reset a board. The debug *session* survives too, and
     * cannot be cleared from here at all — see `DebugController`.
     */
    private suspend fun DebugAction.clearOverrides() {
        controller.update { DebugOverrides.None }
        sendEvent(DebugEvent.Message(DebugMessage.OverridesCleared))
    }

    /**
     * SPEC 19's autoplay soak, on `tools/balance`'s own policies (SPEC 4.4).
     *
     * Reusing them rather than writing a scripted player here is what makes the
     * result comparable to the numbers the spawn table was measured against. A
     * second, subtly different Greedy would produce a level that looks like the
     * harness's and is not.
     *
     * It runs on the injected default dispatcher because a few thousand drops of
     * a pure state machine is real work, and the main thread is drawing the menu
     * that is about to show the answer. Injected rather than `Dispatchers.Default`
     * so a test sees the result on the same scheduler it asked for it on.
     */
    private suspend fun DebugAction.RunSoak.runSoak() {
        updateState { it.copy(soak = null, soaking = true) }
        val seed = state.overrides.seed ?: Random.nextLong()
        val summary = withContext(dispatchers.default) {
            val result = Autoplay.soak(
                from = Cascade.newGame(seed),
                policy = policy,
                random = Random(seed),
            )
            SoakSummary(
                policy = result.policy,
                cheats = policy.cheats,
                seed = seed,
                level = result.level,
                score = result.score,
                drops = result.drops,
                merges = result.merges,
                bursts = result.bursts,
                deepestCascade = result.deepestCascade,
                faults = result.faults,
                hitDropCap = result.hitDropCap,
            )
        }
        updateState { it.copy(soak = summary, soaking = false) }
    }

    /**
     * Starts a run under the current overrides, and throws away the Endless save
     * slot on the way.
     *
     * **This was a bug found by using the menu, not by a test.** "Start run"
     * navigated to the game screen, which resumes a saved run if there is one —
     * so a tester who had a run in progress got that board back and the preset
     * they had just chosen was never applied. Nothing looked broken; the wrong
     * board simply appeared, which is the worst shape a QA tool can fail in.
     *
     * The slot is cleared through [AppCache] rather than through
     * `SavedRunStore`, which owns the format: a feature impl may not depend on
     * another feature's impl, and the alternative — a seam on the game's api just
     * for this — would be a production type that exists for the debug menu. What
     * this touches is the one nullable string, and `SavedRunStore` treats a null
     * slot as "no saved run" by construction.
     */
    private suspend fun DebugAction.startForcedRun() {
        Catching { appCache.update { data -> data.copy(savedRun = null) } }
            .logOnFailure { "Could not clear the saved run before a forced one" }
        sendEvent(DebugEvent.StartRun)
    }

    private suspend fun DebugAction.resetTutorial() {
        Catching { appCache.update { data -> data.copy(hasUserOnboarded = false) } }
            .logOnFailure { "Could not reset the tutorial flag" }
        sendEvent(DebugEvent.Message(DebugMessage.TutorialReset))
    }

    /**
     * Every table, plus the in-flight runs.
     *
     * `Set<ClearableDao>` rather than a list of DAOs, for the reason C4 gave when
     * it declared the multibinding and C11 gave when it first consumed one: a
     * table added in a later chunk is wiped by this without anybody remembering
     * to come back here. Each DAO gets its own `Catching` so one failing table
     * cannot leave the rest standing.
     */
    private suspend fun DebugAction.resetAllLocalData() {
        clearableDaos.forEach { dao ->
            Catching { dao.deleteAll() }.logOnFailure { "Could not clear a table" }
        }
        Catching { appCache.update { data -> data.copy(savedRun = null) } }
            .logOnFailure { "Could not clear the in-flight run" }
        sendEvent(DebugEvent.Message(DebugMessage.LocalDataReset))
    }

    private suspend fun DebugAction.dumpState() {
        val dump = DebugStateDump.of(
            appVersion = BuildInfo.versionString(),
            buildType = if (BuildInfo.isDebug) "debug" else "release",
            debugSession = controller.isDebugSession.value,
            overrides = controller.overrides.value,
            diagnostics = diagnostics.settings.value,
            data = Catching { appCache.get() }.getOrNull(),
        )
        updateState { it.copy(stateDump = dump) }
    }

    /**
     * The seed, so a surprising run can be reproduced somewhere else.
     *
     * `run_record.seed` has been populated since C4 (SPEC 11), so replaying a
     * finished run is pasting its seed in here — there is no replay machinery to
     * build, which is what SPEC 4.1 bought.
     */
    private suspend fun DebugAction.copySeed() {
        state.overrides.seed?.let { sendEvent(DebugEvent.Copy(it.toString())) }
    }

    private suspend fun DebugAction.copyDump() {
        state.stateDump?.let { sendEvent(DebugEvent.Copy(it)) }
    }

    private suspend fun override(transform: (DebugOverrides) -> DebugOverrides) {
        controller.update(transform)
    }

    private suspend fun diagnose(transform: (DiagnosticsSettings) -> DiagnosticsSettings) {
        diagnostics.update(transform)
    }
}

/** SPEC 19's "force a test crash". Its own type so a crash report says what it was. */
class DebugTestCrash : RuntimeException("Deliberate crash from the debug menu")

data class DebugState(
    val appVersion: String = "",

    /** True on a release build until the passphrase is right. Nothing is drawn until then. */
    val needsPassphrase: Boolean = false,
    val passphraseRejected: Boolean = false,

    val overrides: DebugOverrides = DebugOverrides.None,
    val diagnostics: DiagnosticsSettings = DiagnosticsSettings.Off,
    val transcript: List<TranscriptLine> = emptyList(),

    val proGranted: Boolean = false,

    val soaking: Boolean = false,
    val soak: SoakSummary? = null,

    val stateDump: String? = null,

    val ads: AdToolsState = AdToolsState(),
)

/**
 * SPEC 19's ad tools, and the gate's own inputs beside them.
 *
 * [snapshot] is the whole reason this is a panel rather than two buttons. An
 * interstitial that does not appear is silent, and the eight things that could
 * have silenced it are all invisible; C10 gave `ads.interstitial_blocked` a
 * named reason so a dashboard could be asked which one, and this puts the same
 * word on the device before the ad is asked for rather than after.
 */
data class AdToolsState(
    /** Whether this build contains a house network at all. False in every release build. */
    val houseAdsAvailable: Boolean = false,
    val houseAdsSelected: Boolean = false,
    val forcedOutcome: AdShowResult? = null,
    val reportsReady: Boolean = true,
    val snapshot: AdGateSnapshot? = null,

    /** An ad is on screen. The menu is behind it, and the buttons must not restack. */
    val showing: Boolean = false,

    /** The outcome, or the block reason, from the last forced show. */
    val lastResult: String? = null,
)

/** One finished soak, flattened for display. */
data class SoakSummary(
    val policy: String,
    val cheats: Boolean,
    val seed: Long,
    val level: Int,
    val score: Long,
    val drops: Int,
    val merges: Int,
    val bursts: Int,
    val deepestCascade: Int,
    val faults: List<String>,
    val hitDropCap: Boolean,
)

/** The two-line confirmations the menu gives back. Enum, not a string, so the copy stays in the screen. */
enum class DebugMessage {
    OverridesCleared,
    TutorialReset,
    LocalDataReset,
}

sealed interface DebugEvent {
    data object NavigateBack : DebugEvent

    /** Start a run under the current overrides. The game screen is the destination. */
    data object StartRun : DebugEvent

    /**
     * Put [text] on the clipboard.
     *
     * An event rather than a call, because the clipboard is a composition-scoped
     * thing (`LocalClipboardManager`) and a ViewModel that reached for it would
     * need a platform seam per target for a control only QA ever presses.
     */
    data class Copy(val text: String) : DebugEvent

    data class Message(val message: DebugMessage) : DebugEvent

    /** SPEC 10's keys, on their own screen. See `ConfigOverridesRoute`. */
    data object OpenConfigOverrides : DebugEvent
}

sealed interface DebugAction {
    data object Load : DebugAction
    data class OverridesChanged(val overrides: DebugOverrides) : DebugAction
    data class DiagnosticsChanged(val settings: DiagnosticsSettings) : DebugAction
    data class TranscriptChanged(val lines: List<TranscriptLine>) : DebugAction
    data class UnlockChanged(val unlocked: Boolean) : DebugAction
    data class ProChanged(val granted: Boolean) : DebugAction
    data object Back : DebugAction

    data class SubmitPassphrase(val typed: String) : DebugAction

    data class SetStartLevel(val level: Int?) : DebugAction
    data class SetTickInterval(val ms: Int?) : DebugAction
    data class SetPreset(val preset: PresetBoard?) : DebugAction
    data class QueueBlock(val block: Block) : DebugAction
    data object ClearQueue : DebugAction
    data object ToggleInvincible : DebugAction
    data object ToggleFreezeTimer : DebugAction
    data class SetSeed(val seed: Long?) : DebugAction
    data object ClearOverrides : DebugAction
    data object StartForcedRun : DebugAction

    data class RunSoak(val policy: Policy) : DebugAction

    data object ToggleFrameRate : DebugAction
    data object ToggleTick : DebugAction
    data object ToggleCoordinates : DebugAction
    data object ToggleMergeArrows : DebugAction
    data object ToggleTranscript : DebugAction

    data class SetProGranted(val granted: Boolean) : DebugAction
    data object ResetPurchases : DebugAction

    data object ResetTutorial : DebugAction
    data object ResetAllLocalData : DebugAction
    data object DumpState : DebugAction
    data object CopySeed : DebugAction
    data object CopyDump : DebugAction
    data object RollSeed : DebugAction
    data object ForceCrash : DebugAction

    data object RefreshAds : DebugAction
    data class SelectHouseAds(val useHouseAds: Boolean) : DebugAction
    data class SetForcedAdOutcome(val result: AdShowResult?) : DebugAction
    data class SetAdReportsReady(val ready: Boolean) : DebugAction
    data object NoteRunFinished : DebugAction
    data class ShowRewardedNow(val placement: AdPlacement) : DebugAction
    data object ShowInterstitialNow : DebugAction
    data object OpenConfigOverrides : DebugAction
}

/** The block palette the queue picker offers: every tier, plus the three specials. */
val DebugBlockChoices: List<Block> =
    BlockValue.entries.map { blockOf(it) } + Special.entries.map { blockOf(it) }
