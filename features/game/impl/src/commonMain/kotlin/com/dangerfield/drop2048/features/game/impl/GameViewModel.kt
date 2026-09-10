package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cascade
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.Direction
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.GameEvent
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.cascade.Input
import com.dangerfield.drop2048.libraries.cascade.Transition
import androidx.lifecycle.viewModelScope
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.core.logging.logEvent
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.drop2048.AppLifecycle
import com.dangerfield.drop2048.libraries.drop2048.AppLifecycleObserver
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import com.dangerfield.drop2048.libraries.flowroutines.collectIn
import com.dangerfield.drop2048.libraries.progress.GameMode
import com.dangerfield.drop2048.libraries.progress.ProgressRepository
import com.dangerfield.drop2048.libraries.progress.RunRecord
import com.dangerfield.drop2048.libraries.progress.daily.DailyAttempt
import com.dangerfield.drop2048.libraries.progress.daily.DailyRepository
import com.dangerfield.drop2048.libraries.ui.system.Cue
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
    private val clock: Clock,
    private val appLifecycle: AppLifecycle,
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
     * The best score as it stood **before** this run, which is the only number
     * that can answer "did they beat it".
     *
     * `GameUiState.best` cannot: it is `maxOf(best, score)` on every publish, so
     * it has already absorbed the live score and is equal to it by the time the
     * run ends. Comparing against that would light "new best!" on every run.
     */
    private var bestBeforeRun: Long = 0

    private var frames: List<PlaybackFrame> = emptyList()
    private var frameIndex: Int = 0

    /** What [frames] were derived from, kept so a save can hand them back. */
    private var resolutionSnapshot: SavedResolution? = null

    private var tickerJob: Job? = null
    private var lockJob: Job? = null
    private var playbackJob: Job? = null

    private var softDropping = false
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
            GameAction.Nudge -> action.nudge()
            GameAction.SoftDropStart -> action.softDrop(on = true)
            GameAction.SoftDropEnd -> action.softDrop(on = false)
            GameAction.Pause -> action.pause()
            GameAction.Resume -> action.resume()
            GameAction.Restart -> action.restart()
            GameAction.StartDaily -> action.startDaily()
            GameAction.TutorialAdvance -> action.tutorialAdvance()
            GameAction.TutorialSkip -> action.tutorialSkip()
            GameAction.ReplayTutorial -> action.replayTutorial()
            GameAction.Quit -> action.quit()
            GameAction.ShowStats -> sendEvent(GameEffect.OpenStats)
            GameAction.ShowDaily -> sendEvent(GameEffect.OpenDaily)
            is GameAction.SettingsChanged -> action.settingsChanged(action.data)
            GameAction.OpenSettings -> sendEvent(GameEffect.OpenSettings)
            GameAction.ConfirmQuit -> sendEvent(GameEffect.Leave)
            GameAction.DismissQuitConfirm -> action.updateState { it.copy(confirmingQuit = false) }
            is GameAction.SetReduceMotion -> reduceMotion = action.enabled
            is GameAction.ShowFrame -> action.showFrame(action.index)
            GameAction.FinishResolution -> action.finishResolution()
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
        val saved = if (teach) null else savedRunStore.load()?.takeUnless { it.state.isOver }
        bestBeforeRun = best ?: 0

        if (teach) {
            beginTutorial()
        } else if (saved != null) {
            started = StartedRun(state = saved.state, seed = saved.seed, mode = saved.mode)
            engine = saved.state
            tally = saved.tally
            dailyDate = saved.dailyDate
            logger.logEvent("run.resume", "level" to engine.level, "score" to engine.score)
        } else {
            beginRun()
        }

        updateState {
            it.published(
                best = best ?: 0,
                leftHanded = cached?.leftHandedControls ?: false,
                ghostEnabled = cached?.ghostEnabled ?: true,
            )
        }

        val resolution = saved?.resolution
        when {
            teach -> updateState { it.copy(phase = GamePhase.Playing, tutorial = tutorial.frame) }

            resolution != null -> restoreResolution(resolution)

            saved != null -> {
                updateState { it.copy(phase = GamePhase.Playing) }
                beginPlaying()
            }

            else -> {
                updateState { it.copy(phase = GamePhase.Ready) }
                saveRun()
            }
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
        savedRunStore.clear()
        engine = tutorial.begin()
        started = StartedRun(state = engine, seed = started.seed, mode = started.mode)
        tally = RunTally()
        frames = emptyList()
        frameIndex = 0
        resolutionSnapshot = null
        lockPending = false
        lockResetUsed = false
        softDropping = false
        bufferedMove = null
        playingSince = null
        logger.logEvent("tutorial.started")
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
     */
    private suspend fun GameAction.finishTutorial() {
        logger.logEvent("tutorial.completed", "drop" to tutorial.currentDrop)
        tutorial.stop()
        Catching { appCache.update { data -> data.copy(hasUserOnboarded = true) } }
            .logOnFailure { "Could not persist tutorial completion" }
        resetToNewRun()
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
        updateState { it.copy(phase = GamePhase.Playing) }
        beginPlaying()
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

    private fun adopt(run: StartedRun, day: String?) {
        started = run
        dailyDate = day
        engine = run.state
        tally = RunTally(highestTier = engine.board.highestValue()?.points ?: 0)
        logger.logEvent("run.start", "level" to engine.level, "mode" to run.mode.name)
    }

    /** Starts the drop timer and the stretch of play the duration is made of. */
    private fun beginPlaying() {
        playingSince = clock.now().toEpochMilliseconds()
        restartTicker()
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
        val step = if (input == Input.MoveLeft) -1 else 1
        val target = (engine.falling?.cell?.col ?: return) + step
        if (!tutorialAllows(target)) return
        val transition = Cascade.apply(engine, input)
        if (transition.isRejected) return
        engine = transition.state
        sendEvent(GameEffect.Play(Cue.Move))
        updateState { it.published() }
        noteTutorial(TutorialAwait.Steered)
        if (lockPending && !lockResetUsed) {
            lockResetUsed = true
            scheduleLock()
        }
    }

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
     * The ▼ control and the downward flick (decision D11).
     *
     * Two rows and no lock, so unlike the hard drop it replaced this does **not**
     * start a resolution. What it does have to do is arm the lock delay: without
     * that, nudging a block onto the stack leaves it sitting there until the next
     * drop tick notices, and at level 1 that is half a second of a block visibly
     * resting on the pile doing nothing. That is the same arming [tick] does, and
     * for the same reason.
     *
     * Deliberately not buffered during a resolution. A move that arrives mid
     * cascade is replayed on the next block because a sideways step is cheap and
     * recoverable; two rows of fall on a board the player has not looked at yet
     * is not.
     */
    private suspend fun GameAction.nudge() {
        if (state.phase != GamePhase.Playing) return
        val transition = Cascade.apply(engine, Input.Nudge)
        if (transition.isRejected) return
        engine = transition.state
        sendEvent(GameEffect.Play(Cue.Move))
        updateState { it.published() }
        noteTutorial(TutorialAwait.Nudged)

        val falling = engine.falling ?: return
        if (!engine.board.isEmpty(falling.cell + Direction.DOWN) && !lockPending) {
            lockPending = true
            scheduleLock()
        }
    }

    private fun GameAction.softDrop(on: Boolean) {
        if (softDropping == on) return
        softDropping = on
        if (state.phase == GamePhase.Playing) restartTicker()
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
        playingSince = clock.now().toEpochMilliseconds()
        if (frameIndex < frames.size) {
            updateState { it.copy(phase = GamePhase.Resolving) }
            drivePlayback()
        } else {
            updateState { it.copy(phase = GamePhase.Playing) }
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
        tickerJob?.cancel()
        lockJob?.cancel()
        playbackJob?.cancel()
        frames = emptyList()
        frameIndex = 0
        resolutionSnapshot = null
        lockPending = false
        lockResetUsed = false
        softDropping = false
        bufferedMove = null
        playingSince = null
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
     * Only the three the engine or the layout reads. The palette, reduce motion
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
                ghost = if (data.ghostEnabled) engine.landingCell else null,
            )
        }
    }

    /**
     * Quit, which ends the run and cannot be undone.
     *
     * It sits a thumb-width from Restart on the pause overlay, so it asks first
     * unless the player has turned that off in settings.
     */
    private suspend fun GameAction.quit() {
        if (!confirmBeforeQuit) {
            sendEvent(GameEffect.Leave)
            return
        }
        updateState { it.copy(confirmingQuit = true) }
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
            endRun()
            return
        }
        if (tutorial.isRunning) {
            advanceTutorialDrop()
            return
        }
        updateState { it.copy(phase = GamePhase.Playing).published() }
        saveRun()
        bufferedMove?.let { buffered ->
            bufferedMove = null
            val transition = Cascade.apply(engine, buffered)
            if (!transition.isRejected) {
                engine = transition.state
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
     */
    private suspend fun GameAction.endRun() {
        tickerJob?.cancel()
        stopPlaying()
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
        Catching { progress.record(record) }.logOnFailure { "Could not record the run" }
        bankDailyResult(score)
        savedRunStore.clear()
        val best = Catching { progress.bestScore() }.logOnFailure { "Could not read best score" }
            .getOrNull() ?: maxOf(state.best, score)
        logger.logEvent(
            "run.end",
            "score" to score,
            "level" to engine.level,
            "blocks" to engine.blocksDropped,
            "highest_tier" to tally.highestTier,
            "cause" to record.cause,
        )
        updateState {
            it.published(best = best).copy(
                phase = GamePhase.StackedOut,
                falling = null,
                ghost = null,
                newBest = score > 0 && score > bestBeforeRun,
            )
        }
        bestBeforeRun = best
    }

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
     */
    private suspend fun bankDailyResult(score: Long) {
        if (started.mode != GameMode.DAILY) return
        val day = dailyDate ?: return
        Catching { daily.recordAttempt(LocalDate.parse(day), score) }
            .logOnFailure { "Could not record the Daily result" }
        logger.logEvent("daily.end", "date" to day, "score" to score)
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

        val transition = Cascade.apply(engine, Input.Lock)
        engine = transition.state
        tallyUp(transition)
        reportFaults(transition)

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
        tickerJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalMillis())
                takeAction(GameAction.Tick)
            }
        }
    }

    private fun scheduleLock() {
        lockJob?.cancel()
        lockJob = viewModelScope.launch {
            delay(LockDelayMillis)
            takeAction(GameAction.LockNow)
        }
    }

    /**
     * SPEC 5.5. Soft drop is a flat 40ms at every level rather than a multiplier,
     * which is the line that keeps it useful at level 1 and not a cheat at 20.
     */
    private fun intervalMillis(): Long {
        val curve = engine.config.speed
        return if (softDropping) curve.softDropMsPerRow.toLong() else curve.msPerRow(engine.level).toLong()
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
            highestTier = reached,
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

    private fun GameUiState.published(
        best: Long = this.best,
        leftHanded: Boolean = this.leftHanded,
        ghostEnabled: Boolean = this.ghostEnabled,
    ): GameUiState = copy(
        board = engine.board,
        falling = engine.falling,
        ghost = if (ghostEnabled) engine.landingCell else null,
        score = engine.score,
        best = maxOf(best, engine.score),
        level = engine.level,
        levelFraction = engine.blocksDropped % engine.config.blocksPerLevel /
            engine.config.blocksPerLevel.toFloat(),
        dropIndex = engine.blocksDropped,
        inDanger = engine.inDanger,
        biggestTier = tally.highestTier,
        leftHanded = leftHanded,
        ghostEnabled = ghostEnabled,
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
enum class GamePhase { Ready, Playing, Resolving, Paused, StackedOut }

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

    /** Whether the run that just ended beat the score it started under. */
    val newBest: Boolean = false,
    val leftHanded: Boolean = false,
    val ghostEnabled: Boolean = true,

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

    /** ▼, or a downward flick. Two rows of fall (decision D11). */
    data object Nudge : GameAction
    data object SoftDropStart : GameAction
    data object SoftDropEnd : GameAction
    data object Pause : GameAction
    data object Resume : GameAction
    data object Restart : GameAction

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
}
