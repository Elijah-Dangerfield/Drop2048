package com.dangerfield.drop2048.features.settings.impl

import com.dangerfield.drop2048.libraries.ads.AdConsent
import com.dangerfield.drop2048.libraries.billing.Entitlements
import com.dangerfield.drop2048.libraries.billing.PaywallCoordinator
import com.dangerfield.drop2048.libraries.billing.PaywallRequest
import com.dangerfield.drop2048.libraries.billing.PaywallTrigger
import com.dangerfield.drop2048.libraries.billing.PurchaseOutcome
import com.dangerfield.drop2048.libraries.billing.RestoreOutcome
import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.gameconfig.LegalPrivacyUrl
import com.dangerfield.drop2048.libraries.gameconfig.LegalTermsUrl
import com.dangerfield.drop2048.libraries.ui.system.HapticsSetting
import com.dangerfield.drop2048.libraries.ui.system.color.BlockPaletteChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

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

        vm.takeAction(SettingsAction.ToggleLargeNumbers)
        vm.takeAction(SettingsAction.SetHaptics(HapticsSetting.Strong))
        runCurrent()

        val settings = scenario.store.settings.value
        assertTrue(settings.largeNumbers)
        assertEquals(HapticsSetting.Strong, settings.haptics)
    }

    @Test
    fun `a change made anywhere else shows up on the screen`() = runUnitTest {
        val scenario = scenario()

        // The pause overlay, a gate, a reset — anything that writes settings
        // without going through this screen.
        scenario.store.update { it.copy(ghostEnabled = false) }
        runCurrent()

        assertFalse(scenario.viewModel.state.settings.ghostEnabled)
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
    fun `replaying the tutorial is an event rather than a write`() = runUnitTest {
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

    /**
     * The row exists so a player can quote the id to
     * `nightjarlabs.llc/delete-data`, which is the only route to records that
     * already left the device. `PlayerDataEraser` cannot reach those.
     */
    @Test
    fun `the install id is shown once the cache has one`() = runUnitTest {
        val scenario = scenario(AppData(installId = "abc-123"))

        assertEquals("abc-123", scenario.viewModel.state.installId)
    }

    /** Null keeps the row off the screen rather than drawing a blank one. */
    @Test
    fun `the install id is absent before the cache hydrates`() = runUnitTest {
        val scenario = scenario(AppData(installId = null))

        assertNull(scenario.viewModel.state.installId)
    }

    @Test
    fun `copying the install id emits it and says so for a beat`() = runUnitTest {
        val scenario = scenario(AppData(installId = "abc-123"))

        val events = mutableListOf<SettingsEvent>()
        val collector = launch { scenario.viewModel.eventFlow.toList(events) }

        scenario.viewModel.takeAction(SettingsAction.CopyInstallId)
        runCurrent()

        assertEquals(listOf<SettingsEvent>(SettingsEvent.CopyToClipboard("abc-123")), events)
        assertTrue(scenario.viewModel.state.installIdCopied)

        advanceTimeBy(CopiedMillis + 1)
        runCurrent()
        assertFalse(scenario.viewModel.state.installIdCopied)
        collector.cancel()
    }

    /**
     * The one that makes collecting the cache worth it rather than reading it
     * once: "Delete local data" mints a fresh id, and a row still showing the
     * old one would send the player to the form with a key that no longer
     * matches anything they could still ask us to delete.
     */
    @Test
    fun `the install id follows the one delete local data issues`() = runUnitTest {
        val scenario = scenario(AppData(installId = "old-id"))
        assertEquals("old-id", scenario.viewModel.state.installId)

        scenario.cache.set(AppData(installId = "new-id"))
        runCurrent()

        assertEquals("new-id", scenario.viewModel.state.installId)
    }

    private fun TestScope.scenario(data: AppData = AppData()): Scenario {
        val cache = FakeAppCache(data)
        val store = FakeSettingsStore()
        val dao = RecordingDao()
        val viewModel = SettingsViewModel(
            settingsStore = store,
            eraser = PlayerDataEraser(setOf(dao), cache),
            entitlement = NeverProEntitlement(),
            adConsent = NoAdConsent(),
            paywall = RecordingPaywall(),
            termsUrl = LegalTermsUrl(EmptyConfigMap),
            privacyUrl = LegalPrivacyUrl(EmptyConfigMap),
            appCache = cache,
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

private class NeverProEntitlement : Entitlements {
    override val isPro: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun purchasePro(trigger: String?): PurchaseOutcome = PurchaseOutcome.Unavailable
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

/**
 * SPEC 12's Settings entry goes through the coordinator rather than to a route,
 * so what a test can see is the trigger it asked with.
 */
private class RecordingPaywall : PaywallCoordinator {
    val offers = mutableListOf<PaywallTrigger>()

    override val requests: Flow<PaywallRequest> = emptyFlow()

    override fun requestOffer(trigger: PaywallTrigger): Boolean {
        offers += trigger
        return true
    }

    override fun mayOffer(trigger: PaywallTrigger): Boolean = true

    override suspend fun claimStackedOutCard(): Boolean = false
}
