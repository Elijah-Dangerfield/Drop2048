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

    /**
     * The falling block reached the cell the beat outlined.
     *
     * It used to mean "moved at all, in any direction", which made the opening
     * beat satisfiable by a twitch — and, before the block moved off the target
     * column, by doing nothing whatsoever. A beat that asks the player to put the
     * block somewhere is finished by the block being there, not by the player
     * having touched it.
     */
    Steered,

    /**
     * The block landed and its whole cascade finished playing.
     *
     * Since decision D21 this is also "the player dropped it", because with the
     * clock frozen a hard drop is the only thing that can produce a landing. The
     * separate `Nudged` signal retired with the nudge: it existed so drop 1 could
     * ask for the *first* of several presses, and there is only one press now.
     */
    Dropped,
}

/**
 * What a beat lights up while the rest of the screen dims.
 *
 * **A focus names a role, not a widget**, which is the fix for the bug this enum
 * caused. [Drop] used to be read as "the ▼ button" and was resolved to that
 * button's focus key unconditionally, so under `ControlScheme.Drag` — where
 * there is no button row at all — the key was never registered, the scrim punched
 * no hole, and the beat that exists to point at the drop control pointed at
 * nothing. The screen resolves a focus against the controls the player actually
 * has (`TutorialFocus.spotlightKeys`), and `TutorialFocusKeysTest` renders every
 * scheme to prove each key a lesson asks for is one the screen puts on screen.
 */
enum class TutorialFocus {
    /** Nothing in particular. The card is talking about the game, not a control. */
    None,

    /** The board, so the player can see what they are being asked to steer. */
    Board,

    /**
     * The board, with the card hung off the cell the beat is outlining.
     *
     * Only the opening beat, and only because a card anchored on the whole board
     * drops to the bottom of the screen — which is where the outlined cell is.
     * The first frame of the tutorial was the instruction sitting on top of the
     * thing it was pointing at. Anchoring on the cell puts the card above it.
     *
     * A beat with this focus must belong to a drop with a [TutorialDrop.target];
     * `TutorialTest` pins that, because an anchor key nothing registers is the
     * silent failure this enum already caused once.
     */
    Target,

    /**
     * The hard drop, which is the one thing this tutorial exists to teach.
     *
     * Under `Buttons` and `Both` that is the ▼ button. Under `Drag` it is a
     * downward flick on the board, so the board is what gets lit — a gesture has
     * no rectangle of its own.
     */
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
    /**
     * The cell the board outlines while this beat runs, or null for the beats
     * that do not place the block for the player.
     *
     * Only the opening beat has one. It is the visible half of the same rule
     * [TutorialDrop.target] enforces: the block may not be dropped anywhere else,
     * so the player is shown where "anywhere else" is not.
     */
    val target: Cell? = null,
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
 *
 * Drop 1 pins the placement outright with [target] instead of relying on every
 * column agreeing, because it is the drop that teaches steering and a lesson
 * about aiming cannot also be a lesson you can pass without aiming. The range
 * there is the *path* the block may travel, not a set of equivalent endings.
 */
data class TutorialDrop(
    val placed: Map<Cell, Block>,
    val falling: Block,
    val allowedColumns: IntRange,
    /**
     * Which column the block spawns in. Every drop says, and none of them ask
     * the engine.
     *
     * Drop 1 spawns at the far edge, because the beat in front of it says to
     * slide the block somewhere and an instruction the player satisfies by
     * standing still is not an instruction. Every other drop spawns in the
     * middle, beside its partner.
     *
     * This used to be nullable and used to mean "null spawns wherever the engine
     * would have". The owner's 2026-09-20 ruling made the engine's entry column
     * a uniform draw, which would have handed drops 2-6 a block one steer away
     * from a partner the player has not been taught to reach yet — a scripted
     * merge that does not happen, on a beat with no card in front of it. The
     * scripted spawn is now stated per drop, so the next change to the engine's
     * spawn rule cannot reach the tutorial without editing this file.
     */
    val spawnColumn: Int,
    /**
     * The one cell this drop may land in, or null when any allowed column does.
     *
     * The opening beat is the only drop that has one. It is what makes the beat
     * unsatisfiable by inaction: the hard drop is refused while the block is
     * somewhere else, so the taught gesture is the only input that leads
     * anywhere. The board outlines it (see [TutorialFrame.target]) rather than
     * leaving the player to discover a rule by being ignored.
     *
     * It is a property of the *drop*, not of the beat, because the beat after it
     * asks for the drop itself — a restriction that lapsed when the steer beat
     * ended would let the very next input undo it.
     */
    val target: Cell? = null,
)

/**
 * The curriculum, as data.
 *
 * ### Why the timer stays frozen, and what that buys
 *
 * SPEC 13 freezes the drop clock for the whole tutorial, and the reason given
 * there is that a scripted run should not be a race. It turns out to do
 * something much more valuable, and it is the single design decision in this
 * chunk: **with gravity switched off, the player's own drop input is the only
 * thing that makes a block land.** A player cannot reach the end of six drops
 * without using it, and they will never once have watched a block come down on
 * its own. Which control that is — the ▼ button, or a downward flick under the
 * default scheme — is the one thing about this that the script does not care
 * about.
 *
 * C1c measured that whether a player uses the drop control is worth 223 seconds
 * against 33 to reach level 4 (L29) — a bigger lever on the opening than the drop
 * clock, the spawn table and every speed-curve change put together. A tutorial
 * that *mentions* the control teaches a fact. A tutorial the player cannot
 * finish without it teaches a habit, and the habit is what the number is about.
 *
 * **Decision D21 made the mechanism cheaper rather than weaker.** The drop is a
 * hard drop now, so a scripted drop is one input instead of four or five. That
 * cost the script a beat: drop 1 used to ask for a first press and then for
 * several more, and "do it again" is not a thing that can be asked of a control
 * that finishes the drop the first time. The two beats are one, and the six
 * drops still cannot be reached the end of without using it.
 */
object Tutorial {

    /** How many scripted drops the player makes. */
    const val Drops = 6

    /** SPEC 13: skippable from drop 3, not before. */
    const val SkippableFromDrop = 3

    val Script: List<TutorialLesson> = listOf(
        TutorialLesson(TutorialStep.Steer, drop = 1, await = TutorialAwait.Steered, focus = TutorialFocus.Target),
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
     * **Every partner sits beside the scripted spawn column, and that is
     * load-bearing.**
     * The scripted merge has to happen for a player who only ever drops, because
     * that is the player L49 deliberately produces — with the clock frozen the
     * drop is the only input that makes progress, so most first-timers never
     * steer at all. Drop 1 is the exception and is the beat that teaches them
     * otherwise. A partner the block cannot reach without a drag would let the
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
     *
     * ### Drop 1 is the exception, and it is deliberate
     *
     * It spawns at the far edge and names the cell it has to reach. Everything
     * above still holds *after* that: the cell it names is the one beside the
     * partner, so the merge the ladder starts from is the same merge, made the
     * same way. What changed is that the player has to make it. Spawning in the
     * landing column meant the opening card said "slide it over" to a block that
     * was already over the only cell the drop resolves in, so the first thing the
     * tutorial ever asked for was satisfied by not moving — and a player who
     * learned that on beat one learned the wrong thing about every beat after it.
     */
    fun drop(index: Int, config: EngineConfig): TutorialDrop {
        val floor = config.rows - 1
        fun at(col: Int) = Cell(col, floor)
        return when (index) {
            1 -> TutorialDrop(
                placed = mapOf(at(1) to tier(BlockValue.V2)),
                falling = tier(BlockValue.V2),
                allowedColumns = 2..config.cols - 1,
                spawnColumn = config.cols - 1,
                target = at(2),
            )

            2 -> TutorialDrop(mapOf(at(1) to tier(BlockValue.V4)), tier(BlockValue.V4), 1..2, config.centreColumn)
            3 -> TutorialDrop(mapOf(at(1) to tier(BlockValue.V8)), tier(BlockValue.V8), 1..2, config.centreColumn)
            4 -> TutorialDrop(mapOf(at(1) to tier(BlockValue.V16)), tier(BlockValue.V16), 1..2, config.centreColumn)

            5 -> TutorialDrop(
                placed = mapOf(
                    at(0) to tier(BlockValue.V16),
                    at(1) to tier(BlockValue.V8),
                    at(2) to tier(BlockValue.V4),
                ),
                falling = tier(BlockValue.V4),
                allowedColumns = 2..3,
                spawnColumn = config.centreColumn,
            )

            else -> TutorialDrop(
                placed = mapOf(at(1) to tier(BlockValue.V1024), at(2) to tier(BlockValue.V512)),
                falling = tier(BlockValue.V512),
                allowedColumns = 2..3,
                spawnColumn = config.centreColumn,
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
            falling = FallingBlock(drop.falling, Cell(drop.spawnColumn, 0)),
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
