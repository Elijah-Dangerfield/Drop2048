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
import com.dangerfield.drop2048.libraries.drop2048.AppLifecycle
import com.dangerfield.drop2048.libraries.drop2048.AppLifecycleObserver
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import com.dangerfield.drop2048.libraries.progress.ProgressRepository
import com.dangerfield.drop2048.libraries.progress.RunRecord
import com.dangerfield.drop2048.libraries.ui.system.Cue
import com.dangerfield.drop2048.libraries.ui.system.reduced
import kotlin.time.Clock
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
    private val clock: Clock,
    private val appLifecycle: AppLifecycle,
) : SEAViewModel<GameUiState, GameEffect, GameAction>(initialStateArg = GameUiState()) {

    private val logger = KLog.withTag("Game")

    private var started: StartedRun = runFactory.newRun()
    private var engine: GameState = started.state
    private var tally: RunTally = RunTally()

    /** Epoch millis the current stretch of play began, null while not playing. */
    private var playingSince: Long? = null

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

    /**
     * The one sideways nudge the player made while a cascade was on screen.
     *
     * SPEC 6 says input during resolution is ignored, and it is: nothing here
     * reaches the engine mid-cascade. But *discarding* it turned out to be a
     * different rule, and a bad one — playing this on device, every move tapped
     * in the beat after a hard drop vanished, so the block that had just spawned
     * went straight down the middle. The player did the right thing and the game
     * did nothing.
     *
     * So the last one is held and replayed once the next block exists. Only
     * sideways moves, and only the most recent: a queue would let a burst of
     * panicked taps march a block across the board on its own, and a buffered
     * hard drop would kill a run the player had not looked at yet.
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
    }

    override suspend fun handleAction(action: GameAction) {
        when (action) {
            GameAction.Enter -> action.enter()
            GameAction.Tick -> action.tick()
            GameAction.LockNow -> action.lockNow()
            GameAction.MoveLeft -> action.move(Input.MoveLeft)
            GameAction.MoveRight -> action.move(Input.MoveRight)
            GameAction.HardDrop -> action.hardDrop()
            GameAction.Hold -> action.hold()
            GameAction.SoftDropStart -> action.softDrop(on = true)
            GameAction.SoftDropEnd -> action.softDrop(on = false)
            GameAction.Pause -> action.pause()
            GameAction.Resume -> action.resume()
            GameAction.Restart -> action.restart()
            GameAction.Quit -> sendEvent(GameEffect.Leave)
            GameAction.ShowStats -> sendEvent(GameEffect.OpenStats)
            GameAction.ToggleHandedness -> action.toggleHandedness()
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
        val saved = savedRunStore.load()?.takeUnless { it.state.isOver }

        if (saved != null) {
            started = StartedRun(state = saved.state, seed = saved.seed, mode = saved.mode)
            engine = saved.state
            tally = saved.tally
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
        if (resolution != null) {
            restoreResolution(resolution)
        } else {
            beginPlaying()
            if (saved == null) saveRun()
        }
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

    private fun beginRun() {
        started = runFactory.newRun()
        engine = started.state
        tally = RunTally(highestTier = engine.board.highestValue()?.points ?: 0)
        logger.logEvent("run.start", "level" to engine.level)
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
        lock(Input.Lock)
    }

    private suspend fun GameAction.move(input: Input) {
        if (state.phase == GamePhase.Resolving) {
            bufferedMove = input
            return
        }
        if (state.phase != GamePhase.Playing) return
        val transition = Cascade.apply(engine, input)
        if (transition.isRejected) return
        engine = transition.state
        sendEvent(GameEffect.Play(Cue.Move))
        updateState { it.published() }
        if (lockPending && !lockResetUsed) {
            lockResetUsed = true
            scheduleLock()
        }
    }

    private suspend fun GameAction.hardDrop() {
        if (state.phase != GamePhase.Playing) return
        if (engine.falling == null) return
        sendEvent(GameEffect.Play(Cue.HardDrop))
        lock(Input.HardDrop)
    }

    private suspend fun GameAction.hold() {
        if (state.phase != GamePhase.Playing) return
        val transition = Cascade.apply(engine, Input.Hold)
        if (transition.isRejected) return
        engine = transition.state
        sendEvent(GameEffect.Play(Cue.Spawn))
        updateState { it.published() }
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
        beginRun()
        updateState { it.copy(phase = GamePhase.Playing).published() }
        saveRun()
        beginPlaying()
    }

    private suspend fun GameAction.toggleHandedness() {
        val flipped = !state.leftHanded
        updateState { it.copy(leftHanded = flipped) }
        Catching { appCache.update { data -> data.copy(leftHandedControls = flipped) } }
            .logOnFailure { "Could not persist handedness" }
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
            )
        }
    }

    private suspend fun GameAction.lock(input: Input) {
        lockJob?.cancel()
        lockPending = false
        lockResetUsed = false
        tickerJob?.cancel()

        bufferedMove = null
        val landing = engine.landingCell
        val block = engine.falling?.block
        val before = if (landing != null && block != null) engine.board.with(landing, block) else engine.board
        val scoreBefore = engine.score

        val transition = Cascade.apply(engine, input)
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

    private fun restartTicker() {
        tickerJob?.cancel()
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
        savedRunStore.save(
            SavedRun(
                state = engine,
                tally = tallyIncludingTimeSoFar(),
                seed = started.seed,
                mode = started.mode,
                resolution = resolutionSnapshot?.takeIf { frameIndex < frames.size }
                    ?.copy(frameIndex = frameIndex),
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
        next = engine.preview,
        hold = engine.hold,
        canHold = engine.config.holdEnabled && !engine.holdUsedThisDrop,
        dropIndex = engine.blocksDropped,
        inDanger = engine.inDanger,
        biggestTier = tally.highestTier,
        leftHanded = leftHanded,
        ghostEnabled = ghostEnabled,
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

enum class GamePhase { Playing, Resolving, Paused, StackedOut }

/**
 * Everything on screen, and nothing the engine would call state.
 *
 * The board, the falling block and the preview are the engine's own types rather
 * than mirrored view models. There is one board in this app and one shape it can
 * be in; a parallel hierarchy would exist only to be kept in sync.
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
    val next: List<Block> = emptyList(),
    val hold: Block? = null,
    val canHold: Boolean = true,
    val inDanger: Boolean = false,
    val phase: GamePhase = GamePhase.Playing,
    /** The cascade step the last chain reached, 0 when nothing has chained. */
    val chainStep: Int = 0,
    /** Where the chaining merge landed, so the callout floats off it (SPEC 8.2). */
    val chainCell: Cell? = null,

    /** Bumped per chain so a second `CHAIN x3` in a run still animates. */
    val chainNonce: Int = 0,
    val biggestTier: Int = 0,
    val leftHanded: Boolean = false,
    val ghostEnabled: Boolean = true,
)

sealed interface GameEffect {
    /** Sound and haptic in one, played by the screen because both are Compose-scoped. */
    data class Play(val cue: Cue) : GameEffect

    data object Leave : GameEffect

    /** SPEC 15's stats page, opened from the stacked-out sheet. */
    data object OpenStats : GameEffect
}

sealed interface GameAction {
    data object Enter : GameAction
    data object Tick : GameAction
    data object LockNow : GameAction
    data object MoveLeft : GameAction
    data object MoveRight : GameAction
    data object HardDrop : GameAction
    data object Hold : GameAction
    data object SoftDropStart : GameAction
    data object SoftDropEnd : GameAction
    data object Pause : GameAction
    data object Resume : GameAction
    data object Restart : GameAction
    data object Quit : GameAction
    data object ShowStats : GameAction
    data object ToggleHandedness : GameAction

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
