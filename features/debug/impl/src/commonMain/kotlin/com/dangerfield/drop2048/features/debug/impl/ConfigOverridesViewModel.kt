package com.dangerfield.drop2048.features.debug.impl

import androidx.lifecycle.viewModelScope
import com.dangerfield.drop2048.libraries.config.ConfigOverride
import com.dangerfield.drop2048.libraries.config.ConfigOverrideRepository
import com.dangerfield.drop2048.libraries.config.ConfiguredValue
import com.dangerfield.drop2048.libraries.config.QaConfigValue
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.flowroutines.SEAViewModel
import com.dangerfield.drop2048.libraries.flowroutines.collectIn
import me.tatarka.inject.annotations.Inject

/**
 * The QA writer `ConfigOverrideRepository` never had.
 *
 * ### Every key, not a curated list
 *
 * The keys this chunk needed were the seven ad ones, but the set is taken from
 * the `QaConfigValue` multibinding rather than named here, and that is the same
 * ruling `Set<ClearableDao>` got: a key added in a later chunk is editable
 * without anybody remembering to come back to this file, and a key that forgot
 * to contribute itself is visibly missing rather than quietly unreachable. The
 * curated version would have been correct for exactly as long as C15 was the
 * most recent chunk.
 *
 * ### Values are written typed, never as text
 *
 * [ConfigOverride] carries `Any`, and the obvious implementation of a screen
 * full of text fields writes every one of them as a `String`. That would walk
 * straight into L48: `getValueRecursive` coerces a boolean through
 * `toBooleanStrictOrNull`, so `"on"`, `"1"` or a trailing space in
 * `ads.enabled` would resolve to null and fall back — and for the *number* keys
 * a string that does not parse resolves to null too, so `ads.interstitial.
 * cooldownSeconds` set to `"3 0"` would silently be 180 again with nothing on
 * screen to say so.
 *
 * So the value's own compiled default decides the type, the input is parsed
 * before anything is persisted, and a number that does not parse is **refused
 * with a message** rather than written. The one thing a QA override must never
 * do is look applied and not be.
 *
 * ### Removing is not writing the default back
 *
 * An override equal to the default still shadows the console for the life of
 * the install, which is why [ConfigOverrideRepository.removeOverride] exists and
 * why every overridden row has its own reset.
 */
@Inject
class ConfigOverridesViewModel(
    private val values: Set<QaConfigValue>,
    private val overrides: ConfigOverrideRepository,
) : SEAViewModel<ConfigOverridesState, ConfigOverridesEvent, ConfigOverridesAction>(
    initialStateArg = ConfigOverridesState(),
) {

    init {
        overrides.getOverridesFlow().collectIn(viewModelScope) {
            takeAction(ConfigOverridesAction.Refresh)
        }
        takeAction(ConfigOverridesAction.Refresh)
    }

    override suspend fun handleAction(action: ConfigOverridesAction) {
        when (action) {
            ConfigOverridesAction.Refresh -> action.refresh()
            ConfigOverridesAction.Back -> sendEvent(ConfigOverridesEvent.NavigateBack)
            is ConfigOverridesAction.Toggle -> action.toggle()
            is ConfigOverridesAction.Submit -> action.submit()
            is ConfigOverridesAction.Reset -> action.reset()
            ConfigOverridesAction.ClearAll -> action.clearAll()
        }
    }

    private suspend fun ConfigOverridesAction.refresh() {
        val overridden = Catching { overrides.getOverrides().map { it.path }.toSet() }
            .logOnFailure { "Could not read the current overrides" }
            .getOrDefault(emptySet())

        val rows = configuredValues()
            .map { value -> value.toRow(overridden = value.path in overridden) }
            .sortedWith(compareBy({ it.group.sortKey() }, { it.path }))

        updateState { it.copy(rows = rows) }
    }

    private suspend fun ConfigOverridesAction.Toggle.toggle() {
        write(path, !current)
    }

    /**
     * Parses against the key's own default type, and says so when it cannot.
     *
     * The refusal is the useful half. A screen that accepted `"3 0"` and then
     * resolved to 180 would be indistinguishable from a gate that ignored the
     * override, and the tester's next hour goes into the ad layer rather than
     * into the typo.
     */
    private suspend fun ConfigOverridesAction.Submit.submit() {
        val value = configuredValues().firstOrNull { it.path == path } ?: return
        val parsed = value.parse(raw)
        if (parsed == null) {
            sendEvent(ConfigOverridesEvent.Message(ConfigOverridesMessage.NotParseable))
            return
        }
        write(path, parsed)
    }

    private suspend fun ConfigOverridesAction.Reset.reset() {
        Catching { overrides.removeOverride(path) }
            .logOnFailure { "Could not remove the override for $path" }
        takeAction(ConfigOverridesAction.Refresh)
    }

    private suspend fun ConfigOverridesAction.clearAll() {
        Catching { overrides.clearAll() }.logOnFailure { "Could not clear the overrides" }
        sendEvent(ConfigOverridesEvent.Message(ConfigOverridesMessage.Cleared))
        takeAction(ConfigOverridesAction.Refresh)
    }

    private suspend fun write(path: String, value: Any) {
        Catching { overrides.addOverride(ConfigOverride(path, value)) }
            .logOnFailure { "Could not persist the override for $path" }
        takeAction(ConfigOverridesAction.Refresh)
    }

    private fun configuredValues(): List<ConfiguredValue<Any>> = values
        .filterIsInstance<ConfiguredValue<Any>>()
        .filter { it.showInQADashboard }
}

/**
 * The ad keys first, then the two that can lock every player out, then the rest
 * alphabetically.
 *
 * A QA screen is a list somebody scrolls under time pressure, and the order is
 * the only affordance it has. `upgrade` and `legal` are second because they are
 * the keys L-numbered as able to brick an install, so they are the ones worth
 * finding by eye rather than by search.
 */
private fun String.sortKey(): String = when (this) {
    "ads" -> "0$this"
    "upgrade", "legal", "feature" -> "1$this"
    else -> "2$this"
}

private fun ConfiguredValue<Any>.toRow(overridden: Boolean): ConfigRow = ConfigRow(
    path = path,
    name = name,
    description = description,
    group = group,
    kind = kind(),
    current = value.toString(),
    defaultValue = default.toString(),
    isOverridden = overridden,
    allowedValues = allowedValues?.map { it.toString() },
)

private fun ConfiguredValue<Any>.kind(): ConfigKind = when (default) {
    is Boolean -> ConfigKind.Flag
    is Int, is Long -> ConfigKind.Number
    else -> ConfigKind.Text
}

/**
 * The compiled default is the type authority, not the text on screen.
 *
 * `ConfiguredValue<T>` erases `T` at runtime, and its `default` is the only
 * instance of it this screen can see — which is enough, and is also the value
 * the resolver will be compared against.
 */
private fun ConfiguredValue<Any>.parse(raw: String): Any? {
    val trimmed = raw.trim()
    return when (val fallback = default) {
        is Boolean -> trimmed.toBooleanStrictOrNull()
        is Int -> trimmed.toIntOrNull()
        is Long -> trimmed.toLongOrNull()
        is Double -> trimmed.toDoubleOrNull()
        is String -> trimmed.takeIf { allowedValues == null || it in allowedValues.orEmpty() }
        else -> trimmed.takeIf { fallback is CharSequence }
    }
}

data class ConfigOverridesState(
    val rows: List<ConfigRow> = emptyList(),
) {
    val overriddenCount: Int get() = rows.count { it.isOverridden }
}

/** One config key, flattened for display. */
data class ConfigRow(
    val path: String,
    val name: String,
    val description: String?,
    val group: String,
    val kind: ConfigKind,

    /** The value the app is resolving right now, override included. */
    val current: String,

    /** What the binary ships with, so "what did I change this from" is on screen. */
    val defaultValue: String,
    val isOverridden: Boolean,
    val allowedValues: List<String>?,
)

enum class ConfigKind {
    Flag,
    Number,
    Text,
}

enum class ConfigOverridesMessage {
    Cleared,
    NotParseable,
}

sealed interface ConfigOverridesEvent {
    data object NavigateBack : ConfigOverridesEvent
    data class Message(val message: ConfigOverridesMessage) : ConfigOverridesEvent
}

sealed interface ConfigOverridesAction {
    data object Refresh : ConfigOverridesAction
    data object Back : ConfigOverridesAction
    data class Toggle(val path: String, val current: Boolean) : ConfigOverridesAction
    data class Submit(val path: String, val raw: String) : ConfigOverridesAction
    data class Reset(val path: String) : ConfigOverridesAction
    data object ClearAll : ConfigOverridesAction
}
