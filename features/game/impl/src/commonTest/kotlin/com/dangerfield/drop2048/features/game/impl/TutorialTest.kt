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

    @Test
    fun firstDrop_teachesSteeringThenTheNudge() = runUnitTest {
        playing(teach = true, pressPlay = false) {
            act(GameAction.MoveLeft)
            assertEquals(TutorialStep.FirstNudge, state.tutorial?.step)
            assertEquals(TutorialFocus.Nudge, state.tutorial?.focus)

            act(GameAction.Nudge)
            assertEquals(TutorialStep.KeepNudging, state.tutorial?.step)
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
            assertEquals(BlockValue.V32.points, state.biggestTier, "which ends in a 32")
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
