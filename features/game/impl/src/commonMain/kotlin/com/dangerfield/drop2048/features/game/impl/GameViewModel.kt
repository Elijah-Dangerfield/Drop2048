package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.features.debug.DebugController
import com.dangerfield.drop2048.features.debug.Diagnostics
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.RunStatus
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.Direction
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.GameEvent
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.cascade.Input
import com.dangerfield.drop2048.libraries.cascade.Transition
import androidx.lifecycle.viewModelScope
import com.dangerfield.drop2048.libraries.achievements.AchievementId
import com.dangerfield.drop2048.libraries.achievements.AchievementsRepository
import com.dangerfield.drop2048.libraries.achievements.outcomeWith
import com.dangerfield.drop2048.libraries.ads.AdGate
import com.dangerfield.drop2048.libraries.ads.AdPlacement
import com.dangerfield.drop2048.libraries.ads.InterstitialGate
import com.dangerfield.drop2048.libraries.ads.RewardOutcome
import com.dangerfield.drop2048.libraries.ads.RunActivity
import com.dangerfield.drop2048.libraries.billing.PaywallCoordinator
import com.dangerfield.drop2048.libraries.billing.PaywallTrigger
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.features.settings.ControlScheme
import com.dangerfield.drop2048.features.settings.asEnumOr
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.drop2048.AppLifecycle
import com.dangerfield.drop2048.libraries.drop2048.AppLifecycleObserver
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import com.dangerfield.drop2048.libraries.flowroutines.collectIn
import com.dangerfield.drop2048.libraries.gameconfig.RewardedContinuesPerRun
import com.dangerfield.drop2048.libraries.progress.GameMode
import com.dangerfield.drop2048.libraries.progress.ProgressRepository
import com.dangerfield.drop2048.libraries.progress.RunRecord
import com.dangerfield.drop2048.libraries.progress.daily.DailyAttempt
import com.dangerfield.drop2048.libraries.progress.daily.DailyRepository
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboard
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboards
import com.dangerfield.drop2048.libraries.sharing.ShareResult
import com.dangerfield.drop2048.libraries.ui.system.Cue
import com.dangerfield.drop2048.libraries.ui.system.Motion
import com.dangerfield.drop2048.libraries.ui.system.reduced
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * The clock the engine does not have (SPEC 4.1).
 *
 * Three things live here and nowhere else: the drop timer, the lock delay, and
 * **transcript playback**. Everything else is the engine's, and this class never
 * decides what a merge does.
 *
 * ### Transcript playback is the architecture
 *
 * A lock returns a `Transition` whose `state` is the board *after* every merge,
 * burst and settle has already happened. The screen must not show that state
 * yet. So the lock does four things in order:
 *
 * 1. keep the resolved [GameState] in [engine], unpublished;
 * 2. re-derive the intermediate boards with [framesFor];
 * 3. move the published state to [GamePhase.Resolving];
 * 4. hand the frames to a driver coroutine that feeds them back in as
 *    [GameAction.ShowFrame].
 *
 * Everything still arrives on the one action channel, so there is exactly one
 * writer of [engine] and no lock anywhere. And SPEC 6's "input during resolution
 * is ignored" costs one guard at the top of [handleAction] instead of being a
 * special case threaded through the input handling: while the phase is
 * `Resolving` there simply is no falling block to move.
 *
 * Pausing mid-cascade (SPEC 18.9) falls out of the same shape. The driver is
 * cancelled and [frameIndex] is already the snapshot; resuming relaunches from
 * there and the cascade finishes before the ticker starts again.
 *
 * ### Resume is the same snapshot, written to disk
 *
 * The app dying is a pause nobody got to handle, so persistence reuses the pause
 * seam rather than adding one. A [SavedRun] is written at every lock and at every
 * pause, and it carries the engine state, the run's tallies, and — when a cascade
 * was on screen — the board and score it started from plus the transcript and
 * [frameIndex]. Restoring re-derives the frames with [framesFor] and relaunches
 * the driver from that index, so the cascade finishes before the ticker starts
 * and before any input is accepted. That is SPEC 18.9 for a force-quit and for a
 * backgrounding at the same time, through one path.
 *
 * ### The clock lives here
 *
 * The engine has none on purpose (SPEC 4.1), and `run_record` wants a duration.
 * [clock] is the injected `kotlin.time.Clock` the app already provides, and it is
 * only ever read at the edges of play: time accumulates while the phase is
 * `Playing` or `Resolving` and stops at every pause, so a run left paused
 * overnight does not report eight hours of playtime.
 */
@Suppress("TooManyFunctions", "LongParameterList")
@Inject
class GameViewModel(
    private val runFactory: RunFactory,
    private val appCache: AppCache,
    private val savedRunStore: SavedRunStore,
    private val progress: ProgressRepository,
    private val daily: DailyRepository,
    private val achievements: AchievementsRepository,
    private val leaderboards: Leaderboards,
    private val clock: Clock,
    private val appLifecycle: AppLifecycle,
    private val debug: DebugController,
    private val diagnostics: Diagnostics,
    private val adGate: AdGate,
    private val interstitials: InterstitialGate,
    private val runActivity: RunActivity,
    private val paywall: PaywallCoordinator,
    private val continuesPerRun: RewardedContinuesPerRun,
) : SEAViewModel<GameUiState, GameEffect, GameAction>(initialStateArg = GameUiState()) {

    private val logger = KLog.withTag("Game")

    private var started: StartedRun = runFactory.newRun()
    private var engine: GameState = started.state
    private var tally: RunTally = RunTally()

    /**
     * The guided run (SPEC 13), which lives here rather than in its own screen
     * because it is the game screen with the clock switched off.
     *
     * It takes the config the run factory resolved rather than
     * `EngineConfig.Default`, so a remote change to `board.rows` moves the
     * scripted boards with it instead of leaving the tutorial playing on a board
     * shape the game no longer has.
     */
    private val tutorial = TutorialRunner(started.state.config)

    /**
     * The UTC day the Daily run in flight belongs to (SPEC 14), ISO-formatted,
     * null in Endless.
     *
     * Read at the end of the run rather than re-derived from the clock, because
     * an attempt started at 23:58 UTC and finished after midnight belongs to the
     * board it began on. It survives a force-quit through [SavedRun.dailyDate].
     */
    private var dailyDate: String? = null

    /** Epoch millis the current stretch of play began, null while not playing. */
    private var playingSince: Long? = null

    /**
     * SPEC 17's two decision-time instruments, and the reason C8 exists (L41).
     *
     * Per-run rather than per-session: the numbers are properties of how the
     * player played *this board*, and a session's worth of them averaged
     * together would hide the thing L41 actually asks about, which is whether
     * the player is slower at level 20 than at level 2. Reset in [adopt] with
     * everything else a fresh run has to forget.
     */
    private var decisions = DecisionTimer()

    /**
     * The best score as it stood **before** this run, which is the only number
     * that can answer "did they beat it".
     *
     * It survives C3a's fix to [published], which stopped `GameUiState.best`
     * absorbing the live score, because the two answer different questions.
     * `best` is what the header shows and is re-read from the run store *after*
     * the run is recorded, so by the time the stacked-out sheet is drawn it
     * already includes the run being asked about. This is the number from before
     * it, kept in memory for exactly one comparison.
     */
    private var bestBeforeRun: Long = 0

    private var frames: List<PlaybackFrame> = emptyList()
    private var frameIndex: Int = 0

    /** What [frames] were derived from, kept so a save can hand them back. */
    private var resolutionSnapshot: SavedResolution? = null

    private var tickerJob: Job? = null
    private var lockJob: Job? = null
    private var playbackJob: Job? = null

    /**
     * SPEC 12's continue counter, per run. Reset in [adopt] with everything else
     * a fresh run has to forget — a cap that survived into the next run would
     * make the second run of a session the one with no continues in it, which is
     * the opposite of what a per-run cap means.
     */
    private var continuesUsed = 0

    /** The 8-second auto-decline (SPEC 8.4). Cancelled by either answer. */
    private var countdownJob: Job? = null

    private var lockPending = false
    private var lockResetUsed = false

    private var reduceMotion = false

    /** SPEC 11's confirm-before-quit, kept current by `settingsChanged`. */
    private var confirmBeforeQuit = true

    /**
     * The one sideways nudge the player made while a cascade was on screen.
     *
     * SPEC 6 says input during resolution is ignored, and it is: nothing here
     * reaches the engine mid-cascade. But *discarding* it turned out to be a
     * different rule, and a bad one — playing this on device, every move tapped
     * in the beat after a block landed vanished, so the block that had just
     * spawned went straight down the middle. The player did the right thing and
     * the game did nothing.
     *
     * So the last one is held and replayed once the next block exists. Only
     * sideways moves, and only the most recent: a queue would let a burst of
     * panicked taps march a block across the board on its own, and a buffered ▼
     * would drop a block two rows into a board the player had not looked at yet.
     */
    private var bufferedMove: Input? = null

    /**
     * SPEC 8.4's auto-pause on backgrounding, and the moment the mid-cascade
     * snapshot is written.
     *
     * Registered here rather than from a `LifecycleEventEffect` in the screen
     * because the snapshot has to be taken whether or not the composition is
     * still around to take it, and because the pause path already does exactly
     * the right thing.
     */
    private val lifecycleObserver = object : AppLifecycleObserver {
        override fun onEnterForeground() = Unit
        override fun onEnterBackground() {
            takeAction(GameAction.Pause)
        }
    }

    init {
        appLifecycle.addObserver(lifecycleObserver)
        takeAction(GameAction.Enter)
        // The settings screen opens *over* a live board, so this ViewModel is
        // still alive when the player flips left-handed or the landing outline.
        // Reading them once in `enter` would leave the board disagreeing with
        // the switch until the run ended, which is the inert-setting failure
        // C11 exists to remove. Palette, reduce motion and large numbers do not
        // need this: they come down a CompositionLocal from the theme.
        appCache.updates.collectIn(viewModelScope) { data ->
            takeAction(GameAction.SettingsChanged(data))
        }
    }

    override suspend fun handleAction(action: GameAction) {
        when (action) {
            GameAction.Enter -> action.enter()
            GameAction.Start -> action.start()
            GameAction.Tick -> action.tick()
            GameAction.LockNow -> action.lockNow()
            GameAction.MoveLeft -> action.move(Input.MoveLeft)
            GameAction.MoveRight -> action.move(Input.MoveRight)
            is GameAction.SteerTo -> action.steerTo(action.col)
            GameAction.HardDrop -> action.hardDrop()
            GameAction.Pause -> action.pause()
            GameAction.Resume -> action.resume()
            GameAction.Restart -> action.restart()
            GameAction.StartDaily -> action.startDaily()
            GameAction.TutorialAdvance -> action.tutorialAdvance()
            GameAction.TutorialSkip -> action.tutorialSkip()
            GameAction.ReplayTutorial -> action.replayTutorial()
            GameAction.Quit -> action.quit()
            GameAction.Share -> action.share()
            GameAction.ShowStats -> sendEvent(GameEffect.OpenStats)
            GameAction.ShowDaily -> sendEvent(GameEffect.OpenDaily)
            is GameAction.SettingsChanged -> action.settingsChanged(action.data)
            GameAction.OpenSettings -> sendEvent(GameEffect.OpenSettings)
            GameAction.ConfirmQuit -> action.leaveRun()
            GameAction.DismissQuitConfirm -> action.updateState { it.copy(confirmingQuit = false) }
            GameAction.DismissUnlocks -> action.updateState { it.copy(unlocked = emptyList()) }
            is GameAction.SetReduceMotion -> reduceMotion = action.enabled
            is GameAction.ShowFrame -> action.showFrame(action.index)
            GameAction.FinishResolution -> action.finishResolution()
            GameAction.ContinueAccept -> action.continueAccept()
            GameAction.ContinueDecline -> action.continueDecline()
            GameAction.ContinueAgain -> action.continueAgain()
            is GameAction.ContinueTick -> action.continueTick(action.secondsLeft)
            GameAction.OpenPro -> action.openPro()
        }
    }

    /**
     * Launch, and the fork between resuming and starting.
     *
     * A saved run whose engine already reports `isOver` is dropped rather than
     * restored: the run that wrote it was killed after its last resolution and
     * before the stacked-out sheet, and reopening the app onto a dead board with
     * no record of the run behind it is worse than starting a new one.
     */
    private suspend fun GameAction.enter() {
        val cached = Catching { appCache.get() }.logOnFailure { "Could not read app data" }.getOrNull()
        val best = Catching { progress.bestScore() }.logOnFailure { "Could not read best score" }.getOrNull()
        val teach = cached?.hasUserOnboarded != true
        val saved = if (teach) null else loadSaved(GameMode.ENDLESS)
        bestBeforeRun = best ?: 0

        if (teach) {
            beginTutorial()
        } else if (saved != null) {
            adoptSaved(saved)
        } else {
            beginRun()
        }

        updateState {
            it.published(
                best = best ?: 0,
                leftHanded = cached?.leftHandedControls ?: false,
                ghostEnabled = cached?.ghostEnabled ?: true,
                controlScheme = cached?.controlScheme.asEnumOr(ControlScheme.Both),
            )
        }

        when {
            teach -> updateState { it.copy(phase = GamePhase.Playing, tutorial = tutorial.frame) }

            saved != null -> resumeInto(saved)

            else -> {
                updateState { it.copy(phase = GamePhase.Ready) }
                saveRun()
            }
        }
    }

    /**
     * The saved run in [mode]'s slot, unless the engine that wrote it already
     * reports `isOver`.
     *
     * A dead board is dropped rather than restored: the run that wrote it was
     * killed after its last resolution and before the stacked-out sheet, and
     * reopening onto it with no record of the run behind it is worse than
     * starting a new one.
     */
    private suspend fun loadSaved(mode: GameMode): SavedRun? =
        savedRunStore.load(mode)?.takeUnless { it.state.isOver }

    /** A [SavedRun] becoming the run this ViewModel is playing. */
    private fun adoptSaved(saved: SavedRun) {
        started = StartedRun(state = saved.state, seed = saved.seed, mode = saved.mode)
        engine = saved.state
        tally = saved.tally
        dailyDate = saved.dailyDate
        logger.logEvent(
            "run.resume",
            "mode" to saved.mode.name,
            "level" to engine.level,
            "score" to engine.score,
        )
    }

    /**
     * Putting an adopted run back on screen: a cascade caught mid-playback
     * finishes before the ticker starts, otherwise play resumes straight away.
     */
    private suspend fun GameAction.resumeInto(saved: SavedRun) {
        val resolution = saved.resolution
        if (resolution != null) {
            restoreResolution(resolution)
        } else {
            updateState { it.copy(phase = GamePhase.Playing) }
            beginPlaying()
        }
    }

    /**
     * SPEC 13: first launch drops straight into a scripted run. No menus, no
     * video, no wall of text, and **no start overlay** — the phase goes to
     * `Playing` directly, because a Play button in front of a tutorial is the
     * menu SPEC 13 says the app does not have.
     *
     * Any saved run is cleared rather than resumed. A player who has not been
     * taught the game cannot have a run worth keeping, and a half-finished
     * tutorial is not something to restore into.
     */
    private suspend fun beginTutorial() {
        tickerJob?.cancel()
        lockJob?.cancel()
        playbackJob?.cancel()
        savedRunStore.clearAll()
        engine = tutorial.begin()
        started = StartedRun(state = engine, seed = started.seed, mode = started.mode)
        tally = RunTally()
        frames = emptyList()
        frameIndex = 0
        resolutionSnapshot = null
        lockPending = false
        lockResetUsed = false
        bufferedMove = null
        playingSince = null
        decisions.suspend()
        logger.logEvent("tutorial.started")
        // The first beat is reached by arriving, not by finishing a drop, so
        // it fires here. Without it the abandonment query cannot tell a player
        // who quit on lesson one from one who never saw the tutorial at all —
        // and lesson one is where a tutorial is abandoned.
        logger.logEvent("tutorial.step_reached", "drop" to tutorial.currentDrop)
    }

    /** The card's button: "Got it", "OK", "Play". */
    private suspend fun GameAction.tutorialAdvance() {
        if (!tutorial.isRunning) return
        sendEvent(GameEffect.Play(Cue.UiTap))
        tutorial.observe(TutorialAwait.Tapped)
        if (!tutorial.isRunning) {
            finishTutorial()
            return
        }
        tutorial.stateForCurrentLesson(engine.score)?.let { engine = it }
        updateState { it.copy(tutorial = tutorial.frame).published() }
    }

    /** SPEC 13's escape hatch, offered from drop 3 onward. */
    private suspend fun GameAction.tutorialSkip() {
        if (!tutorial.isRunning) return
        sendEvent(GameEffect.Play(Cue.UiTap))
        logger.logEvent("tutorial.skipped", "drop" to tutorial.currentDrop)
        finishTutorial()
    }

    /**
     * Replays the tutorial, which is the entry point SPEC 13 says lives in
     * Settings. There is no Settings screen yet (C11), so the route argument
     * `GameRoute(replayTutorial = true)` is the whole of it for now.
     */
    private suspend fun GameAction.replayTutorial() {
        beginTutorial()
        updateState {
            it.copy(
                phase = GamePhase.Playing,
                tutorial = tutorial.frame,
                callout = null,
                chainStep = 0,
                chainCell = null,
                newBest = false,
            ).published()
        }
    }

    /**
     * SPEC 13 step 5: "Now for real." The scripted run is thrown away and a real
     * endless run starts at level 1, on the clock.
     *
     * The persisted flag is written here and only here, so a player who quits
     * halfway through gets the tutorial again rather than a game nobody
     * explained.
     *
     * The best score is re-read from disk on the way out rather than carried
     * over. The scripted run banks about 11,600 points and is deliberately never
     * recorded, so the only number that can be right here is the one `run_record`
     * holds — which for a brand-new player is zero. C3c needed this because
     * `published()` folded the live score into `best` and the script's total had
     * therefore become the player's record; that fold is gone, and this stays
     * because the tutorial is also the one path where the run behind the state is
     * replaced wholesale.
     */
    private suspend fun GameAction.finishTutorial() {
        logger.logEvent("tutorial.completed", "drop" to tutorial.currentDrop)
        tutorial.stop()
        Catching { appCache.update { data -> data.copy(hasUserOnboarded = true) } }
            .logOnFailure { "Could not persist tutorial completion" }
        resetToNewRun()
        val best = Catching { progress.bestScore() }
            .logOnFailure { "Could not read best score" }
            .getOrNull() ?: 0
        bestBeforeRun = best
        updateState { it.copy(best = best) }
    }

    /**
     * The next scripted drop, installed the moment the last one finished playing.
     *
     * The engine has already spawned a block of its own by this point — a lock
     * always spawns — and it is replaced rather than suppressed, because
     * suppressing it would mean a second spawn path in the engine for the benefit
     * of six drops. The player never sees it: the phase is `Resolving` for the
     * whole of playback and `GameUiState.falling` is null throughout.
     */
    private suspend fun GameAction.advanceTutorialDrop() {
        tutorial.observe(TutorialAwait.Dropped)
        if (!tutorial.isRunning) {
            finishTutorial()
            return
        }
        engine = tutorial.stateForCurrentLesson(engine.score) ?: engine.copy(falling = null)
        logger.logEvent("tutorial.step_reached", "drop" to tutorial.currentDrop)
        updateState { it.copy(phase = GamePhase.Playing, tutorial = tutorial.frame).published() }
    }

    /** Tells the current beat what the player just did, and republishes if it moved. */
    private suspend fun GameAction.noteTutorial(signal: TutorialAwait) {
        if (!tutorial.isRunning) return
        if (!tutorial.observe(signal)) return
        tutorial.stateForCurrentLesson(engine.score)?.let { engine = it }
        updateState { it.copy(tutorial = tutorial.frame).published() }
    }

    /**
     * The Play button on the start overlay.
     *
     * A brand-new run waits here rather than starting under the overlay, and that
     * is the shape L32 cost this project once already: a phase-less state copy
     * left a real run playing, invisible, behind a scrim that is a genuine input
     * barrier. A run that has not started has no ticker, so there is nothing to
     * play underneath.
     *
     * A **resumed** run never sees this. The player already pressed Play; asking
     * them to press it again to get back to the board they were killed out of is
     * the app admitting it lost their place.
     */
    private suspend fun GameAction.start() {
        if (state.phase != GamePhase.Ready) return
        sendEvent(GameEffect.Play(Cue.UiTap))
        resumePlay()
    }

    /**
     * SPEC 18.9, on the launch path: a cascade the app died in the middle of
     * finishes before the drop timer starts and before any input is accepted.
     *
     * The frames are re-derived rather than restored, because they are a pure
     * function of the transcript and the board it started from — storing them
     * would be storing a second copy of the same truth, and the copy is the one
     * that would go stale.
     */
    private suspend fun GameAction.restoreResolution(resolution: SavedResolution) {
        frames = framesFor(
            before = resolution.beforeBoard,
            scoreBefore = resolution.scoreBefore,
            transcript = resolution.transcript,
        )
        frameIndex = resolution.frameIndex.coerceIn(0, frames.size)
        playingSince = clock.now().toEpochMilliseconds()
        updateState {
            it.copy(
                phase = GamePhase.Resolving,
                board = frames.getOrNull(frameIndex - 1)?.board ?: resolution.beforeBoard,
                score = frames.getOrNull(frameIndex - 1)?.score ?: resolution.scoreBefore,
                falling = null,
                ghost = null,
                chainStep = 0,
                chainCell = null,
            )
        }
        drivePlayback()
    }

    private fun beginRun() = adopt(runFactory.newRun(), day = null)

    /**
     * Every timer cancelled and every per-drop scrap of playback state dropped.
     *
     * Shared by "throw this run away and start another" and by "the run on screen
     * is being replaced by one off disk", because those differ only in what
     * arrives next.
     */
    private fun stopEverything() {
        tickerJob?.cancel()
        lockJob?.cancel()
        playbackJob?.cancel()
        frames = emptyList()
        frameIndex = 0
        resolutionSnapshot = null
        lockPending = false
        lockResetUsed = false
        bufferedMove = null
        playingSince = null
        decisions.suspend()
    }

    private fun adopt(run: StartedRun, day: String?) {
        started = run
        dailyDate = day
        engine = run.state
        tally = RunTally(highestTier = engine.board.highestValue()?.points ?: 0)
        decisions = DecisionTimer()
        continuesUsed = 0
        // The ad layer asks this rather than being told by an argument, so that
        // SPEC 12's governing principle is the gate's rule rather than a
        // convention every call site has to keep. See `RunActivity`.
        runActivity.runStarted()
        interstitials.preload()
        adGate.preload(AdPlacement.ContinueRun)
        logger.logEvent("run.start", "level" to engine.level, "mode" to run.mode.name)
    }

    /** Starts the drop timer and the stretch of play the duration is made of. */
    private fun beginPlaying() {
        val now = clock.now().toEpochMilliseconds()
        playingSince = now
        beginMeasuringDrop(now)
        restartTicker()
    }

    /**
     * Starts the decision clock on the block currently in flight.
     *
     * Every path that hands the board back to the player goes through here or
     * through [beginPlaying] — a fresh run, a resume, a continue, the frame
     * after a cascade. A path that forgot to would not break anything visible;
     * it would quietly stop measuring, which is exactly the failure mode SPEC
     * 17 is supposed to be immune to, so `DecisionTimerCallSiteTest` asserts
     * the measurement rather than the call.
     *
     * The tutorial is excluded outright. Its clock is frozen (SPEC 13), the
     * board is scripted and a coach mark is asking the player to read — none
     * of which is the number `decisionMillis` models.
     */
    private fun beginMeasuringDrop(nowMs: Long = clock.now().toEpochMilliseconds()) {
        if (tutorial.isRunning) return
        if (engine.falling == null) return
        decisions.dropBegan(nowMs)
    }

    /**
     * [tally] with the stretch of play currently underway folded in.
     *
     * Only what goes to disk, never [tally] itself, so [stopPlaying] can still
     * bank the whole stretch without double counting. Without this a force-quit
     * loses the playtime since the last pause, and SPEC 15's "total playtime"
     * reads low for every player who never pauses — which is most of them,
     * because backgrounding is the pause they actually use.
     */
    private fun tallyIncludingTimeSoFar(): RunTally {
        val since = playingSince ?: return tally
        return tally.copy(playedMs = tally.playedMs + (clock.now().toEpochMilliseconds() - since))
    }

    /**
     * Closes the current stretch of play into [RunTally.playedMs].
     *
     * Idempotent, because the pause path and the end-of-run path both call it and
     * a stacked-out run that also gets backgrounded must not bank its last
     * seconds twice.
     */
    private fun stopPlaying() {
        decisions.suspend()
        val since = playingSince ?: return
        playingSince = null
        tally = tally.copy(playedMs = tally.playedMs + (clock.now().toEpochMilliseconds() - since))
    }

    private suspend fun GameAction.tick() {
        if (state.phase != GamePhase.Playing) return
        val falling = engine.falling ?: return
        if (engine.board.isEmpty(falling.cell + Direction.DOWN)) {
            lockPending = false
            engine = Cascade.apply(engine, Input.Tick).state
            updateState { it.published() }
            return
        }
        if (!lockPending) {
            lockPending = true
            scheduleLock()
        }
    }

    /**
     * SPEC 6's lock delay. The block has been resting for its 150ms; unless a
     * sideways move opened a hole underneath it in the meantime, it locks.
     */
    private suspend fun GameAction.lockNow() {
        if (state.phase != GamePhase.Playing) return
        if (tutorialIsAsking) return
        val falling = engine.falling ?: return
        if (engine.board.isEmpty(falling.cell + Direction.DOWN)) {
            lockPending = false
            return
        }
        lock()
    }

    private suspend fun GameAction.move(input: Input) {
        if (state.phase == GamePhase.Resolving) {
            bufferedMove = input
            return
        }
        if (state.phase != GamePhase.Playing) return
        if (tutorialIsAsking) return
        val step = if (input == Input.MoveLeft) -1 else 1
        val target = (engine.falling?.cell?.col ?: return) + step
        if (!tutorialAllows(target)) return
        val transition = Cascade.apply(engine, input)
        if (transition.isRejected) return
        engine = transition.state
        decisions.columnStep(clock.now().toEpochMilliseconds())
        sendEvent(GameEffect.Play(Cue.Move))
        updateState { it.published() }
        noteTutorial(TutorialAwait.Steered)
        if (lockPending && !lockResetUsed) {
            lockResetUsed = true
            scheduleLock()
        }
    }

    /**
     * Whether a guided beat is waiting on its own button, in which case the board
     * takes no input at all.
     *
     * The scrim over a coach mark passes touches through on purpose — the first
     * lesson lights the board and asks the player to drag it — so nothing in the
     * screen stops ▼ while a card is up. That cost the tutorial a **permanent
     * deadlock**: a player who kept nudging landed the drop the card was about to
     * introduce, and the beat that follows it then waited forever for a landing
     * that had already happened, on a clock that is frozen and with no coach mark
     * left to offer the skip. See [TutorialRunner.awaitsTap].
     *
     * Four call sites and not one, because the deadlock does not care which input
     * got there first.
     */
    private val tutorialIsAsking: Boolean get() = tutorial.isRunning && tutorial.awaitsTap

    /**
     * Whether the guided run will let the block into [col].
     *
     * True whenever no script is running, so this is a no-op for every real run.
     * See [TutorialDrop.allowedColumns] for why a scripted drop clamps at all.
     */
    private fun tutorialAllows(col: Int): Boolean = tutorial.allowedColumns?.contains(col) ?: true

    /**
     * Drag steering (decision D11): put the block in column [col] if it can get
     * there.
     *
     * **Absolute, not incremental, and the difference is the whole feel.** The
     * screen records the pointer's x and the block's column on the way down and
     * asks for `startCol + round(dx / cellWidth)` on every move, so the block
     * tracks the finger's *position* rather than accumulating its deltas. An
     * incremental scheme drifts: a drag out and back leaves the block somewhere
     * other than where it started, and the player learns not to trust it.
     *
     * The travel is one engine step at a time and stops at the first refusal,
     * which is how "blocked by a placed tile at the block's current row" arrives
     * without this class knowing what a board is. `Input.MoveLeft` already
     * rejects a move into an occupied cell, so a drag across a column with
     * something in it parks against it rather than teleporting past — the same
     * rule the arrow buttons obey, because it is literally the same call.
     *
     * Loop-bounded by the column count rather than by `while (col != target)`: a
     * rejection already breaks, but a bound means a future engine that rejects
     * *and* moves cannot hang the action channel.
     */
    private suspend fun GameAction.steerTo(col: Int) {
        if (state.phase != GamePhase.Playing) return
        if (tutorialIsAsking) return
        val limits = tutorial.allowedColumns
        val target = if (limits != null) col.coerceIn(limits) else col.coerceIn(0, engine.board.cols - 1)
        var moved = false
        var steps = 0
        while (steps++ < engine.board.cols) {
            val current = engine.falling?.cell?.col ?: break
            if (current == target) break
            val input = if (target > current) Input.MoveRight else Input.MoveLeft
            val transition = Cascade.apply(engine, input)
            if (transition.isRejected) break
            engine = transition.state
            // Per engine step, not per gesture. A drag across three columns
            // costs the modelled player three taps at `tapMillis`, so it has
            // to cost three here or the live histogram is measuring a
            // different quantity from the one it is compared against.
            decisions.columnStep(clock.now().toEpochMilliseconds())
            moved = true
        }
        if (!moved) return
        sendEvent(GameEffect.Play(Cue.Move))
        updateState { it.published() }
        noteTutorial(TutorialAwait.Steered)
        if (lockPending && !lockResetUsed) {
            lockResetUsed = true
            scheduleLock()
        }
    }

    /**
     * The ▼ control and the downward flick (decision D21): send the block to the
     * floor of its column and lock it there.
     *
     * There is no lock delay to arm and no interval to shorten. [lock] cancels
     * the ticker and the pending lock job itself, so a hard drop leaves **no
     * per-drop state behind at all** — which is the whole reason D21 preferred it
     * to fixing the hold gesture. The bug it replaces was a held "on" flag with
     * one path out of it.
     *
     * Deliberately not buffered during a resolution, for the reason the ▼ nudge
     * was not: a sideways step arriving mid-cascade is cheap and recoverable, and
     * committing a block to a board the player has not looked at yet is not.
     */
    private suspend fun GameAction.hardDrop() {
        if (state.phase != GamePhase.Playing) return
        if (tutorialIsAsking) return
        val falling = engine.falling ?: return
        val landing = engine.landingCell ?: return
        sendEvent(GameEffect.Play(Cue.HardDrop))

        // The travel, and it has to be on screen before the lock or the tile
        // teleports seven rows. It is published **without touching [engine]**,
        // which is not a detail: `Input.Lock` pays SPEC 7's bonus for the rows the
        // block skipped, and moving the engine's block to the landing cell first
        // would make that zero on every drop. The engine stays where the player
        // pressed; only the picture falls.
        if (landing != falling.cell) {
            updateState { it.published().copy(falling = falling.copy(cell = landing)) }
            delay(reduced(Motion.HardDropMillis, reduceMotion).toLong())
        }
        lock()
    }

    private suspend fun GameAction.pause() {
        if (state.phase != GamePhase.Playing && state.phase != GamePhase.Resolving) return
        tickerJob?.cancel()
        lockJob?.cancel()
        playbackJob?.cancel()
        stopPlaying()
        saveRun()
        sendEvent(GameEffect.Play(Cue.UiTap))
        updateState { it.copy(phase = GamePhase.Paused) }
    }

    /**
     * SPEC 18.9: a cascade interrupted by a pause finishes before any input is
     * accepted, so a resume that lands mid-transcript restarts the driver rather
     * than the drop timer.
     */
    private suspend fun GameAction.resume() {
        if (state.phase != GamePhase.Paused) return
        sendEvent(GameEffect.Play(Cue.UiTap))
        resumePlay()
    }

    /**
     * Play, from whichever overlay was covering the board.
     *
     * Shared by Resume and by the start overlay's Play, because since Quit stops
     * leaving the app the start overlay is somewhere a *live* run can be sitting
     * behind — and a run quit mid-cascade has to finish that cascade before it
     * takes an input either way (SPEC 18.9).
     */
    private suspend fun GameAction.resumePlay() {
        playingSince = clock.now().toEpochMilliseconds()
        if (frameIndex < frames.size) {
            updateState { it.copy(phase = GamePhase.Resolving) }
            drivePlayback()
        } else {
            updateState { it.copy(phase = GamePhase.Playing) }
            beginMeasuringDrop()
            restartTicker()
        }
    }

    /**
     * A new run, from the pause menu or from "Drop again".
     *
     * The phase is set explicitly. `published()` copies the engine into the state
     * and deliberately says nothing about the phase, so a restart that relied on
     * it left the run playing underneath the sheet or the pause menu that
     * launched it — on device, "Drop again" started a run the player could not
     * see and could not touch, because the scrim is a real input barrier.
     */
    private suspend fun GameAction.restart() {
        // "Drop again" *is* the dismissal (SPEC 12), so this is one of the two
        // call sites an interstitial can ever come from. It suspends, and the
        // reset below happens after the ad closes — a run started behind a
        // full-screen ad is a run the player is losing while they cannot see it.
        dismissResults()
        if (started.mode == GameMode.DAILY) {
            sendEvent(GameEffect.Leave)
            return
        }
        resetToNewRun()
    }

    /**
     * SPEC 14, entered from `GameRoute(mode = DAILY)`.
     *
     * Two paths, and the fork is the one-attempt rule.
     *
     * A Daily run already in flight — resumed a moment ago by [enter] from the
     * saved run store — is *continued*, not restarted. The attempt was spent when
     * it began, so charging for it again would make backgrounding the app during
     * the Daily cost the player their day.
     *
     * Otherwise the attempt is requested, and a refusal leaves rather than
     * starting anything. The Daily screen is the gate and will not normally offer
     * a run there are no attempts for, but the gate has to hold here too: this is
     * a route, and a route can be reached from a deep link, a restored back stack
     * or a stale screen.
     */
    private suspend fun GameAction.startDaily() {
        if (started.mode == GameMode.DAILY && !engine.isOver) {
            updateState { it.published() }
            return
        }
        val saved = loadSaved(GameMode.DAILY)
        if (saved != null) {
            stopEverything()
            adoptSaved(saved)
            updateState { it.copy(callout = null, newBest = false, tutorial = null).published() }
            resumeInto(saved)
            return
        }
        val attempt = Catching { daily.startAttempt() }
            .logOnFailure { "Could not start a Daily attempt" }
            .getOrDefault(DailyAttempt.NoAttemptsLeft)
        if (attempt !is DailyAttempt.Granted) {
            logger.logEvent("daily.refused", "reason" to attempt::class.simpleName.orEmpty())
            sendEvent(GameEffect.Leave)
            return
        }
        logger.logEvent("daily.start", "date" to attempt.date.toString(), "attempt" to attempt.attemptNumber)
        resetToRun(runFactory.dailyRun(attempt.seed), day = attempt.date.toString())
    }

    private suspend fun GameAction.resetToNewRun() = resetToRun(runFactory.newRun(), day = null)

    /**
     * Everything a fresh run has to forget, and the one place it is written down.
     *
     * Shared by "Drop again", the pause menu's Restart, the end of the tutorial
     * and the start of a Daily attempt, because all four are the same event:
     * throw away what is on screen and start [run] at level 1.
     */
    private suspend fun GameAction.resetToRun(run: StartedRun, day: String?) {
        stopEverything()
        adopt(run, day)
        updateState {
            it.copy(
                phase = GamePhase.Playing,
                callout = null,
                newBest = false,
                chainStep = 0,
                chainCell = null,
                tutorial = null,
            ).published()
        }
        saveRun()
        beginPlaying()
    }

    /**
     * The settings the board itself has to honour, republished as they change.
     *
     * Only the ones the engine or the layout reads. The palette, reduce motion
     * and the large-numbers scale are not here on purpose: they arrive through
     * `AppThemeProvider` as CompositionLocals (decision D4), and mirroring them
     * into this state would be a second source for one setting.
     */
    private suspend fun GameAction.settingsChanged(data: AppData) {
        confirmBeforeQuit = data.confirmBeforeQuit
        updateState {
            it.copy(
                leftHanded = data.leftHandedControls,
                ghostEnabled = data.ghostEnabled,
                controlScheme = data.controlScheme.asEnumOr(ControlScheme.Both),
                ghost = if (data.ghostEnabled) engine.landingCell else null,
            )
        }
    }

    /**
     * Quit, which puts the board away without ending the run.
     *
     * It sits a thumb-width from Restart on the pause overlay, so it asks first
     * unless the player has turned that off in settings.
     */
    private suspend fun GameAction.quit() {
        if (!confirmBeforeQuit) {
            leaveRun()
            return
        }
        updateState { it.copy(confirmingQuit = true) }
    }

    /**
     * Where Quit goes, and it is not out of the app.
     *
     * A Daily was navigated to, so it pops back to the Daily screen. Endless is
     * the **start destination** (C5 deleted the home screen), so popping it took
     * the player to their launcher — which reads as a crash rather than as a
     * decision, on a button whose dialog they had just confirmed.
     *
     * Endless therefore quits to the start overlay, which since C3c is the app's
     * menu. Decision D12 already says the run survives a Quit rather than dying,
     * so there is a run behind that overlay and Play picks it back up.
     */
    private suspend fun GameAction.leaveRun() {
        // The other dismissal. Quitting from the pause overlay is not one — the
        // run is still alive there, and the gate refuses it anyway.
        dismissResults()
        if (started.mode == GameMode.DAILY) {
            sendEvent(GameEffect.Leave)
            return
        }
        tickerJob?.cancel()
        lockJob?.cancel()
        playbackJob?.cancel()
        stopPlaying()
        saveRun()
        updateState { it.copy(phase = GamePhase.Ready, confirmingQuit = false) }
    }

    private suspend fun GameAction.showFrame(index: Int) {
        val frame = frames.getOrNull(index) ?: return
        frameIndex = index + 1
        frame.cue?.let { sendEvent(GameEffect.Play(it)) }
        updateState {
            it.copy(
                board = frame.board,
                score = frame.score,
                chainStep = if (frame.chained) frame.cascadeStep else it.chainStep,
                chainCell = frame.chainAt ?: it.chainCell,
                chainNonce = if (frame.chained) it.chainNonce + 1 else it.chainNonce,
                callout = frame.callout ?: it.callout,
                calloutNonce = if (frame.callout != null) it.calloutNonce + 1 else it.calloutNonce,
            )
        }
    }

    private suspend fun GameAction.finishResolution() {
        frames = emptyList()
        frameIndex = 0
        resolutionSnapshot = null
        if (engine.isOver) {
            if (mayOfferContinue()) offerContinue() else endRun()
            return
        }
        if (tutorial.isRunning) {
            advanceTutorialDrop()
            return
        }
        updateState { it.copy(phase = GamePhase.Playing).published() }
        saveRun()
        // Before the buffered move is replayed, so a nudge the player made
        // during the cascade is measured against the moment the block became
        // theirs rather than against nothing.
        beginMeasuringDrop()
        bufferedMove?.let { buffered ->
            bufferedMove = null
            val transition = Cascade.apply(engine, buffered)
            if (!transition.isRejected) {
                engine = transition.state
                decisions.columnStep(clock.now().toEpochMilliseconds())
                sendEvent(GameEffect.Play(Cue.Move))
                updateState { it.published() }
            }
        }
        restartTicker()
    }

    /**
     * SPEC 3.1: the run ends on the frame the last cascade finishes on, never
     * during it. The engine has already decided; playback is what makes the
     * player watch the merge that killed them before the sheet arrives.
     *
     * ### A debug run is played and then thrown away
     *
     * [StartedRun.debug] is true for every run started in a session that has
     * opened the debug menu (SPEC 19), and it gates all four writes at once:
     * `run_record`, `daily_result`, `Leaderboards.submit` and the achievement
     * fact log. The gate is here rather than inside each repository because
     * these are the only four things in the app that claim a player did
     * something, and one guard over the four of them is one place to be wrong
     * instead of four.
     *
     * The stacked-out sheet is still drawn, with its score and its share — the
     * run happened, it just does not count. The badge announcement is suppressed
     * with the fact that would have earned it, since a toast for a badge the
     * player does not own is worse than no toast.
     */
    private suspend fun GameAction.endRun() {
        tickerJob?.cancel()
        countdownJob?.cancel()
        stopPlaying()
        // Before anything else, and before the sheet is drawn. Everything the
        // ad layer is allowed to do from here reads this, and a run that is
        // still "alive" while its results are on screen would make SPEC 12's one
        // non-negotiable rule depend on the order of the next twenty lines.
        runActivity.runEnded()
        sendEvent(GameEffect.Play(Cue.StackedOut))
        val score = engine.score
        val record = RunRecord(
            endedAt = clock.now().toEpochMilliseconds(),
            score = score,
            level = engine.level,
            blocksPlaced = engine.blocksDropped,
            durationMs = tally.playedMs,
            highestTier = tally.highestTier,
            cause = engine.deathCause?.name ?: UnknownCause,
            longestCascade = tally.longestCascade,
            bursts = tally.bursts,
            merges = tally.merges,
            mode = started.mode,
            seed = started.seed,
        )
        val recordable = !started.debug
        if (recordable) {
            Catching { progress.record(record) }.logOnFailure { "Could not record the run" }
        }
        val streak = if (recordable) bankDailyResult(score) else 0
        if (recordable) postToLeaderboards(score)
        savedRunStore.clear(started.mode)
        val unlocked = if (recordable) recordAchievements(record, streak) else emptyList()
        val best = Catching { progress.bestScore() }.logOnFailure { "Could not read best score" }
            .getOrNull() ?: maxOf(state.best, score)
        val decided = decisions.summary()
        logger.logEvent(
            "run.end",
            "mode" to started.mode.name,
            "score" to score,
            "level" to engine.level,
            "blocks" to engine.blocksDropped,
            "duration_ms" to tally.playedMs,
            "highest_tier" to tally.highestTier,
            "cause" to record.cause,
            "bursts" to tally.bursts,
            "merges" to tally.merges,
            "longest_cascade" to tally.longestCascade,
            "cascades_1" to tally.cascadesByDepth.getOrElse(1) { 0 },
            "cascades_2" to tally.cascadesByDepth.getOrElse(2) { 0 },
            "cascades_3" to tally.cascadesByDepth.getOrElse(3) { 0 },
            "cascades_4plus" to tally.cascadesByDepth.drop(DeepCascade).sum(),
            // SPEC 17 asks for the seed and SPEC 14 says a Daily seed is the
            // whole world's board for that day. Endless seeds are a private
            // number that makes a reported run replayable; a Daily seed on a
            // record that can ship the moment the attempt starts is a board
            // leaked before its day. So Endless carries it and Daily carries
            // the day instead — which is the only part of a Daily run that is
            // not already public.
            "seed" to started.seed.takeIf { started.mode == GameMode.ENDLESS },
            "daily_date" to dailyDate,
            // Whether the four writes that claim a player did something
            // actually happened (L63). `debug_session` is stamped on every
            // record by `GrafanaLogTree` and answers a wider question — did
            // this process have a debug menu open in it. This answers the
            // narrower one the dashboards need: "no row was written" and "no
            // row reached us" look identical downstream otherwise.
            "recorded" to recordable,
            // L41's two numbers. `steer_ms_*` is `decisionMillis`;
            // `tap_gap_ms_p50` is `tapMillis`. Omitted rather than zeroed when
            // the run produced no steered drop at all.
            "steer_ms_p50" to decided.steerP50,
            "steer_ms_p90" to decided.steerP90,
            "tap_gap_ms_p50" to decided.tapGapP50,
            "drops_steered" to decided.steeredDrops,
            "drops_unsteered" to decided.unsteeredDrops,
            "steps" to decided.steps,
        )
        noteFirstRunCompleted()
        // Counts towards SPEC 12's "not before the 4th run of a session", and
        // shows nothing. A debug run is still a run the player sat through, so
        // it counts here even though it writes nothing anywhere else (L63): the
        // frequency gates are about attention, not about the economy.
        interstitials.noteRunFinished()
        interstitials.preload()

        updateState {
            it.published(best = best).copy(
                phase = GamePhase.StackedOut,
                falling = null,
                ghost = null,
                newBest = started.mode == GameMode.ENDLESS && score > 0 && score > bestBeforeRun,
                unlocked = unlocked,
                continueSecondsLeft = 0,
                continueAvailable = mayOfferSecondContinue(),
                // SPEC 12: one non-modal card, at most once per session. The
                // coordinator owns the cap; claiming it here rather than asking
                // the screen to is what keeps "once per session" from becoming
                // "once per recomposition".
                showUpsell = paywall.claimStackedOutCard(),
            )
        }
        bestBeforeRun = best
    }

    /**
     * Whether the offer may be made *automatically*, which is only ever for the
     * first continue of a run.
     *
     * Two refusals worth naming.
     *
     * **A Daily run is never offered a continue**, and this is the one place
     * SPEC 12 and SPEC 14 have to be reconciled rather than both applied. The
     * Daily's whole claim is that everybody played the same board — decision D18
     * goes as far as pinning `EngineConfig.Default` against remote config to keep
     * two players' runs comparable. A continue clears three rows and drops a
     * level, which is a larger change to the board than any config key could
     * make, and one that only some players would have. Watching an ad is not
     * allowed to buy a better score on a leaderboard everyone shares.
     *
     * **The second continue is not offered here.** SPEC 12 asks for "a 2nd at
     * higher friction", and the friction is that nothing offers it: the sheet
     * carries a quiet option and the player has to reach for it. An automatic
     * second offer with a longer countdown would be more friction to *sit
     * through* and less to *accept*, which is backwards.
     *
     * **A debug run is not refused**, and that was decided the other way first.
     * L63 makes the session the taint and gates the four writes that claim a
     * player did something; a continue is none of them, and `endRun` already
     * refuses every one of those for a debug run whether it was continued or
     * not. Refusing here bought a second guard saying the same thing, and it
     * cost the only way to reach this screen on a build with a seed switch —
     * which is what SPEC 19's menu is *for*. Same reasoning as the Pro grant.
     */
    /**
     * SPEC 17's "upsell tapped", which is the half of the upsell funnel the
     * coordinator cannot see.
     *
     * `iap.paywall_shown` fires inside `RealPaywallCoordinator` when a request
     * is *accepted*, so a tap that the coordinator refuses — the player is
     * already Pro, or `pro.upsell.enabled` is off — produces no event at all
     * and reads downstream as a card nobody touched. The tap is the player's
     * intent and it happened either way, so it is recorded here, at the
     * control, with what the coordinator did about it.
     */
    private fun GameAction.openPro() {
        val offered = paywall.requestOffer(PaywallTrigger.StackedOut)
        logger.logEvent(
            "iap.upsell_tapped",
            "surface" to PaywallTrigger.StackedOut.id,
            "offered" to offered,
        )
    }

    private fun mayOfferContinue(): Boolean =
        started.mode == GameMode.ENDLESS && continuesUsed == 0 && continuesLeft() > 0

    /**
     * SPEC 12's hard cap, read at the point of use so `ads.rewarded.continuesPerRun`
     * can be turned down from the console mid-session.
     *
     * A configured zero disables the placement, which is what an operator setting
     * it to zero means. There is no floor of one here: "continues off" has to be
     * expressible, and the compiled default is 2 so only a deliberate value gets
     * there.
     */
    private fun continuesLeft(): Int = (continuesPerRun() - continuesUsed).coerceAtLeast(0)

    /**
     * Whether the sheet carries the quiet "continue" option, which is SPEC 12's
     * higher-friction second one.
     *
     * It is also what the player sees if they declined the first offer and
     * changed their mind three seconds later. That is deliberate: the eight
     * seconds are there so a board does not sit waiting forever, not to punish
     * someone for not reading fast enough. The cap is the cap either way.
     */
    private fun mayOfferSecondContinue(): Boolean =
        started.mode == GameMode.ENDLESS && continuesLeft() > 0

    /**
     * The offer, over a board that is still on screen and **not blurred**.
     *
     * SPEC 8.4 and 12.2: the player has to see exactly what they are saving. The
     * board is the argument, so the screen draws it plainly behind a light scrim
     * rather than behind the 6dp blur every other overlay uses.
     *
     * The countdown is here rather than in the composable because it is a rule,
     * not an animation: at zero the offer declines itself and the run ends, and
     * that is a decision worth being able to test without a frame clock. The
     * screen renders the integer this publishes.
     */
    private suspend fun GameAction.offerContinue() {
        tickerJob?.cancel()
        lockJob?.cancel()
        stopPlaying()
        sendEvent(GameEffect.Play(Cue.StackedOut))
        logger.logEvent("ads.continue_offered", "continues_used" to continuesUsed)
        updateState {
            it.published().copy(
                phase = GamePhase.ContinueOffer,
                falling = null,
                ghost = null,
                continueSecondsLeft = ContinueCountdownSeconds,
            )
        }
        startCountdown()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = ContinueCountdownSeconds
            while (isActive && remaining > 0) {
                delay(CountdownStepMillis)
                remaining--
                takeAction(GameAction.ContinueTick(remaining))
            }
        }
    }

    private suspend fun GameAction.continueTick(secondsLeft: Int) {
        if (state.phase != GamePhase.ContinueOffer) return
        updateState { it.copy(continueSecondsLeft = secondsLeft) }
        if (secondsLeft <= 0) continueDecline()
    }

    /**
     * The player asked for the ad, so from here the reward is theirs unless they
     * close it themselves.
     *
     * Every outcome except [RewardOutcome.Dismissed] continues the run. No fill,
     * no network, an SDK that threw — none of those is the player saying no, and
     * a board ten minutes in the building is not something an ad network's bad
     * afternoon gets to take. `AdGate` already draws that line; this is the call
     * site that spends it.
     */
    private suspend fun GameAction.continueAccept() {
        if (state.continueBusy) return
        countdownJob?.cancel()
        updateState { it.copy(continueBusy = true) }

        val outcome = Catching { adGate.showRewarded(AdPlacement.ContinueRun) }
            .logOnFailure { "The continue ad threw; continuing anyway" }
            .getOrDefault(RewardOutcome.Failed("threw"))

        // Both directions of SPEC 12's 45-second rule start here, and it is
        // recorded for a dismissal too: the player still sat in front of an ad.
        interstitials.noteRewardedShown()

        updateState { it.copy(continueBusy = false) }
        logger.logEvent(
            "ads.continue_result",
            "outcome" to outcome::class.simpleName.orEmpty(),
            "continues_used" to continuesUsed,
        )

        if (outcome == RewardOutcome.Dismissed) {
            endRun()
            return
        }
        grantContinue()
    }

    /**
     * SPEC 12's continue, applied.
     *
     * The board half is [Cascade.continueRun] — three rows cleared, a level back,
     * the score kept — and it has been in the engine with its own tests since C1.
     * The clock half is the line below it: [restartTicker] begins a fresh
     * interval, so the player gets a whole drop's worth of time rather than
     * whatever was left of the one that killed them. SPEC 12 asks for exactly
     * that and it is the half that could only ever be done here, because the
     * engine has no clock.
     */
    private suspend fun GameAction.grantContinue() {
        continuesUsed++
        val transition = Cascade.continueRun(engine)
        engine = transition.state
        transition.events.forEach { event ->
            when (event) {
                GameEvent.DangerEntered -> sendEvent(GameEffect.Play(Cue.DangerEnter))
                GameEvent.DangerCleared -> sendEvent(GameEffect.Play(Cue.DangerExit))
                else -> Unit
            }
        }
        updateState {
            it.published().copy(
                phase = GamePhase.Playing,
                continueSecondsLeft = 0,
                continueAvailable = false,
                callout = null,
            )
        }
        saveRun()
        beginPlaying()
    }

    private suspend fun GameAction.continueDecline() {
        countdownJob?.cancel()
        if (state.phase != GamePhase.ContinueOffer) return
        logger.logEvent("ads.continue_declined", "continues_used" to continuesUsed)
        endRun()
    }

    /**
     * The second continue, reached only by a player who went looking for it on
     * the stacked-out sheet.
     *
     * No countdown, because there is nothing to time out of: the sheet is not an
     * offer that expires, it is a screen the player is sitting on. The refusal
     * below is the cap, and it is checked again here rather than trusted to the
     * screen having hidden the control — this action can also arrive from a
     * stale composition.
     */
    private suspend fun GameAction.continueAgain() {
        if (state.phase != GamePhase.StackedOut) return
        if (continuesLeft() <= 0) return
        if (started.mode != GameMode.ENDLESS) return
        updateState { it.copy(phase = GamePhase.ContinueOffer, continueSecondsLeft = 0) }
        continueAccept()
    }

    /**
     * The player dismissed the results, which is the **only** moment SPEC 12
     * allows an interstitial.
     *
     * It suspends: the ad is full screen, and the next run must not start behind
     * it. Every gate is evaluated inside `InterstitialGate` — including whether a
     * run is alive, which is false by now because [endRun] said so — and a gate
     * that refuses, or a network with nothing preloaded, returns immediately with
     * nothing shown. There is no spinner and no wait, by design (SPEC 12.3).
     */
    private suspend fun dismissResults() {
        if (state.phase != GamePhase.StackedOut) return
        Catching { interstitials.showIfReady() }
            .logOnFailure { "The interstitial path threw; carrying on" }
    }

    /**
     * SPEC 15's share, from the stacked-out sheet.
     *
     * The numbers come off the same tally `run_record` was written from, so a
     * shared run cannot disagree with the sheet it was shared from. The words do
     * not: [ShareResult] carries no strings and no board, which is what keeps a
     * Daily share from handing the reader a seed they have not played.
     */
    private suspend fun GameAction.share() {
        sendEvent(
            GameEffect.Share(
                result = ShareResult(
                    score = engine.score,
                    biggestTier = tally.highestTier,
                    longestCascade = tally.longestCascade,
                    level = engine.level,
                    durationMs = tally.playedMs,
                ),
                day = dailyDate,
            )
        )
    }

    /**
     * SPEC 15's submission, and **this is the call site the whole chunk is
     * about.** Sodogku shipped `Leaderboards.submit` with no production caller
     * and did not notice for a long time; every reference outside the module was
     * a test double, so no score was ever posted and its telemetry event could
     * not fire. `GameViewModelTest.aFinishedRunPostsItsScoreToTheLeaderboard` is
     * the guard.
     *
     * Which board depends on the mode, and it is not a preference (decision D19).
     * A Daily score is set on a seed everybody else also played, so it goes to
     * the Daily board and nowhere near the two Endless ones. Submitting is
     * fire-and-forget by design — nothing here suspends, nothing returns a
     * result, and a player with no Game Center account notices nothing.
     */
    private fun postToLeaderboards(score: Long) {
        when (started.mode) {
            GameMode.ENDLESS -> {
                leaderboards.submit(Leaderboard.AllTimeScore, score)
                leaderboards.submit(Leaderboard.WeeklyScore, score)
            }

            GameMode.DAILY -> leaderboards.submit(Leaderboard.DailyScore, score)
        }
    }

    /**
     * Files the run as an achievement fact and hands back whatever it just
     * unlocked, in catalog order.
     *
     * The fact is `run_record` plus [RunTally.facts] plus the Daily streak the
     * run landed on, so the badges and the stats page are reading the same run
     * rather than two tallies that can drift. A failure here costs the player a
     * badge announcement and nothing else — the fact log is append-only and the
     * fold is re-run from scratch on the next write, so the badge is granted
     * (silently) the next time they finish a run.
     */
    private suspend fun recordAchievements(record: RunRecord, streak: Int): List<AchievementId> =
        Catching { achievements.record(record.outcomeWith(tally.facts, streak)) }
            .logOnFailure { "Could not record achievements for the run" }
            .getOrNull()
            .orEmpty()
            .map { it.id }

    /**
     * Closes today's Daily row (SPEC 11), if this was a Daily run.
     *
     * The day comes from [dailyDate] rather than from the clock, so an attempt
     * that crossed 00:00 UTC scores against the board it was played on. The write
     * only ever raises `completed` and takes the better score, so reporting the
     * same finish twice — which a force-quit on the stacked-out sheet can cause —
     * costs nothing.
     *
     * `run_record` is written either way and carries `mode = DAILY`, which is what
     * keeps SPEC 15's lifetime numbers whole: a Daily run is a run.
     *
     * Returns the streak the day now stands at, which is what SPEC 15's 7- and
     * 30-day badges are earned against. It is read back from `daily_result`
     * *after* the write rather than derived here, because the streak has exactly
     * one owner and a second fold would eventually disagree with the number on
     * the Daily card. Zero for an Endless run.
     */
    private suspend fun bankDailyResult(score: Long): Int {
        if (started.mode != GameMode.DAILY) return 0
        val day = dailyDate ?: return 0
        Catching { daily.recordAttempt(LocalDate.parse(day), score) }
            .logOnFailure { "Could not record the Daily result" }
        logger.logEvent("daily.end", "date" to day, "score" to score)
        return Catching { daily.status().streak.current }
            .logOnFailure { "Could not read the Daily streak" }
            .getOrNull()
            ?: 0
    }

    private suspend fun GameAction.lock() {
        lockJob?.cancel()
        lockPending = false
        lockResetUsed = false
        tickerJob?.cancel()

        bufferedMove = null
        val landing = engine.landingCell
        val block = engine.falling?.block
        val before = if (landing != null && block != null) engine.board.with(landing, block) else engine.board
        val scoreBefore = engine.score

        decisions.dropEnded()
        val transition = Cascade.apply(engine, Input.Lock)
        engine = applyDebugToLanding(transition.state)
        tallyUp(transition)
        sampleDrop()
        reportFaults(transition)
        // Recorded whether or not the overlay is switched on. The merge that
        // looked wrong has already happened by the time anybody reaches for the
        // switch, so a transcript kept only while someone is watching is useless
        // exactly when it is wanted (SPEC 19).
        diagnostics.record(transition.transcript)

        frames = framesFor(before = before, scoreBefore = scoreBefore, transcript = transition.transcript)
        frameIndex = 0
        resolutionSnapshot = SavedResolution(
            beforeBoard = before,
            scoreBefore = scoreBefore,
            transcript = transition.transcript,
            frameIndex = 0,
        )
        saveRun()
        updateState {
            it.copy(
                phase = GamePhase.Resolving,
                board = before,
                falling = null,
                ghost = null,
                chainStep = 0,
                chainCell = null,
            )
        }
        sendEvent(GameEffect.Play(Cue.Lock))
        transition.events.forEach { event ->
            when (event) {
                GameEvent.DangerEntered -> sendEvent(GameEffect.Play(Cue.DangerEnter))
                GameEvent.DangerCleared -> sendEvent(GameEffect.Play(Cue.DangerExit))
                else -> Unit
            }
        }
        drivePlayback()
    }

    private fun drivePlayback() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var index = frameIndex
            while (isActive && index < frames.size) {
                val hold = reduced(frames[index].holdMillis, reduceMotion)
                takeAction(GameAction.ShowFrame(index))
                delay(hold.toLong())
                index++
            }
            if (isActive) takeAction(GameAction.FinishResolution)
        }
    }

    /**
     * The drop timer, and the one line that implements SPEC 13's frozen clock.
     *
     * **A tutorial run has no ticker at all**, which means gravity never moves a
     * block and ▼ is the only way to make one land. That is not a side effect of
     * freezing the timer, it is the reason to freeze it: the player finishes six
     * drops having pressed ▼ on every one of them and having never watched a
     * block come down on its own. L29 measured that habit at 223 seconds against
     * 56 to reach level 4.
     */
    private fun restartTicker() {
        tickerJob?.cancel()
        if (tutorial.isRunning) return
        // SPEC 19's freeze. The falling block stops falling on its own and waits
        // for a move, a nudge or a lock — which is the same shape as the tutorial
        // above it, and for the same reason: a frozen clock is how you look at a
        // board rather than play it.
        if (debug.overrides.value.freezeTimer) return
        tickerJob = viewModelScope.launch {
            var previous = clock.now().toEpochMilliseconds()
            while (isActive) {
                val intended = intervalMillis()
                delay(intended)
                val now = clock.now().toEpochMilliseconds()
                // SPEC 19's actual-versus-intended tick. Measured here because
                // this is the only place that knows both halves: what `delay` was
                // asked for, and what the wall clock did about it.
                diagnostics.recordTick(intendedMs = intended, actualMs = now - previous)
                previous = now
                takeAction(GameAction.Tick)
            }
        }
    }

    /**
     * SPEC 19's forced block queue and invincibility, applied to the state the
     * engine just produced.
     *
     * After the engine, not inside it. Both are lies about the run — a block the
     * RNG did not draw, and a board that should have ended it — and the engine's
     * whole value is that it is a pure function of state and a seed (SPEC 4.1).
     * Teaching it about a debug queue would mean a `GameState` no longer
     * determines the next one, which is the property Daily Challenge, undo,
     * resume, the balance harness and replay are all built on.
     *
     * So the engine plays honestly and this rewrites the answer afterwards, which
     * is also why a run that has been through here writes no `run_record`.
     */
    private fun applyDebugToLanding(state: GameState): GameState {
        val overrides = debug.overrides.value
        var result = state
        if (overrides.invincible && result.isOver) {
            result = result.copy(status = RunStatus.PLAYING, deathCause = null)
        }
        val forced = debug.takeForcedBlock()
        val falling = result.falling
        if (forced != null && falling != null) {
            result = result.copy(falling = falling.copy(block = forced))
        }
        return result
    }

    private fun scheduleLock() {
        lockJob?.cancel()
        lockJob = viewModelScope.launch {
            delay(LockDelayMillis)
            takeAction(GameAction.LockNow)
        }
    }

    /**
     * SPEC 5.5's drop clock, and since decision D21 it has exactly one value at a
     * given level.
     *
     * There is no held mode to branch on any more. Soft drop was the only thing
     * that made this function's answer depend on a flag, and the flag was the bug
     * (D21): a `pointerInput` keyed on `enabled = live` tore the gesture down when
     * the block landed, so the release callback never ran and the flag stayed set
     * for the rest of the run. Deleting the mode deletes the class of failure.
     */
    private fun intervalMillis(): Long {
        val curve = engine.config.speed
        // SPEC 19's tick override, and note where it is applied: here, on the
        // clock, and not on `engine.config`. The speed curve is a remote key that
        // travels inside `GameState` (D5), so an override that went in there
        // would end up in a saved run and in a replayed seed. Vertical position
        // is not an input to the engine (L40), so overriding the interval cannot
        // change where a block lands — only how long the tester waits for it.
        debug.overrides.value.tickIntervalMs?.let { return it.toLong() }
        return curve.msPerRow(engine.level).toLong()
    }

    /**
     * Everything `run_record` wants that the engine does not count, folded in one
     * transition at a time.
     *
     * The highest tier is decision D7: the tier *reached*, not the highest at
     * rest. A 2048 bursts its own row, so the literal reading reports zero of the
     * game's defining moment — which is why the merge results are read here and
     * not the final board alone.
     *
     * The cascade depth is per transition rather than per run because SPEC 15's
     * "longest cascade" is a single chain, and summing them would make one long
     * run beat one long chain.
     */
    private fun tallyUp(transition: Transition) {
        val transcript = transition.transcript
        val reached = listOfNotNull(
            transcript.merges.maxByOrNull { it.result.points }?.result?.points,
            transition.state.board.highestValue()?.points,
            tally.highestTier,
        ).max()
        tally = tally.copy(
            merges = tally.merges + transcript.merges.size,
            bursts = tally.bursts + transcript.bursts.size,
            longestCascade = maxOf(tally.longestCascade, transcript.depth),
            cascadesByDepth = tally.cascadesByDepth.incrementing(transcript.depth),
            highestTier = reached,
            facts = tally.facts.fold(transition),
        )
    }

    /**
     * SPEC 17's funnel: the first run this install ever played to the end.
     *
     * Once ever, keyed on a persisted flag rather than on a count of
     * `run_record` rows, because a player who resets their progress in
     * Settings (C11) has not become a new player and a funnel that said so
     * would put a second first-run on the same install id.
     *
     * A debug run is deliberately allowed to claim it. The flag is stamped on
     * the event by `GrafanaLogTree` and every funnel query filters on it, so
     * the alternative — refusing to write the timestamp — would leave a tester
     * permanently able to fire a fresh "first run" on every launch, which is
     * the noisier failure.
     */
    private suspend fun noteFirstRunCompleted() {
        val already = Catching { appCache.get().hasCompletedARun }
            .logOnFailure { "Could not read the first-run marker" }
            .getOrNull() ?: return
        if (already) return
        Catching { appCache.update { it.copy(hasCompletedARun = true) } }
            .logOnFailure { "Could not persist the first-run marker" }
        logger.logEvent(
            "funnel.first_run_completed",
            "score" to engine.score,
            "level" to engine.level,
            "mode" to started.mode.name,
        )
    }

    /**
     * SPEC 17's per-drop sample, every tenth drop.
     *
     * ### The clutter number is the engine's, not a copy of it
     *
     * `engine.clutter` is the same `GameState.clutter` `tools/balance` reads,
     * on the same `isSampleDrop` cadence, and that is the entire point of the
     * metric: SPEC 4.4 wants the offline and live figures to be directly
     * comparable, and two implementations of "blocks with no partner" would be
     * comparable right up until one of them was edited. `ClutterParityTest`
     * plays the same seed through the harness and through this ViewModel and
     * asserts the two sequences are equal.
     *
     * ### It carries the decision times
     *
     * Folding them in here rather than emitting a third event is what keeps
     * the instrument off the firehose list: the sample already fires at the
     * right rate, and arriving on the same record as `level` is what lets the
     * dashboard ask whether players get slower as the board speeds up — which
     * is the question L41's sweep could not answer from the outside.
     *
     * ### A tutorial drop is not a sample
     *
     * Its clock is frozen, its board is scripted and its blocks are chosen. It
     * would be six records of a game nobody is playing.
     */
    private fun sampleDrop() {
        if (tutorial.isRunning) return
        if (!engine.isSampleDrop) return
        val timing = decisions.lastDrop
        logger.logEvent(
            "run.sample",
            "mode" to started.mode.name,
            "drop" to engine.blocksDropped,
            "level" to engine.level,
            "tick_ms" to intervalMillis(),
            "fill_pct" to engine.board.fillPercent,
            "highest_tier" to tally.highestTier,
            "clutter" to engine.clutter,
            "steer_ms" to timing?.firstStepMillis,
            "tap_gap_ms" to timing?.firstGapMillis,
            "steps" to timing?.steps,
        )
    }

    /**
     * Writes the run to disk (SPEC 11).
     *
     * Called at every lock and at every pause, not on every tick: the value it
     * writes only changes meaningfully per drop, and a resume that costs the
     * player the sideways nudges of the drop they were in the middle of is a
     * fair trade for not writing to disk twice a second.
     *
     * The cascade in flight, if there is one, comes along with it at the frame
     * playback has reached (SPEC 18.9).
     */
    private suspend fun saveRun() {
        if (engine.isOver) return
        if (tutorial.isRunning) return
        savedRunStore.save(
            SavedRun(
                version = SAVE_FORMAT_VERSION,
                state = engine,
                tally = tallyIncludingTimeSoFar(),
                seed = started.seed,
                mode = started.mode,
                resolution = resolutionSnapshot?.takeIf { frameIndex < frames.size }
                    ?.copy(frameIndex = frameIndex),
                dailyDate = dailyDate,
            )
        )
    }

    private fun reportFaults(transition: Transition) {
        transition.faults.forEach { fault ->
            logger.logEvent("engine.fault", "fault" to fault.name)
        }
    }

    /**
     * The engine copied onto the state, and the one number that is deliberately
     * *not* recomputed here.
     *
     * [best] used to be `maxOf(best, engine.score)`, which made the header read
     * "BEST 736" during a run sitting at 736 — the game congratulating the player
     * on a record they were still in the middle of setting, and then, on the next
     * launch, quietly disagreeing with itself. It also made the number useless as
     * a baseline: a value derived to always include the current one cannot be the
     * thing you compare the current one against (L44), which is why C3b had to add
     * [bestBeforeRun] beside it.
     *
     * So best is now what it says: the best score as of the last time the run
     * store was read. It is read on entry, and again in [endRun] *after* the run
     * has been recorded — so the moment the number is genuinely beaten is the
     * moment the stacked-out sheet says so, which is where "new best!" already
     * lives. Nothing animates it: the header draws it as plain text, and only the
     * live score has a counter on it.
     */
    private fun GameUiState.published(
        best: Long = this.best,
        leftHanded: Boolean = this.leftHanded,
        ghostEnabled: Boolean = this.ghostEnabled,
        controlScheme: ControlScheme = this.controlScheme,
    ): GameUiState = copy(
        board = engine.board,
        falling = engine.falling,
        ghost = if (ghostEnabled) engine.landingCell else null,
        score = engine.score,
        best = best,
        level = engine.level,
        levelFraction = engine.blocksDropped % engine.config.blocksPerLevel /
            engine.config.blocksPerLevel.toFloat(),
        dropIndex = engine.blocksDropped,
        inDanger = engine.inDanger,
        biggestTier = tally.highestTier,
        leftHanded = leftHanded,
        ghostEnabled = ghostEnabled,
        controlScheme = controlScheme,
        mode = started.mode,
    )

    override fun onCleared() {
        appLifecycle.removeObserver(lifecycleObserver)
        tickerJob?.cancel()
        lockJob?.cancel()
        playbackJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val LockDelayMillis = 150L

        /**
         * Where SPEC 17's cascade histogram stops resolving individual depths.
         * Four steps and deeper is roughly one lock in a thousand; giving each
         * its own attribute would be a column of zeroes on every record.
         */
        const val DeepCascade = 4

        /**
         * SPEC 8.4: eight seconds to decide on the continue, then the offer
         * declines itself.
         *
         * Not remote. Every other ad number in section 12 is a frequency dial an
         * operator should be able to turn, and this one is a piece of interaction
         * design: it is how long the board stays on screen before the run is
         * over. A console value that could be set to one second would be a way to
         * take a continue away from a player who was looking at their board,
         * which is the exact thing the countdown exists to give them.
         */
        const val ContinueCountdownSeconds = 8

        const val CountdownStepMillis = 1_000L

        /**
         * What `run_record.cause` reads when the engine ended a run without
         * naming a reason. SPEC 18.1 says that is a bug rather than a state, so
         * it is stored as a value that can be counted rather than as a null that
         * cannot be told apart from an old row.
         */
        const val UnknownCause = "UNKNOWN"
    }
}

/**
 * [Ready] is the handoff's start overlay, and it exists only for a run that has
 * not begun. A resumed run skips it — see `GameViewModel.start`.
 */
/**
 * [ContinueOffer] sits between the last cascade and the results, and it is the
 * only phase where the board is covered but **not blurred** — SPEC 12.2 makes
 * the board the argument for taking the offer, so it has to stay readable.
 */
enum class GamePhase { Ready, Playing, Resolving, Paused, ContinueOffer, StackedOut }

/**
 * Everything on screen, and nothing the engine would call state.
 *
 * The board and the falling block are the engine's own types rather than
 * mirrored view models. There is one board in this app and one shape it can be
 * in; a parallel hierarchy would exist only to be kept in sync.
 */
data class GameUiState(
    val board: Board = Board.empty(1, 1),
    val falling: FallingBlock? = null,
    val ghost: Cell? = null,
    /** Which drop this is, so the falling block's animators reset per block. */
    val dropIndex: Int = 0,
    val score: Long = 0,
    val best: Long = 0,
    val level: Int = 1,
    val levelFraction: Float = 0f,
    val inDanger: Boolean = false,
    val phase: GamePhase = GamePhase.Ready,
    /** The cascade step the last chain reached, 0 when nothing has chained. */
    val chainStep: Int = 0,
    /** Where the chaining merge landed, so the callout floats off it (SPEC 8.2). */
    val chainCell: Cell? = null,

    /** Bumped per chain so a second `CHAIN x3` in a run still animates. */
    val chainNonce: Int = 0,

    /** The line the board is currently shouting over itself, if any. */
    val callout: GameCallout? = null,

    /**
     * Bumped per callout, for the same reason [chainNonce] is: two `ROW BUST!`s
     * in a run are the same string, and a toast keyed on its own text would play
     * once and then go quiet for the rest of the game.
     */
    val calloutNonce: Int = 0,
    val biggestTier: Int = 0,

    /**
     * Whether the run that just ended beat the score it started under.
     *
     * Endless only (decision D19). A Daily score cannot own the headline best, so
     * a Daily that celebrated one was promising a number the stats page would
     * then refuse to show — `bestScore()` filters Daily rows out.
     */
    val newBest: Boolean = false,

    /**
     * Badges the run that just ended unlocked, in catalog order (SPEC 15).
     *
     * Ids rather than names, because a `stringResource` needs a composition and
     * this class has none — the same split `GameCallout` makes. Empty on every
     * run that earned nothing, which after the first few is most of them.
     */
    val unlocked: List<AchievementId> = emptyList(),
    val leftHanded: Boolean = false,
    val ghostEnabled: Boolean = true,

    /**
     * SPEC 6's control scheme, and the reason it is on the state at all.
     *
     * It persisted and displayed from C11 and changed nothing: the board drew the
     * arrow row and accepted drag whatever the row said. A setting that visibly
     * does nothing is worse than no setting, so the screen now branches on this.
     *
     * Unlike the palette and reduce motion it cannot come down a CompositionLocal
     * (decision D4), because [Buttons][ControlScheme.Buttons] has to reach the
     * gesture handler on the board and the theme does not know what a board is.
     */
    val controlScheme: ControlScheme = ControlScheme.Both,

    /**
     * Whether the pause overlay is asking whether Quit was meant.
     *
     * On the state rather than remembered in the overlay because backgrounding
     * the app pauses the run and the question has to survive that.
     */
    val confirmingQuit: Boolean = false,

    /**
     * The beat of the guided run the board is waiting on, null in a real run
     * (SPEC 13).
     *
     * The copy is not in here. The screen resolves it from
     * [TutorialFrame.step], exactly as it does for [GameCallout], because a
     * `stringResource` needs a composition and this class has none.
     */
    val tutorial: TutorialFrame? = null,

    /**
     * Which rules are being played (SPEC 14).
     *
     * The screen reads it in exactly one place — the stacked-out sheet, where
     * "Drop again" would otherwise start an Endless run off the back of a Daily
     * one and quietly leave the mode. Everything else about a Daily run looks and
     * plays identically, which is the point.
     */
    val mode: GameMode = GameMode.ENDLESS,

    /**
     * Seconds left on the continue offer's auto-decline, published by the
     * ViewModel rather than animated by the screen.
     *
     * An integer because it is a rule: at zero the offer declines itself and the
     * run ends. A ring animating on its own spec can sit at 1.2 seconds while
     * the rule has already fired, and the player would be looking at a control
     * that no longer does anything.
     */
    val continueSecondsLeft: Int = 0,

    /** Set while the rewarded ad is up, so neither answer can be given twice. */
    val continueBusy: Boolean = false,

    /**
     * Whether the stacked-out sheet carries the quiet continue option — SPEC 12's
     * second continue, at the higher friction of having to be reached for.
     */
    val continueAvailable: Boolean = false,

    /**
     * SPEC 12's one non-modal Pro card, at most once per session. Decided when
     * the run ends and never recomputed, so a rotation cannot spend a second
     * session's worth of card on the same sheet.
     */
    val showUpsell: Boolean = false,
)

sealed interface GameEffect {
    /** Sound and haptic in one, played by the screen because both are Compose-scoped. */
    data class Play(val cue: Cue) : GameEffect

    data object Leave : GameEffect

    /** SPEC 15's stats page, opened from the stacked-out sheet. */
    data object OpenStats : GameEffect

    /** SPEC 14's Daily Challenge, opened from the stacked-out sheet. */
    data object OpenDaily : GameEffect

    /** SPEC 11's settings, opened from the pause overlay. */
    data object OpenSettings : GameEffect

    /**
     * SPEC 15's share sheet, from the stacked-out sheet.
     *
     * The effect carries numbers and a UTC day, never words: the copy is
     * resolved where there is a composition to resolve it in, and this class has
     * none. [day] is null for an Endless run.
     */
    data class Share(val result: ShareResult, val day: String?) : GameEffect
}

sealed interface GameAction {
    data object Enter : GameAction

    /** Play, from the start overlay. Only a [GamePhase.Ready] run answers it. */
    data object Start : GameAction
    data object Tick : GameAction
    data object LockNow : GameAction
    data object MoveLeft : GameAction
    data object MoveRight : GameAction

    /**
     * Drag steering (decision D11): the column the finger is currently over.
     *
     * Absolute rather than a delta, because the screen computes it as
     * `startCol + round(dx / cellWidth)` from the grab point. Sending deltas
     * instead would make the ViewModel accumulate rounding error the screen has
     * already avoided.
     */
    data class SteerTo(val col: Int) : GameAction

    /**
     * ▼, or a downward flick: send the block to the bottom and lock it there
     * (decision D21).
     *
     * One action, with no start-and-end pair to lose half of. The soft drop it
     * replaced was two actions and a flag, and the flag is what latched.
     */
    data object HardDrop : GameAction
    data object Pause : GameAction
    data object Resume : GameAction
    data object Restart : GameAction

    /**
     * The unlock toast finished, or was tapped away (SPEC 15).
     *
     * Clearing the list on the state rather than remembering "shown" in the
     * composable, because the stacked-out sheet survives a rotation and a
     * backgrounding, and a badge that re-announced itself every time the screen
     * recomposed would be a worse bug than one that never announced at all.
     */
    data object DismissUnlocks : GameAction

    /** The coach mark's button, on the beats that wait for one (SPEC 13). */
    data object TutorialAdvance : GameAction

    /** SPEC 13's skip, offered from drop 3. */
    data object TutorialSkip : GameAction

    /**
     * Run the tutorial again, from `GameRoute(replayTutorial = true)`.
     *
     * SPEC 13 puts this in Settings. Settings is C11, so the route argument is
     * the entry point until then.
     */
    data object ReplayTutorial : GameAction

    /**
     * Play today's Daily Challenge, from `GameRoute(mode = DAILY)`.
     *
     * A route argument rather than a second screen, and an action rather than a
     * constructor parameter, because the ViewModel is built before the back stack
     * entry is read. It mirrors [ReplayTutorial] exactly, and for the same
     * reason.
     */
    data object StartDaily : GameAction

    data object Quit : GameAction

    /** SPEC 15's share, offered on the stacked-out sheet. */
    data object Share : GameAction

    data object ShowStats : GameAction

    /**
     * SPEC 14's Daily Challenge, offered from the stacked-out sheet.
     *
     * The sheet is the only entry point until C11 builds a menu. It is also the
     * right one: the moment a player has just lost a run is exactly when a board
     * everyone else is also playing today is worth offering.
     */
    data object ShowDaily : GameAction
    /** `AppData` changed under a live board — see `settingsChanged`. */
    data class SettingsChanged(val data: AppData) : GameAction

    data object OpenSettings : GameAction
    data object ConfirmQuit : GameAction
    data object DismissQuitConfirm : GameAction

    /**
     * Reduce motion, read from `LocalReduceMotion` by the screen and forwarded.
     *
     * The one setting that has to cross this boundary: SPEC 16 shortens the
     * cascade rather than removing it, and the cascade's pace is a `delay` in a
     * driver coroutine rather than an animation spec, so no composable is in a
     * position to shorten it.
     */
    data class SetReduceMotion(val enabled: Boolean) : GameAction

    /** One step of the transcript, fed back in by the playback driver. */
    data class ShowFrame(val index: Int) : GameAction

    data object FinishResolution : GameAction

    /** Watch the ad and keep the board (SPEC 12). */
    data object ContinueAccept : GameAction

    /** "No thanks", or the countdown reaching zero. Both end the run. */
    data object ContinueDecline : GameAction

    /**
     * The second continue, from the stacked-out sheet rather than from an offer.
     * Its own action because it skips the countdown: a sheet is not something
     * that expires.
     */
    data object ContinueAgain : GameAction

    /** One second of the auto-decline, fed back in by the countdown coroutine. */
    data class ContinueTick(val secondsLeft: Int) : GameAction

    /** The upsell card was tapped (SPEC 12). Opens the paywall; buys nothing. */
    data object OpenPro : GameAction
}

/**
 * `this` with the count at [index] raised by one, grown with zeroes if it does
 * not reach that far.
 *
 * A list rather than a map because the index *is* the depth and the thing is
 * serialized into a saved run on every lock — a map would spend a key on every
 * entry to encode the number it is already sitting at.
 */
private fun List<Int>.incrementing(index: Int): List<Int> {
    val grown = if (size > index) toMutableList() else (this + List(index + 1 - size) { 0 }).toMutableList()
    grown[index] = grown[index] + 1
    return grown
}
