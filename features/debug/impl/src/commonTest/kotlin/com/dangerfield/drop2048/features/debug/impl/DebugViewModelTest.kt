package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.libraries.billing.InMemoryProGrant
import com.dangerfield.drop2048.features.debug.DebugMenuGate
import com.dangerfield.drop2048.features.debug.PresetBoard
import com.dangerfield.drop2048.libraries.cascade.BlockValue
import com.dangerfield.drop2048.libraries.cascade.autoplay.Policy
import com.dangerfield.drop2048.libraries.cascade.blockOf
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ClearableDao
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DebugViewModelTest : CoroutineTest() {

    /**
     * The session latches on **open**, not on use.
     *
     * If it latched on the first control instead, a tester who opened the menu to
     * read the seed off it and then went back to play would produce a
     * `run_record` for a run they had just been reading the seed of. Opening is
     * the only moment that is unambiguous.
     */
    @Test
    fun openingTheMenuMarksTheSession() = runUnitTest {
        val scenario = scenario()

        assertTrue(scenario.controller.isDebugSession.value)
    }

    @Test
    fun aReleaseBuildDrawsNothingUntilThePassphraseIsRight() = runUnitTest {
        val gate = FakeGate(open = false)
        val scenario = scenario(gate = gate)

        scenario.viewModel.takeAction(DebugAction.SubmitPassphrase("nope"))
        assertTrue(scenario.viewModel.state.needsPassphrase)
        assertTrue(scenario.viewModel.state.passphraseRejected)

        scenario.viewModel.takeAction(DebugAction.SubmitPassphrase(FakeGate.Passphrase))
        assertFalse(scenario.viewModel.state.needsPassphrase)
    }

    @Test
    fun clearingTheOverridesLeavesTheDiagnosticsSwitchesAlone() = runUnitTest {
        val scenario = scenario()

        scenario.viewModel.takeAction(DebugAction.SetPreset(PresetBoard.StoneHeavy))
        scenario.viewModel.takeAction(DebugAction.ToggleTranscript)
        assertEquals(PresetBoard.StoneHeavy, scenario.viewModel.state.overrides.preset)
        assertTrue(scenario.viewModel.state.diagnostics.showTranscript)

        scenario.viewModel.takeAction(DebugAction.ClearOverrides)

        assertEquals(null, scenario.viewModel.state.overrides.preset)
        assertTrue(
            scenario.viewModel.state.diagnostics.showTranscript,
            "clearing a board took the tester's overlay away",
        )
    }

    /**
     * The queue is consumed, not re-read. A `StateFlow` that could hand the same
     * head out twice would repeat a block on every recomposition.
     */
    @Test
    fun theForcedQueueIsConsumedOnceEach() = runUnitTest {
        val scenario = scenario()
        scenario.viewModel.takeAction(DebugAction.QueueBlock(blockOf(BlockValue.V2)))
        scenario.viewModel.takeAction(DebugAction.QueueBlock(blockOf(BlockValue.V4)))

        assertEquals(blockOf(BlockValue.V2), scenario.controller.takeForcedBlock())
        assertEquals(blockOf(BlockValue.V4), scenario.controller.takeForcedBlock())
        assertEquals(null, scenario.controller.takeForcedBlock())
    }

    /**
     * The soak plays the real engine with `tools/balance`'s real policies, which
     * is the point: a second scripted player would report a level that looks like
     * the harness's and is not comparable to it.
     */
    @Test
    fun theSoakPlaysARunToItsEndAndReportsIt() = runUnitTest {
        val scenario = scenario()
        scenario.viewModel.takeAction(DebugAction.SetSeed(1234))

        scenario.viewModel.takeAction(DebugAction.RunSoak(Policy.Greedy))

        val soak = requireNotNull(scenario.viewModel.state.soak) { "the soak reported nothing" }
        assertEquals("greedy", soak.policy)
        assertEquals(1234, soak.seed)
        assertTrue(soak.drops > 0, "the soak played no drops")
        assertFalse(soak.hitDropCap, "the soak never died, which means the engine is wrong")
        assertTrue(soak.faults.isEmpty(), "the engine reported ${soak.faults}")
    }

    /**
     * A soak run is a soak run: it never touches the same seam a played run does,
     * because it never leaves the ViewModel. Worth pinning because "autoplay"
     * would be an obvious thing to implement by driving the real game loop.
     */
    @Test
    fun theSoakLeavesTheDatabaseAlone() = runUnitTest {
        val scenario = scenario()

        scenario.viewModel.takeAction(DebugAction.RunSoak(Policy.Random))

        assertEquals(0, scenario.dao.clears)
    }

    /**
     * The bug this test exists for was found on a device, not here: "Start run"
     * navigated to a game screen that resumes a saved run, so a tester who had a
     * run in progress got that board back and the preset they had just chosen
     * never applied. Nothing looked broken — the wrong board simply appeared.
     */
    @Test
    fun startingAForcedRunThrowsAwayTheEndlessSaveFirst() = runUnitTest {
        val cache = StubAppCache(AppData(savedRun = "an endless run in progress"))
        val scenario = scenario(cache = cache)

        scenario.viewModel.takeAction(DebugAction.StartForcedRun)

        assertEquals(null, cache.value.savedRun)
    }

    /**
     * And it leaves the Daily slot alone. A Daily attempt is spent and cannot be
     * given back (SPEC 14), so deleting one to make room for a debug board would
     * cost the tester's device a day it can never replay.
     */
    @Test
    fun startingAForcedRunLeavesTheDailySaveAlone() = runUnitTest {
        val cache = StubAppCache(AppData(savedDailyRun = "today's attempt"))
        val scenario = scenario(cache = cache)

        scenario.viewModel.takeAction(DebugAction.StartForcedRun)

        assertEquals("today's attempt", cache.value.savedDailyRun)
    }

    @Test
    fun resettingLocalDataClearsEveryTable() = runUnitTest {
        val scenario = scenario()

        scenario.viewModel.takeAction(DebugAction.ResetAllLocalData)

        assertEquals(1, scenario.dao.clears)
    }

    private fun scenario(
        gate: DebugMenuGate = FakeGate(open = true),
        cache: StubAppCache = StubAppCache(),
    ): DebugScenario {
        val controller = InMemoryDebugController()
        val dao = CountingClearableDao()
        return DebugScenario(
            controller = controller,
            dao = dao,
            viewModel = DebugViewModel(
                controller = controller,
                diagnostics = InMemoryDiagnostics(),
                gate = gate,
                proGrant = InMemoryProGrant(),
                daily = StubDailyRepository(),
                appCache = cache,
                clearableDaos = setOf(dao),
                dispatchers = dispatchers,
            ),
        )
    }

    private class DebugScenario(
        val controller: InMemoryDebugController,
        val dao: CountingClearableDao,
        val viewModel: DebugViewModel,
    )

    private class FakeGate(open: Boolean) : DebugMenuGate {
        override val isOpenBuild: Boolean = open
        private val unlocked = MutableStateFlow(open)
        override val isUnlocked: StateFlow<Boolean> = unlocked
        override fun unlock(passphrase: String): Boolean {
            val correct = passphrase == Passphrase
            if (correct) unlocked.value = true
            return correct
        }

        companion object {
            const val Passphrase = "open sesame"
        }
    }

    private class CountingClearableDao : ClearableDao {
        var clears = 0
            private set

        override suspend fun deleteAll() {
            clears += 1
        }
    }
}
