package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.features.game.impl.GameScenario.Companion.playing
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.Cell
import com.dangerfield.drop2048.libraries.cascade.NumberBlock
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.ui.system.Cue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * The whole of SPEC 13, driven the way a player drives it.
 *
 * This is the test the chunk exists for. The tutorial is a list of forced
 * `GameState`s and expected inputs (SPEC 4.1, SPEC 13), so the entire curriculum
 * — six drops, a three-step cascade and a 2048 burst — runs in a ViewModel test
 * in milliseconds, with no renderer and no device.
 *
 * **Every drop here is landed by pressing ▼ and by nothing else**, which is not
 * an artefact of the harness. The tutorial's drop clock is frozen (SPEC 13), so
 * gravity never moves a block and there is no other way down. That is the point
 * of the chunk: L29 measured the nudge at 223 seconds against 56 to reach level
 * 4, and a player who finishes this has pressed it on all six drops.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TutorialTest : CoroutineTest() {

    @Test
    fun firstLaunch_opensStraightIntoTheScriptedRun() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            assertPhase(GamePhase.Playing)
            assertEquals(TutorialStep.Steer, state.tutorial?.step)
            assertEquals(NumberBlock(BlockValue.V2), state.falling?.block)
            assertEquals(NumberBlock(BlockValue.V2), state.board[Cell(1, 7)])
        }
    }

    @Test
    fun frozenClock_meansGravityNeverMovesTheBlock() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            val before = state.falling?.cell
            advance(FrozenProbeMillis)
            assertEquals(before, state.falling?.cell, "the tutorial clock is frozen")
        }
    }

    /**
     * Decision D21 collapsed drop 1's two ▼ beats into one, because there is no
     * "press it again" left to ask for: the first press finishes the drop. The
     * steer beat still hands over to the drop beat, and the drop beat is the one
     * that lights the control.
     */
    @Test
    fun firstDrop_teachesSteeringThenTheDrop() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            act(GameAction.MoveLeft)
            assertEquals(TutorialStep.FirstDrop, state.tutorial?.step)
            assertEquals(TutorialFocus.Drop, state.tutorial?.focus)

            land()
            waitOutResolution()
            assertEquals(TutorialStep.FirstMerge, state.tutorial?.step)
        }
    }

    /**
     * **Every scripted merge happens for a player who never steers.**
     *
     * This is the property the whole script rests on and it had no test. With the
     * clock frozen, ▼ is the only input that makes progress (L49), so the player
     * the tutorial is designed to produce is one who reaches for ▼ and never
     * drags at all — and every partner therefore sits beside the spawn column so
     * that dropping straight down still merges.
     *
     * If it did not, the merge would be missed and the *next* drop's board would
     * arrive already holding the tier it was supposed to make. Nothing would look
     * broken and nothing would be: the board would just have corrected itself
     * behind the player, which is what makes a scripted run read as arbitrary.
     *
     * The ladder is asserted at each rung rather than at the end, so a board that
     * is moved fails on the drop that moved it.
     */
    @Test
    fun everyScriptedMerge_landsWithoutASingleSteer() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            val ladder = Cell(1, 7)

            playDrop(TutorialStep.Steer)
            assertEquals(NumberBlock(BlockValue.V4), state.board[ladder])
            act(GameAction.TutorialAdvance)

            playDrop(TutorialStep.SecondDrop)
            assertEquals(NumberBlock(BlockValue.V8), state.board[ladder])

            playDrop(TutorialStep.ThirdDrop)
            assertEquals(NumberBlock(BlockValue.V16), state.board[ladder])

            playDrop(TutorialStep.FourthDrop)
            assertEquals(
                BlockValue.V32.points,
                state.biggestTier,
                "the fourth drop doubles the ladder rather than merging a tile nobody placed",
            )
        }
    }

    @Test
    fun steering_isClampedToTheScriptedColumns() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            act(GameAction.SteerTo(4))
            assertEquals(2, state.falling?.cell?.col, "drop one allows columns 1..2")

            act(GameAction.SteerTo(0))
            assertEquals(1, state.falling?.cell?.col)
        }
    }

    @Test
    fun sixDrops_endWithA2048Burst() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            playDrop(TutorialStep.Steer)
            assertEquals(TutorialStep.FirstMerge, state.tutorial?.step)
            assertEquals(NumberBlock(BlockValue.V4), state.board[Cell(1, 7)])
            act(GameAction.TutorialAdvance)

            playDrop(TutorialStep.SecondDrop)
            assertEquals(NumberBlock(BlockValue.V8), state.board[Cell(1, 7)])

            playDrop(TutorialStep.ThirdDrop)
            assertEquals(NumberBlock(BlockValue.V16), state.board[Cell(1, 7)])

            playDrop(TutorialStep.FourthDrop)
            assertEquals(TutorialStep.WatchThis, state.tutorial?.step)
            act(GameAction.TutorialAdvance)

            cues.clear()
            playDrop(TutorialStep.CascadeDrop)
            assertEquals(CascadeDepth - 1, cues.maxOf { it.pitchSteps }, "a three-step cascade")
            assertEquals(TutorialStep.BurstIntro, state.tutorial?.step)
            act(GameAction.TutorialAdvance)

            cues.clear()
            playDrop(TutorialStep.BurstDrop)

            assertTrue(cues.contains(Cue.Burst), "the sixth drop bursts a row")
            assertEquals(BlockValue.V2048.points, state.biggestTier, "a 2048 was reached")
            assertTrue(state.board.isClear, "2048 bursts its own row")
            assertEquals(TutorialStep.Handoff, state.tutorial?.step)
        }
    }

    @Test
    fun theHandoff_startsARealRunOnTheClock() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            playWholeScript()
            assertEquals(TutorialStep.Handoff, state.tutorial?.step)

            act(GameAction.TutorialAdvance)

            assertNull(state.tutorial, "the guided run is over")
            assertPhase(GamePhase.Playing)
            assertEquals(1, state.level)
            assertEquals(0L, state.score)
            assertTrue(cache.snapshot.hasUserOnboarded, "completing it flips the persisted flag")

            val before = state.falling?.cell
            tick()
            assertTrue(state.falling?.cell != before || landedIn(state.board.rows - 1), "the clock is running")
        }
    }

    /**
     * The tutorial's worst bug, and it shipped in C5: a player who keeps tapping
     * ▼ while a card is up bricks the guided run permanently.
     *
     * Reproduced on an emulator before it was fixed and reproduced here after.
     * The mechanism is that the board a card introduces is installed *with* the
     * card, and the scrim passes touches through, so ▼ landed drop five before
     * "Watch this one" was acknowledged. The beat after it then waited on a
     * landing that had already happened, on a frozen clock, with no coach mark
     * left to offer the skip. The app was unplayable from first launch and the
     * only way out was clearing app data.
     *
     * The three assertions are the three things that were false: the card is
     * still up, the block is still there to be dropped, and the run keeps going
     * once the card is answered.
     */
    @Test
    fun pressingDropUnderACard_doesNotStrandTheScript() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            playDrop(TutorialStep.Steer)
            act(GameAction.TutorialAdvance)
            playDrop(TutorialStep.SecondDrop)
            playDrop(TutorialStep.ThirdDrop)
            playDrop(TutorialStep.FourthDrop)

            assertEquals(TutorialStep.WatchThis, state.tutorial?.step)
            val board = state.board
            val falling = state.falling

            repeat(TrampleDrops) { land() }
            waitOutResolution()

            assertEquals(TutorialStep.WatchThis, state.tutorial?.step, "the card is still asking")
            assertEquals(board, state.board, "and nothing moved under it")
            assertEquals(falling, state.falling, "including the block the next beat needs")

            act(GameAction.TutorialAdvance)
            playDrop(TutorialStep.CascadeDrop)
            assertEquals(TutorialStep.BurstIntro, state.tutorial?.step, "the script kept going")
        }
    }

    /**
     * The same guard on the other two inputs. A card that stops ▼ and lets a drag
     * through is the same deadlock one gesture later.
     */
    @Test
    fun steeringUnderACard_isIgnoredToo() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            playDrop(TutorialStep.Steer)

            assertEquals(TutorialStep.FirstMerge, state.tutorial?.step)
            val board = state.board

            act(GameAction.MoveLeft, GameAction.SteerTo(4), GameAction.LockNow)

            assertEquals(board, state.board)
            assertEquals(TutorialStep.FirstMerge, state.tutorial?.step)
        }
    }

    @Test
    fun theTutorial_isNotOfferedTwice() = runUnitTest {
        playing(teach = false) {
            assertNull(state.tutorial)
        }
    }

    @Test
    fun skip_isOfferedFromDropThreeAndNotBefore() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            assertFalse(state.tutorial?.canSkip == true, "drop one cannot be skipped")

            playDrop(TutorialStep.Steer)
            act(GameAction.TutorialAdvance)
            assertFalse(state.tutorial?.canSkip == true, "drop two cannot be skipped")

            playDrop(TutorialStep.SecondDrop)
            assertEquals(TutorialStep.ThirdDrop, state.tutorial?.step)
            assertTrue(state.tutorial?.canSkip == true, "drop three can")
        }
    }

    @Test
    fun skip_endsTheScriptAndFlipsTheFlag() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            playDrop(TutorialStep.Steer)
            act(GameAction.TutorialAdvance)
            playDrop(TutorialStep.SecondDrop)

            act(GameAction.TutorialSkip)

            assertNull(state.tutorial)
            assertPhase(GamePhase.Playing)
            assertTrue(cache.snapshot.hasUserOnboarded)
            assertEquals(0L, state.score, "a skipped tutorial does not carry its score into the run")
        }
    }

    /**
     * A skipped tutorial must not be resumable as a run, which is the failure the
     * saved-run store would otherwise produce: a scripted board with a 1024 on it
     * restored on next launch as if the player had earned it.
     */
    @Test
    fun aScriptedRun_isNeverWrittenToDisk() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            playDrop(TutorialStep.Steer)
            assertNull(savedRun(), "nothing scripted reaches the saved-run store")

            act(GameAction.TutorialAdvance)
            playDrop(TutorialStep.SecondDrop)
            act(GameAction.TutorialSkip)

            assertNotNull(savedRun(), "the real run that follows it does")
        }
    }

    /**
     * The scripted run scores about 11,600 points and is deliberately never
     * recorded, so the number the header shows as "best" has to come from
     * `run_record` and not from the script — during the tutorial as well as after
     * it.
     *
     * C3c fixed the second half of that and C3a fixed the first. `best` was
     * `maxOf(best, score)` on every publish (L44), so a brand-new player watched
     * the header count their scripted 11,598 up as a record they had never set,
     * and it corrected itself to zero the moment the script handed over. Both
     * assertions here are the same bug, one before the handoff and one after.
     */
    @Test
    fun theScriptedScore_isNeverThePlayersBest() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            playWholeScript()
            assertTrue(state.score > 0, "the script really does run the number up")
            assertEquals(0L, state.best, "and none of it reaches the header's best")

            act(GameAction.TutorialAdvance)

            assertEquals(0L, state.best)
        }
    }

    @Test
    fun aScriptedRun_isNeverRecorded() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            playWholeScript()
            act(GameAction.TutorialAdvance)

            assertTrue(recordedRuns().isEmpty(), "the tutorial is not a run")
        }
    }
}

/**
 * Land the block the way the tutorial forces a player to: ▼, and only ▼.
 *
 * [expected] is asserted before the drop rather than after, so a script that
 * reorders itself fails on the step that moved instead of three assertions later.
 */
private const val TrampleDrops = 12

private fun GameScenario.playDrop(expected: TutorialStep) {
    assertEquals(expected, state.tutorial?.step, "the beat the board is waiting on")
    land()
    waitOutResolution()
}

/** Every scripted drop, up to but not including the handoff's own button. */
private fun GameScenario.playWholeScript() {
    playDrop(TutorialStep.Steer)
    act(GameAction.TutorialAdvance)
    playDrop(TutorialStep.SecondDrop)
    playDrop(TutorialStep.ThirdDrop)
    playDrop(TutorialStep.FourthDrop)
    act(GameAction.TutorialAdvance)
    playDrop(TutorialStep.CascadeDrop)
    act(GameAction.TutorialAdvance)
    playDrop(TutorialStep.BurstDrop)
}

/**
 * SPEC 13's drop 5 is "pre-seeded so a single placement triggers a 3-step
 * cascade", and the merge cue's pitch is what counts them: it climbs a semitone
 * per cascade step (SPEC 9), so the deepest pitch in the run is the depth minus
 * one. Asserting the board afterwards cannot work — the next beat's set piece is
 * already on it by then.
 */
private const val CascadeDepth = 3

/** Long enough that a running drop clock would have moved the block several rows. */
private const val FrozenProbeMillis = 5_000L
