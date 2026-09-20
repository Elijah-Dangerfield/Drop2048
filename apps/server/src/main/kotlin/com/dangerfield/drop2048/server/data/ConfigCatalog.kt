package com.dangerfield.drop2048.server.data

import com.dangerfield.drop2048.server.domain.ManifestEntry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The keys `docs/SPEC.md` 10 makes remote, with the defaults the client compiles
 * in, used **only when no build has uploaded a manifest yet**.
 *
 * The durable source of this list is the app's own `ConfiguredValue` registry,
 * uploaded per release to `PUT /v1/admin/config/manifest`. Until that upload is
 * wired into CI there is no manifest at all on a fresh deploy, and an admin
 * console with no manifest shows an empty flag table: every value is editable in
 * principle and none is discoverable in practice. This is what it falls back to,
 * so an operator opening the console sees the fifteen gameplay knobs and the ten
 * ad / Pro / kill-switch ones, each with the number the binary ships with.
 *
 * It also gives [ConfigSchema] something to type-check against, which is what
 * turns `board.rows = "eight"` into a 400 at write time instead of a value the
 * client quietly ignores.
 *
 * An uploaded manifest wins outright. That is deliberate: once a real build has
 * declared what it shipped with, a hand-maintained list must not be allowed to
 * contradict it.
 *
 * **Not here on purpose:** the merge priority order, the resolution algorithm,
 * the burst rule and the scoring formulas. SPEC 10 puts them off the wire
 * because changing them mid-flight silently invalidates every high score on the
 * board, and a path listed here is a path the console invites someone to set.
 */
object ConfigCatalog {

    private val SPEED_CURVE = listOf(
        500,
        470, 440, 410,
        380, 350, 325, 300,
        270, 245, 220, 200,
        185, 170, 158, 148,
        140, 133, 127, 122,
        118,
    )

    val entries: List<ManifestEntry> = listOf(
        int(
            path = "board.rows",
            default = 8,
            description = "Board height. Settles SPEC 3's 7-vs-8 question with live data. Applies to the next run.",
        ),
        int(
            path = "level.blocksPerLevel",
            default = 20,
            description = "MOVES THE DETERMINISM DIGEST. Changing this invalidates every replayable run and " +
                "every seed-attached bug report recorded under the old value (D9). Reach for the speed curve first.",
        ),
        int(
            path = "spawn.cap.divisor",
            default = 16,
            description = "SPEC 5.3 constraint B: a spawn may not exceed highestOnBoard / this. Lower is safer.",
        ),
        json(
            path = "spawn.table",
            default = spawnTableDefault(),
            description = "SPEC 5.3's level bands. Every band's weights must sum to 100 or the whole table is " +
                "ignored and the client keeps its own. Sets the tier ceiling, not the difficulty (L19).",
        ),
        json(
            path = "speed.curve",
            default = buildJsonArray { SPEED_CURVE.forEach { add(JsonPrimitive(it)) } },
            description = "Milliseconds per row, one entry per level from 1. A pacing dial with no measured effect " +
                "on outcomes (L28) — the safest key on this list.",
        ),
        int(
            path = "speed.floorMs",
            default = 90,
            description = "The fastest a row can ever fall. Load-bearing: without a floor the game stops being a " +
                "puzzle and becomes a reflex test (SPEC 5.5).",
        ),
        int(
            path = "speed.tailStepMs",
            default = 2,
            description = "Milliseconds shaved per level past the end of the curve, down to the floor.",
        ),
        int("special.wildcard.perMille", 30, "Wildcard spawn rate in per-mille. 30 = 3% (SPEC 5.2)."),
        int("special.wildcard.firstLevel", 5, "Level Wildcards start appearing at (SPEC 5.2)."),
        int("special.bomb.perMille", 30, "Bomb spawn rate in per-mille. 30 = 3% (SPEC 5.2)."),
        int("special.bomb.firstLevel", 8, "Level Bombs start appearing at (SPEC 5.2)."),
        int("special.stone.perMille", 50, "Stone spawn rate in per-mille. 50 = 5% (SPEC 5.2)."),
        int("special.stone.firstLevel", 12, "Level Stones start appearing at (SPEC 5.2)."),

        flag("ads.enabled", true, "Master kill switch for all advertising, rewarded included."),
        int("ads.interstitial.minSessionRuns", 4, "No interstitial before this run of a session (SPEC 12: 4)."),
        int("ads.interstitial.cooldownSeconds", 180, "Minimum gap since the last interstitial (SPEC 12: 180)."),
        int(
            path = "ads.interstitial.rewardedGapSeconds",
            default = 45,
            description = "Never within this many seconds of a rewarded ad, either direction (SPEC 12: 45).",
        ),
        int(
            path = "ads.interstitial.suppressDaysSinceInstall",
            default = 3,
            description = "Interstitials suppressed entirely for this many days after install (SPEC 12: 3).",
        ),
        flag(
            path = "ads.banner.enabled",
            default = true,
            description = "The banner below the board, drawn only where the arrow row would be (D28). " +
                "Off gives the strip back to the board, which is also what a no-fill does.",
        ),
        int("ads.rewarded.continuesPerRun", 2, "Hard cap on rewarded continues in one run (SPEC 12: 2)."),
        flag("pro.upsell.enabled", true, "The Settings entry and the once-per-session stacked-out card (SPEC 12)."),
        flag("feature.leaderboards", true, "Kill switch for the platform boards. Off hides the entry point."),
    )

    private val byPath = entries.associateBy { it.path }

    fun entry(path: String): ManifestEntry? = byPath[path]

    private fun spawnTableDefault(): JsonElement = buildJsonObject {
        put(
            "bands",
            buildJsonArray {
                add(band(1, "V2" to 65, "V4" to 35))
                add(band(4, "V2" to 35, "V4" to 45, "V8" to 20))
                add(band(7, "V2" to 10, "V4" to 45, "V8" to 35, "V16" to 10))
                add(band(10, "V4" to 30, "V8" to 40, "V16" to 25, "V32" to 5))
                add(band(13, "V4" to 15, "V8" to 35, "V16" to 35, "V32" to 15))
                add(band(16, "V8" to 25, "V16" to 35, "V32" to 30, "V64" to 10))
                add(band(19, "V8" to 15, "V16" to 30, "V32" to 35, "V64" to 20))
            },
        )
    }

    private fun band(fromLevel: Int, vararg weights: Pair<String, Int>): JsonElement = buildJsonObject {
        put("fromLevel", fromLevel)
        put(
            "weights",
            buildJsonArray {
                weights.forEach { (value, percent) ->
                    add(
                        buildJsonObject {
                            put("value", value)
                            put("percent", percent)
                        }
                    )
                }
            },
        )
    }

    private fun int(path: String, default: Int, description: String) =
        ManifestEntry(path, "int", JsonPrimitive(default), description, allowedValues = null)

    private fun flag(path: String, default: Boolean, description: String) =
        ManifestEntry(path, "boolean", JsonPrimitive(default), description, allowedValues = null)

    private fun string(path: String, default: String, description: String) =
        ManifestEntry(path, "string", JsonPrimitive(default), description, allowedValues = null)

    private fun json(path: String, default: JsonElement, description: String) =
        ManifestEntry(path, "json", default, description, allowedValues = null)
}

/**
 * The uploaded manifest when there is one, [ConfigCatalog] when there is not.
 *
 * `ifEmpty` rather than a merge: once a build has declared what it shipped with,
 * a hand-maintained list must not be allowed to contradict it, and "what did
 * v1.0.1 ship with" has to stay an honest question.
 */
fun List<ManifestEntry>.orCatalog(): List<ManifestEntry> = ifEmpty { ConfigCatalog.entries }
