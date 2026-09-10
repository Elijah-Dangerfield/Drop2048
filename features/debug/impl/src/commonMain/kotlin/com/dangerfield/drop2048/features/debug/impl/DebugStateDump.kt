package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.features.debug.DebugOverrides
import com.dangerfield.drop2048.features.debug.DiagnosticsSettings
import com.dangerfield.drop2048.libraries.drop2048.AppData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * SPEC 19's "dump app state as JSON", assembled from the values that are already
 * serializable.
 *
 * ### It leaves the saved runs out, on purpose
 *
 * `AppData` carries two whole serialized `GameState`s, and a state dump is
 * something a tester pastes into a chat window. Two boards, two RNG states and
 * two transcripts would be tens of kilobytes of noise around the twenty fields
 * anybody reads a dump for, and the pair of them would push the interesting part
 * off the top of the message.
 *
 * They are replaced by their length rather than removed, because "is there a
 * saved run" is a question a dump genuinely gets asked and "how big is it" is
 * the cheapest way to answer it. The seed, which is the part of a run worth
 * carrying, is on the RNG section of the menu next to a copy control.
 */
object DebugStateDump {

    private val pretty = Json { prettyPrint = true; encodeDefaults = true }

    fun of(
        appVersion: String,
        buildType: String,
        debugSession: Boolean,
        overrides: DebugOverrides,
        diagnostics: DiagnosticsSettings,
        data: AppData?,
    ): String {
        val json = buildJsonObject {
            put("appVersion", appVersion)
            put("buildType", buildType)
            put("debugSession", debugSession)
            put("overrides", pretty.encodeToJsonElement(DebugOverrides.serializer(), overrides))
            put(
                "diagnostics",
                pretty.encodeToJsonElement(DiagnosticsSettings.serializer(), diagnostics),
            )
            put("appData", data?.let { redacted(it) } ?: JsonPrimitive(null as String?))
        }
        return pretty.encodeToString(JsonObject.serializer(), json)
    }

    private fun redacted(data: AppData): JsonObject {
        val encoded = pretty.encodeToJsonElement(AppData.serializer(), data) as JsonObject
        return JsonObject(
            encoded.toMutableMap().apply {
                put("savedRun", JsonPrimitive(sizeOf(data.savedRun)))
                put("savedDailyRun", JsonPrimitive(sizeOf(data.savedDailyRun)))
            }
        )
    }

    private fun sizeOf(blob: String?): String =
        if (blob == null) "absent" else "${blob.length} chars"
}
