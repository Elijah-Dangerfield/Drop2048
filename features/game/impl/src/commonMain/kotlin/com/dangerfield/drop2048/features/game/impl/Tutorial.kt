package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.EngineConfig
import com.dangerfield.drop2048.libraries.cascade.FallingBlock
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.cascade.Rng

/**
 * One beat of the guided run (SPEC 13).
 *
 * The enum is declared in the order of [Tutorial.Script], so the whole
 * curriculum reads top to bottom.
 */
enum class TutorialStep {
    /** Drop 1. Two 2s, one of them already on the board. */
    Steer,
    FirstDrop,
    FirstMerge,

    /** Drops 2-4. The drop control, again, until it is a habit. */
    SecondDrop,
    ThirdDrop,
    FourthDrop,

    /** Drop 5. A three-step cascade, with no explanation at all. */
    WatchThis,
    CascadeDrop,

    /** Drop 6. A 1024 beside two 512s. */
    BurstIntro,
    BurstDrop,

    /** The handoff into the real run. */
    Handoff,
}

/** What finishes a beat. */
enum class TutorialAwait {
    /** The player pressed the card's button. */
    Tapped,

    /** The player moved the falling block sideways, by drag or by arrow. */
    Steered,

    /**
     * The block landed and its whole cascade finished playing.
     *
     * Since decision D21 this is also "the player pressed ▼", because with the
     * clock frozen a hard drop is the only thing that can produce a landing. The
     * separate `Nudged` signal retired with the nudge: it existed so drop 1 could
     * ask for the *first* of several presses, and there is only one press now.
     */
    Dropped,
}

/** What a beat lights up while the rest of the screen dims. */
enum class TutorialFocus {
    /** Nothing in particular. The card is talking about the game, not a control. */
    None,

    /** The board, so the player can see what they are being asked to steer. */
    Board,

    /** The ▼ control, which is the one thing this tutorial exists to teach. */
    Drop,
}

/**
 * One beat: what it waits for, what it lights, and whether it says anything.
 *
 * [drop] is the drop the beat belongs to. It is what "skippable from drop 3"
 * (SPEC 13) is read off, and it is why several beats share a number — drop 1 is
 * four beats, because the first drop is the only one where the player is being
 * told what the controls are.
 */
data class TutorialLesson(
    val step: TutorialStep,
    val drop: Int,
    val await: TutorialAwait,
    val focus: TutorialFocus = TutorialFocus.None,
    /** Whether a coach mark is drawn. A silent beat is the player just playing. */
    val speaks: Boolean = true,
)

/**
 * What the screen needs to draw the beat it is on. The copy itself is resolved in
 * the composable from [step], the same way [GameCallout] is — the ViewModel has
 * no `stringResource`.
 */
data class TutorialFrame(
    val step: TutorialStep,
    val focus: TutorialFocus,
    val speaks: Boolean,
    val awaitsTap: Boolean,
    val canSkip: Boolean,
)

/**
 * One forced drop: the board it starts from, the block that falls, and the
 * columns the player is allowed to put it in.
 *
 * [allowedColumns] is the part that makes a scripted run scriptable. Every
 * column in the range produces the *same* resolved board, which was checked
 * placement by placement against the merge rules in SPEC 4.3 and is pinned by
 * `TutorialTest`. Without it, drop 5's showpiece cascade is one mis-steer away
 * from a block landing inert in a corner, and the moment the whole beat exists
 * for never happens.
 */
data class TutorialDrop(
    val placed: Map<Cell, Block>,
    val falling: Block,
    val allowedColumns: IntRange,
)

/**
 * The curriculum, as data.
 *
 * ### Why the timer stays frozen, and what that buys
 *
 * SPEC 13 freezes the drop clock for the whole tutorial, and the reason given
 * there is that a scripted run should not be a race. It turns out to do
 * something much more valuable, and it is the single design decision in this
 * chunk: **with gravity switched off, ▼ is the only thing that makes a block
 * land.** A player cannot reach the end of six drops without pressing it, and
 * they will never once have watched a block come down on its own.
 *
 * C1c measured that whether a player uses the drop control is worth 223 seconds
 * against 33 to reach level 4 (L29) — a bigger lever on the opening than the drop
 * clock, the spawn table and every speed-curve change put together. A tutorial
 * that *mentions* the control teaches a fact. A tutorial the player cannot
 * finish without it teaches a habit, and the habit is what the number is about.
 *
 * **Decision D21 made the mechanism cheaper rather than weaker.** ▼ is a hard
 * drop now, so a scripted drop is one press instead of four or five. That cost
 * the script a beat: drop 1 used to ask for a first press and then for several
 * more, and "press it again" is not a thing that can be asked of a control that
 * finishes the drop on the first press. The two beats are one, and the six drops
 * still cannot be reached the end of without using it.
 */
object Tutorial {

    /** How many scripted drops the player makes. */
    const val Drops = 6

    /** SPEC 13: skippable from drop 3, not before. */
    const val SkippableFromDrop = 3

    val Script: List<TutorialLesson> = listOf(
        TutorialLesson(TutorialStep.Steer, drop = 1, await = TutorialAwait.Steered, focus = TutorialFocus.Board),
        TutorialLesson(TutorialStep.FirstDrop, 1, TutorialAwait.Dropped, TutorialFocus.Drop),
        TutorialLesson(TutorialStep.FirstMerge, 1, TutorialAwait.Tapped),
        TutorialLesson(TutorialStep.SecondDrop, 2, TutorialAwait.Dropped, TutorialFocus.Drop),
        TutorialLesson(TutorialStep.ThirdDrop, 3, TutorialAwait.Dropped, TutorialFocus.Drop),
        TutorialLesson(TutorialStep.FourthDrop, 4, TutorialAwait.Dropped, speaks = false),
        TutorialLesson(TutorialStep.WatchThis, 5, TutorialAwait.Tapped),
        TutorialLesson(TutorialStep.CascadeDrop, 5, TutorialAwait.Dropped, speaks = false),
        TutorialLesson(TutorialStep.BurstIntro, 6, TutorialAwait.Tapped),
        TutorialLesson(TutorialStep.BurstDrop, 6, TutorialAwait.Dropped, speaks = false),
        TutorialLesson(TutorialStep.Handoff, drop = Drops + 1, await = TutorialAwait.Tapped),
    )

    /**
     * The six boards, bottom row first.
     *
     * Drops 1-4 are one continuous board: each one is exactly what the player's
     * own previous placement left behind, so nothing ever pops into existence
     * while they are looking at it. Drops 5 and 6 are set pieces and arrive with
     * a card in front of them, which is what makes the board change read as a
     * scene rather than as a glitch.
     *
     * **Every partner sits beside the spawn column, and that is load-bearing.**
     * The scripted merge has to happen for a player who only ever presses ▼,
     * because that is the player L49 deliberately produces — with the clock
     * frozen, ▼ is the only input that makes progress, so most first-timers never
     * steer at all. A partner the block cannot reach without a drag would let the
     * merge be missed, and the next drop's board would then arrive already
     * holding the tier that merge was supposed to make. The lesson would not
     * fail; it would silently correct itself, which is worse, because a board
     * that fixes itself behind the player is exactly what makes a scripted run
     * read as arbitrary.
     *
     * Drop 4 used to break this by placing a second 2 three columns over. It was
     * reachable, so the merge still happened, but the tile appeared out of
     * nowhere on a beat with no card in front of it. It is now the same 16 the
     * player just made, merged with a 16, which keeps the ladder reading
     * 2 → 4 → 8 → 16 → 32 and puts nothing on the board they did not put there.
     */
    fun drop(index: Int, config: EngineConfig): TutorialDrop {
        val floor = config.rows - 1
        fun at(col: Int) = Cell(col, floor)
        return when (index) {
            1 -> TutorialDrop(mapOf(at(1) to tier(BlockValue.V2)), tier(BlockValue.V2), 1..2)
            2 -> TutorialDrop(mapOf(at(1) to tier(BlockValue.V4)), tier(BlockValue.V4), 1..2)
            3 -> TutorialDrop(mapOf(at(1) to tier(BlockValue.V8)), tier(BlockValue.V8), 1..2)
            4 -> TutorialDrop(mapOf(at(1) to tier(BlockValue.V16)), tier(BlockValue.V16), 1..2)

            5 -> TutorialDrop(
                placed = mapOf(
                    at(0) to tier(BlockValue.V16),
                    at(1) to tier(BlockValue.V8),
                    at(2) to tier(BlockValue.V4),
                ),
                falling = tier(BlockValue.V4),
                allowedColumns = 2..3,
            )

            else -> TutorialDrop(
                placed = mapOf(at(1) to tier(BlockValue.V1024), at(2) to tier(BlockValue.V512)),
                falling = tier(BlockValue.V512),
                allowedColumns = 2..3,
            )
        }
    }

    /**
     * The forced [GameState] for [index], carrying [score] forward from the drop
     * before it.
     *
     * The RNG is a fixed seed and is never read for anything the player sees: the
     * block that the engine spawns after each lock is replaced by the next
     * scripted state before playback ends. It is here because a `GameState`
     * cannot exist without one, not because the tutorial is random.
     */
    fun stateFor(index: Int, score: Long, config: EngineConfig): GameState {
        val drop = drop(index, config)
        return GameState(
            config = config,
            board = Board.empty(config.cols, config.rows).withAll(drop.placed),
            falling = FallingBlock(drop.falling, Cell(config.spawnColumn, 0)),
            score = score,
            level = 1,
            blocksDropped = index - 1,
            drawsMade = config.specialSuppressedDraws,
            rng = Rng(Seed),
        )
    }

    private fun tier(value: BlockValue): Block = NumberBlock(value)

    /** Fixed, and unread. See [stateFor]. */
    private const val Seed = 20_480L
}
