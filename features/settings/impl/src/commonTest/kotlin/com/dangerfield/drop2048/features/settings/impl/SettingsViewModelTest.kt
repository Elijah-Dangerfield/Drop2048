package com.dangerfield.drop2048.features.settings.impl

import com.dangerfield.drop2048.features.settings.AdConsent
import com.dangerfield.drop2048.features.settings.ProEntitlement
import com.dangerfield.drop2048.features.settings.RestoreOutcome
import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.gameconfig.LegalPrivacyUrl
import com.dangerfield.drop2048.libraries.gameconfig.LegalTermsUrl
import com.dangerfield.drop2048.libraries.ui.system.HapticsSetting
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The settings screen's rules, none of which are "a toggle flips a boolean".
 *
 * The interesting ones are the three with a consequence: a palette choice has to
 * reach the store the theme reads (which is what makes it reach the board), the
 * reset gate has to refuse a wrong word, and the debug gesture has to need
 * exactly seven taps.
 */
class SettingsViewModelTest : CoroutineTest() {

    @Test
    fun `a palette choice reaches the store the theme reads`() = runUnitTest {
        val scenario = scenario()

        scenario.viewModel.takeAction(SettingsAction.SetPalette(BlockPaletteChoice.HighContrast))
        runCurrent()

        assertEquals(BlockPaletteChoice.HighContrast, scenario.store.settings.value.palette)
        assertEquals(BlockPaletteChoice.HighContrast, scenario.viewModel.state.settings.palette)
    }

    @Test
    fun `every accessibility toggle lands in the store`() = runUnitTest {
        val scenario = scenario()
        val vm = scenario.viewModel

        vm.takeAction(SettingsAction.ToggleReduceMotion)
        vm.takeAction(SettingsAction.ToggleLargeNumbers)
        vm.takeAction(SettingsAction.SetHaptics(HapticsSetting.Strong))
        runCurrent()

        val settings = scenario.store.settings.value
        assertTrue(settings.reduceMotion)
        assertTrue(settings.largeNumbers)
        assertEquals(HapticsSetting.Strong, settings.haptics)
    }

    @Test
    fun `a change made anywhere else shows up on the screen`() = runUnitTest {
        val scenario = scenario()

        // The pause overlay, a gate, a reset — anything that writes settings
        // without going through this screen.
        scenario.store.update { it.copy(leftHanded = true) }
        runCurrent()

        assertTrue(scenario.viewModel.state.settings.leftHanded)
    }

    @Test
    fun `reset progress erases nothing until the word matches`() = runUnitTest {
        val scenario = scenario()
        val vm = scenario.viewModel

        vm.takeAction(SettingsAction.ShowDialog(SettingsDialog.ResetProgressWarn))
        runCurrent()
        assertEquals(SettingsDialog.ResetProgressWarn, vm.state.dialog)
        assertEquals(0, scenario.dao.cleared)

        vm.takeAction(SettingsAction.ShowDialog(SettingsDialog.ResetProgressConfirm))
        vm.takeAction(SettingsAction.ConfirmResetProgress(typed = "reset now", required = "RESET"))
        runCurrent()

        assertEquals(0, scenario.dao.cleared)
        // The dialog stays open so the player can try again, rather than closing
        // on a refusal and looking like it worked.
        assertEquals(SettingsDialog.ResetProgressConfirm, vm.state.dialog)
    }

    /**
     * The positive control for the test above (L35): the only thing that changes
     * is the typed word, so a pass here proves the comparison is what refused.
     */
    @Test
    fun `reset progress erases when the word matches`() = runUnitTest {
        val scenario = scenario()
        val vm = scenario.viewModel

        vm.takeAction(SettingsAction.ShowDialog(SettingsDialog.ResetProgressConfirm))
        vm.takeAction(SettingsAction.ConfirmResetProgress(typed = " reset ", required = "RESET"))
        runCurrent()

        assertEquals(1, scenario.dao.cleared)
        assertNull(vm.state.dialog)
    }

    @Test
    fun `seven taps on the version unlocks the debug menu and no fewer`() = runUnitTest {
        val scenario = scenario()
        val vm = scenario.viewModel

        repeat(6) { vm.takeAction(SettingsAction.TapVersion) }
        runCurrent()
        assertFalse(scenario.store.settings.value.debugMenuUnlocked)

        vm.takeAction(SettingsAction.TapVersion)
        runCurrent()
        assertTrue(scenario.store.settings.value.debugMenuUnlocked)
    }

    @Test
    fun `a restore with no store behind it says so rather than going quiet`() = runUnitTest {
        val scenario = scenario()

        scenario.viewModel.takeAction(SettingsAction.RestorePurchases)
        runCurrent()

        assertEquals(RestoreMessage.Unavailable, scenario.viewModel.state.restoreMessage)
    }

    @Test
    fun `replaying the tutorial is an event, not a write`() = runUnitTest {
        val scenario = scenario()
        val events = mutableListOf<SettingsEvent>()
        val collector = launch { scenario.viewModel.eventFlow.toList(events) }

        scenario.viewModel.takeAction(SettingsAction.ReplayTutorial)
        runCurrent()
        collector.cancel()

        assertEquals(listOf<SettingsEvent>(SettingsEvent.ReplayTutorial), events)
    }

    @Test
    fun `the ad consent row is absent while there is no form behind it`() = runUnitTest {
        val scenario = scenario()
        runCurrent()
        assertFalse(scenario.viewModel.state.adConsentAvailable)
    }

    private fun TestScope.scenario(): Scenario {
        val cache = FakeAppCache(AppData())
        val store = FakeSettingsStore()
        val dao = RecordingDao()
        val viewModel = SettingsViewModel(
            settingsStore = store,
            eraser = PlayerDataEraser(setOf(dao), cache),
            entitlement = NeverProEntitlement(),
            adConsent = NoAdConsent(),
            termsUrl = LegalTermsUrl(EmptyConfigMap),
            privacyUrl = LegalPrivacyUrl(EmptyConfigMap),
        )
        runCurrent()
        return Scenario(viewModel, store, cache, dao)
    }

    private class Scenario(
        val viewModel: SettingsViewModel,
        val store: FakeSettingsStore,
        val cache: FakeAppCache,
        val dao: RecordingDao,
    )
}

private class NeverProEntitlement : ProEntitlement {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun restore(): RestoreOutcome = RestoreOutcome.Unavailable
}

private class NoAdConsent : AdConsent {
    override val isAvailable: Boolean = false
    override suspend fun present() = Unit
}

/**
 * An empty config map, so the two URL values resolve to their compiled-in
 * defaults. That is the same path a device with the server unreachable takes.
 */
private object EmptyConfigMap : AppConfigMap() {
    override val map: Map<String, Any> = emptyMap()
}
