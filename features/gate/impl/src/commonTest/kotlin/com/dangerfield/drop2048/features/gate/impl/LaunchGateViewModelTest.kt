package com.dangerfield.drop2048.features.gate.impl

import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.config.AppConfigRepository
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.gameconfig.LegalForceReacceptBelow
import com.dangerfield.drop2048.libraries.gameconfig.LegalPrivacyUrl
import com.dangerfield.drop2048.libraries.gameconfig.LegalPrivacyVersion
import com.dangerfield.drop2048.libraries.gameconfig.LegalTermsUrl
import com.dangerfield.drop2048.libraries.gameconfig.LegalTermsVersion
import com.dangerfield.drop2048.libraries.gameconfig.MaintenanceMessage
import com.dangerfield.drop2048.libraries.gameconfig.MaintenanceMode
import com.dangerfield.drop2048.libraries.gameconfig.MinSupportedVersionCode
import com.dangerfield.drop2048.libraries.gameconfig.SoftUpdateVersionCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * The two rules that belong to the ViewModel rather than to `resolveLaunchGates`,
 * and both of them can brick an install if they are wrong.
 *
 * Everything else about the decision is pure and lives in `LaunchGatesTest`.
 */
class LaunchGateViewModelTest : CoroutineTest() {

    @Test
    fun `a first launch records the versions in hand and gates nobody`() = runUnitTest {
        val cache = FakeAppCache(AppData(legalAcceptedAt = 0L))
        val vm = viewModel(cache, config = legalConfig(terms = 4, privacy = 3))
        runCurrent()

        assertNull(vm.state.blocking)
        assertNull(vm.state.notice)
        assertEquals(4, cache.snapshot.acceptedTermsVersion)
        assertEquals(3, cache.snapshot.acceptedPrivacyVersion)
        assertTrue(cache.snapshot.legalAcceptedAt > 0L)
    }

    /**
     * A config that regressed `legal.termsVersion` must not un-accept anything.
     * Without the `maxOf`, a rolled-back console edit would put a re-accept wall
     * in front of somebody who had already agreed to the newer text.
     */
    @Test
    fun `the acceptance record only moves forward`() = runUnitTest {
        val cache = FakeAppCache(
            AppData(acceptedTermsVersion = 7, acceptedPrivacyVersion = 7, legalAcceptedAt = 1L),
        )
        val vm = viewModel(cache, config = legalConfig(terms = 2, privacy = 2))
        runCurrent()

        vm.takeAction(LaunchGateAction.AcceptLegal(termsVersion = 2, privacyVersion = 2))
        runCurrent()

        assertEquals(7, cache.snapshot.acceptedTermsVersion)
        assertEquals(7, cache.snapshot.acceptedPrivacyVersion)
    }

    @Test
    fun `dismissing a soft update banner is remembered at that version`() = runUnitTest {
        val cache = FakeAppCache(AppData(acceptedTermsVersion = 1, acceptedPrivacyVersion = 1, legalAcceptedAt = 1L))
        val vm = viewModel(cache)
        runCurrent()

        vm.takeAction(
            LaunchGateAction.DismissNotice(
                com.dangerfield.drop2048.features.gate.NoticeGate.SoftUpdate(versionCode = 14),
            ),
        )
        runCurrent()

        assertEquals(14, cache.snapshot.softUpdateDismissedFor)
    }

    private fun viewModel(
        cache: FakeAppCache,
        config: Map<String, Any> = emptyMap(),
    ): LaunchGateViewModel {
        val map = FixedConfigMap(config)
        return LaunchGateViewModel(
            appCache = cache,
            appConfigRepository = FakeConfigRepository(map),
            minSupportedVersion = MinSupportedVersionCode(map),
            softUpdateVersion = SoftUpdateVersionCode(map),
            maintenanceMode = MaintenanceMode(map),
            maintenanceMessage = MaintenanceMessage(map),
            termsVersion = LegalTermsVersion(map),
            privacyVersion = LegalPrivacyVersion(map),
            forceReacceptBelow = LegalForceReacceptBelow(map),
            termsUrl = LegalTermsUrl(map),
            privacyUrl = LegalPrivacyUrl(map),
            clock = Clock.System,
        )
    }
}

/**
 * The config tree is nested, not flat: `legal.termsVersion` is a `legal` map
 * holding a `termsVersion`, which is what `getValueForPath` walks. Writing it
 * flat resolves to nothing and every value falls back to its default — which is
 * a green test that proves the defaults work and nothing else.
 */
private fun legalConfig(terms: Int, privacy: Int): Map<String, Any> = mapOf(
    "legal" to mapOf("termsVersion" to terms, "privacyVersion" to privacy),
)

private class FixedConfigMap(override val map: Map<String, Any>) : AppConfigMap()

private class FakeConfigRepository(private val map: AppConfigMap) : AppConfigRepository {
    override fun config(): AppConfigMap = map
    override fun configStream(): Flow<AppConfigMap> = MutableStateFlow(map)
}

private class FakeAppCache(initial: AppData) : AppCache {
    private val stored = MutableStateFlow(initial)
    val snapshot: AppData get() = stored.value
    override val updates: Flow<AppData> = stored
    override suspend fun get(): AppData = stored.value
    override suspend fun set(value: AppData) {
        stored.value = value
    }

    override suspend fun clear() {
        stored.value = AppData()
    }
}
