package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.config.FlagConfigValue
import com.dangerfield.drop2048.libraries.config.IntConfigValue
import com.dangerfield.drop2048.libraries.config.QaConfigValue
import com.dangerfield.drop2048.libraries.config.StringConfigValue
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The writer `ConfigOverrideRepository` never had, and the one property that
 * decides whether it is worth having.
 *
 * **Every override is written at the type the key's default has.** L48 is the
 * reason: `getValueRecursive` resolves a boolean through
 * `toBooleanStrictOrNull` and a number through `toDoubleOrNull`, and anything
 * those return null for falls back to the compiled default with nothing on
 * screen to say the override did not take. A screen full of text fields that
 * persisted strings would produce exactly that — an override that looks applied,
 * is not, and sends the tester into the ad layer looking for a gate bug.
 */
class ConfigOverridesViewModelTest : CoroutineTest() {

    @Test
    fun togglingAFlagWritesABooleanAndNotAString() = runUnitTest {
        val scenario = scenario()

        scenario.viewModel.takeAction(
            ConfigOverridesAction.Toggle(path = AdsEnabledPath, current = true)
        )

        val override = scenario.overrides.getOverrides().single { it.path == AdsEnabledPath }
        assertIs<Boolean>(override.value)
        assertEquals(false, override.value)
    }

    @Test
    fun settingANumberWritesAnIntAndNotAString() = runUnitTest {
        val scenario = scenario()

        scenario.viewModel.takeAction(
            ConfigOverridesAction.Submit(path = MinSessionRunsPath, raw = "0")
        )

        val override = scenario.overrides.getOverrides().single { it.path == MinSessionRunsPath }
        assertIs<Int>(override.value)
        assertEquals(0, override.value)
    }

    /**
     * The refusal, which is the half that stops L48 being reachable from here.
     *
     * Persisting `"3 0"` would resolve to null, fall back to 4, and look
     * identical to an override the gate ignored.
     */
    @Test
    fun aNumberThatDoesNotParseIsRefusedRatherThanPersisted() = runUnitTest {
        val scenario = scenario()

        scenario.viewModel.takeAction(
            ConfigOverridesAction.Submit(path = MinSessionRunsPath, raw = "3 0")
        )

        assertTrue(scenario.overrides.getOverrides().isEmpty())
    }

    /**
     * The whole chain, joined at the merged config map: an override written by
     * this screen is what the typed `ConfiguredValue` resolves, which is the
     * number `InterstitialPolicy` compares `runsThisSession` against.
     *
     * The merge itself is `applyOverrides`, which lives in `:libraries:config:impl`
     * and has its own test — a feature impl may not depend on another module's
     * impl, so this reproduces the one line of it that matters (write the value
     * at the dotted path) and asserts on the resolved value rather than on the
     * map.
     */
    @Test
    fun anOverrideWrittenHereIsWhatTheAdKeyResolvesTo() = runUnitTest {
        val scenario = scenario()

        scenario.viewModel.takeAction(
            ConfigOverridesAction.Submit(path = MinSessionRunsPath, raw = "0")
        )

        val merged = scenario.overrides.getOverrides()
            .fold(emptyMap<String, Any?>()) { acc, override ->
                acc.putAtPath(override.path, override.value)
            }

        assertEquals(0, TestMinSessionRuns(TestConfigMap(merged))())
    }

    /**
     * Removing is not writing the default back. An override equal to the
     * default still shadows the console for the life of the install.
     */
    @Test
    fun removingAnOverrideLeavesNothingBehind() = runUnitTest {
        val scenario = scenario()

        scenario.viewModel.takeAction(
            ConfigOverridesAction.Submit(path = MinSessionRunsPath, raw = "0")
        )
        scenario.viewModel.takeAction(ConfigOverridesAction.Reset(MinSessionRunsPath))

        assertTrue(scenario.overrides.getOverrides().isEmpty())
    }

    /** The ad keys first, because those are the ones somebody is looking for. */
    @Test
    fun theAdKeysSortAheadOfEverythingElse() = runUnitTest {
        val scenario = scenario()

        assertEquals("ads", scenario.viewModel.state.rows.first().group)
    }

    @Test
    fun aRowSaysWhatTheValueIsAndWhatItShipsAs() = runUnitTest {
        val scenario = scenario()

        scenario.viewModel.takeAction(
            ConfigOverridesAction.Submit(path = MinSessionRunsPath, raw = "0")
        )

        val row = scenario.viewModel.state.rows.single { it.path == MinSessionRunsPath }
        assertEquals("4", row.defaultValue)
        assertTrue(row.isOverridden)
    }

    private fun scenario(): ConfigScenario {
        val overrides = StubConfigOverrideRepository()
        val map = TestConfigMap(emptyMap<String, Any?>())
        return ConfigScenario(
            overrides = overrides,
            viewModel = ConfigOverridesViewModel(
                values = setOf<QaConfigValue>(
                    TestAdsEnabled(map),
                    TestMinSessionRuns(map),
                    TestMaintenanceMode(map),
                ),
                overrides = overrides,
            ),
        )
    }

    private class ConfigScenario(
        val overrides: StubConfigOverrideRepository,
        val viewModel: ConfigOverridesViewModel,
    )

    private companion object {
        const val AdsEnabledPath = "ads.enabled"
        const val MinSessionRunsPath = "ads.interstitial.minSessionRuns"
    }
}

/**
 * Real config paths on stand-in values, so the test names the keys this chunk is
 * about without dragging `:libraries:gameconfig` into a feature's test
 * classpath for three constants.
 */
private class TestAdsEnabled(map: AppConfigMap) : FlagConfigValue(map) {
    override val name = "Ads enabled"
    override val path = "ads.enabled"
    override val default = true
}

private class TestMinSessionRuns(map: AppConfigMap) : IntConfigValue(map) {
    override val name = "Interstitial: earliest run of a session"
    override val path = "ads.interstitial.minSessionRuns"
    override val default = 4
}

private class TestMaintenanceMode(map: AppConfigMap) : StringConfigValue(map) {
    override val name = "Maintenance mode"
    override val path = "upgrade.maintenanceMode"
    override val default = "off"
    override val allowedValues = listOf("off", "banner", "blocking")
}

private class TestConfigMap(override val map: Map<String, *>) : AppConfigMap()

/** Writes [value] at a dotted [path], the way `applyOverrides` does. */
private fun Map<String, Any?>.putAtPath(path: String, value: Any): Map<String, Any?> {
    val segments = path.split('.')
    val head = segments.first()
    if (segments.size == 1) return this + (head to value)
    @Suppress("UNCHECKED_CAST")
    val child = (this[head] as? Map<String, Any?>) ?: emptyMap()
    return this + (head to child.putAtPath(segments.drop(1).joinToString("."), value))
}
