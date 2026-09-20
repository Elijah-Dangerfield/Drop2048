package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.GameState

/**
 * Where the guided run is up to.
 *
 * Pulled out of [GameViewModel] because it is a state machine of its own — a
 * script, a position in it, and which drop is on the board — and those three
 * had nothing to do with the fifteen other fields already in there.
 *
 * It owns no clock, no cache and no `updateState`. Every method returns a value
 * and changes nothing the player can see, which is what lets the whole
 * curriculum be driven from a test without a renderer.
 */
class TutorialRunner(private val config: EngineConfig = EngineConfig.Default) {

    private var index = NotStarted
    private var drop = 0

    val isRunning: Boolean get() = index in Tutorial.Script.indices

    /** Which of the six scripted drops is on the board, 0 before the first. */
    val currentDrop: Int get() = drop

    private val lesson: TutorialLesson? get() = Tutorial.Script.getOrNull(index)

    /**
     * What the screen should draw, or null when no script is running.
     *
     * `canSkip` is read off the lesson's drop rather than off [drop], so the beat
     * that hands the player back to the real game is skippable even though there
     * is no seventh drop.
     */
    val frame: TutorialFrame?
        get() = lesson?.let {
            TutorialFrame(
                step = it.step,
                focus = it.focus,
                speaks = it.speaks,
                awaitsTap = it.await == TutorialAwait.Tapped,
                canSkip = it.drop >= Tutorial.SkippableFromDrop,
                target = if (it.await == TutorialAwait.Tapped) null else scriptedDrop?.target,
            )
        }

    /**
     * The drop currently on the board, or null between scripts.
     *
     * Read off [drop] rather than off the lesson: a beat that is celebrating the
     * landing belongs to the drop it landed on, and the outline it would publish
     * is an outline over a cell the player has already filled.
     */
    private val scriptedDrop: TutorialDrop?
        get() = if (isRunning && drop in 1..Tutorial.Drops) Tutorial.drop(drop, config) else null

    /**
     * The column the block must be in before it may be dropped, or null when it
     * may be dropped wherever the player has steered it.
     *
     * This is the "restrict other movements" half of the opening beat. The drop
     * is the only input that can end a scripted drop (the clock is frozen), so
     * refusing it while the block is off target is what leaves steering as the
     * only thing that moves the lesson forward — and, unlike a beat that simply
     * waits, the board is saying where "on target" is the whole time.
     */
    val requiredColumn: Int? get() = scriptedDrop?.target?.col

    /**
     * Whether the beat on screen is a card waiting for its own button.
     *
     * The board is frozen while this is true, and that is load-bearing rather
     * than tidy. Before C3c the drop control stayed live under the card, so a
     * player who kept using it landed the *next* scripted drop before
     * acknowledging the card that introduces it. [stateForCurrentLesson] then had nothing to
     * install — the drop it wanted was already the drop on the board — and
     * `GameViewModel` nulled the falling block, leaving a beat waiting for a
     * landing that could never happen. On a frozen clock that is a permanent
     * deadlock on first launch, with no coach mark left to offer the skip.
     *
     * The scrim deliberately passes touches through (it lights a control and asks
     * the player to use it), so the freeze has to be here rather than in the
     * screen's hit testing.
     */
    val awaitsTap: Boolean get() = lesson?.await == TutorialAwait.Tapped

    /**
     * The columns the falling block may occupy, or null when nothing is running.
     *
     * See [TutorialDrop.allowedColumns] for why a scripted run clamps steering at
     * all.
     */
    val allowedColumns: IntRange?
        get() = if (isRunning && drop in 1..Tutorial.Drops) {
            Tutorial.drop(drop, config).allowedColumns
        } else {
            null
        }

    /** Starts the script at drop one and returns the state to force. */
    fun begin(): GameState {
        index = 0
        drop = 1
        return Tutorial.stateFor(drop, score = 0, config = config)
    }

    /**
     * The board the beat the script has just reached wants, or null when the one
     * already on screen is the right one.
     *
     * **The board follows the lesson, not the drop.** Drop 1 is four beats and
     * the last of them is a celebration: it has to sit over the 4 the player just
     * made, not over drop 2's board. Installing the next board on "a drop
     * finished" instead swapped it out from under that card, which is the whole
     * reason this is asked per beat.
     */
    fun stateForCurrentLesson(score: Long): GameState? {
        val next = lesson?.drop ?: return null
        if (next == drop || next > Tutorial.Drops) return null
        drop = next
        return Tutorial.stateFor(drop, score, config)
    }

    /**
     * Feeds a thing the player did to the current beat.
     *
     * Returns whether the beat moved, so the caller only republishes when there
     * is something new to draw.
     *
     * [column] is where the block ended up, and it is what a
     * [TutorialAwait.Steered] signal is judged against on a drop with a
     * [TutorialDrop.target]. A steer that stops short is a steer that has not
     * finished the sentence: the card stays up and the outline stays on the
     * cell, which is the only way the player can tell the difference between
     * "not yet" and "this control does nothing".
     */
    fun observe(signal: TutorialAwait, column: Int? = null): Boolean {
        if (signal == TutorialAwait.Dropped) return completeDrop()
        val current = lesson ?: return false
        if (current.await != signal) return false
        if (signal == TutorialAwait.Steered && !reachedTarget(column)) return false
        index += 1
        return true
    }

    private fun reachedTarget(column: Int?): Boolean {
        val required = requiredColumn ?: return true
        return column == required
    }

    /**
     * A landing finishes **every** remaining instruction for the drop it landed
     * on, not just the beat that was asking for it.
     *
     * The player is under no obligation to do the lessons in order, and since
     * decision D21 the overtaking is the common case rather than the awkward one:
     * a hard drop finishes the drop the instant it is made, so any beat still
     * waiting when the block lands has been overtaken by events rather than
     * skipped. Without this the script would sit on a beat forever while the
     * board moved on without it.
     *
     * Drop 1 used to be the example and is now the exception. It asks for a steer
     * and then for the drop, the block spawned where it could legally land, and a
     * player who went straight for the drop control never satisfied the steering
     * beat — which is the bug the owner reported from the other end: the first
     * instruction the game gives could be passed by ignoring it. The drop is
     * refused off target now, so on drop 1 the steer cannot be overtaken. Every
     * other drop still works exactly as above.
     *
     * A beat that waits on the card's own button is not overtaken: it is the
     * celebration *after* the landing, and it is the one thing a drop finishing
     * is supposed to reveal.
     */
    private fun completeDrop(): Boolean {
        val from = index
        while (index < Tutorial.Script.size) {
            val next = Tutorial.Script[index]
            if (next.drop != drop || next.await == TutorialAwait.Tapped) break
            index += 1
        }
        return index != from
    }

    /** Abandons the guided run for good, from the skip or from the last beat. */
    fun stop() {
        index = NotStarted
        drop = 0
    }

    private companion object {
        const val NotStarted = -1
    }
}
