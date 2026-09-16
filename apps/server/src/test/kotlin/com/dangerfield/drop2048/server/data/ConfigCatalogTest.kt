package com.dangerfield.drop2048.server.data

import com.dangerfield.drop2048.server.domain.ManifestEntry
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The catalog is a hand-maintained mirror of the client's `ConfiguredValue`
 * registry, and it stops being one the day CI uploads a real manifest. Until
 * then these are the properties worth pinning: the SPEC 10 keys are all present,
 * the never-remote ones are all absent, and every entry says enough for an
 * operator to know what they are about to change.
 *
 * It cannot check the defaults against the client — `:apps:server` has no
 * dependency on `:libraries:cascade` and must not grow one, because the
 * server-only Docker build deliberately excludes every client module.
 */
class ConfigCatalogTest {

    @Test
    fun `every SPEC 10 key is listed`() {
        val expected = setOf(
            "spawn.table",
            "spawn.cap.divisor",
            "speed.curve",
            "speed.floorMs",
            "speed.tailStepMs",
            "special.wildcard.perMille",
            "special.wildcard.firstLevel",
            "special.bomb.perMille",
            "special.bomb.firstLevel",
            "special.stone.perMille",
            "special.stone.firstLevel",
            "board.rows",
            "level.blocksPerLevel",
            "ads.enabled",
            "ads.interstitial.minSessionRuns",
            "ads.interstitial.cooldownSeconds",
            "ads.interstitial.rewardedGapSeconds",
            "ads.interstitial.suppressDaysSinceInstall",
            "ads.rewarded.continuesPerRun",
            "ads.rewarded.dailyRetriesPerDay",
            "pro.upsell.enabled",
            "feature.dailyChallenge",
            "feature.leaderboards",
        )

        assertEquals(expected, ConfigCatalog.entries.map { it.path }.toSet())
    }

    @Test
    fun `nothing SPEC 10 calls never-remote is offered`() {
        val forbidden = listOf("scoring", "score.", "merge", "burst", "cascade.")

        ConfigCatalog.entries.forEach { entry ->
            forbidden.forEach { fragment ->
                assertTrue(
                    !entry.path.startsWith(fragment),
                    "${entry.path} looks like a never-remote key: changing scoring or resolution " +
                        "mid-flight silently invalidates every high score on the board",
                )
            }
        }
    }

    @Test
    fun `the clock carries its digest warning where the console will show it`() {
        val entry = assertNotNull(ConfigCatalog.entry("level.blocksPerLevel"))

        assertTrue(entry.description.orEmpty().contains("DETERMINISM DIGEST"))
        assertEquals(JsonPrimitive(20), entry.default)
    }

    @Test
    fun `every entry has a type the schema can check and a description`() {
        val known = setOf("boolean", "int", "long", "double", "string", "json")

        ConfigCatalog.entries.forEach { entry ->
            assertTrue(entry.type in known, "${entry.path} has type ${entry.type}")
            assertTrue(!entry.description.isNullOrBlank(), "${entry.path} has no description")
        }
    }

    @Test
    fun `no path is listed twice`() {
        assertEquals(ConfigCatalog.entries.size, ConfigCatalog.entries.map { it.path }.toSet().size)
    }

    @Test
    fun `the schema built from the catalog rejects a value the client cannot parse`() {
        val schema = ConfigSchema.from(ConfigCatalog.entries)

        assertNotNull(schema.validateValue("board.rows", JsonPrimitive("eight")))
        assertNotNull(schema.validateValue("ads.enabled", JsonPrimitive("yes")))
        assertEquals(null, schema.validateValue("board.rows", JsonPrimitive(7)))
        assertEquals(null, schema.validateValue("ads.enabled", JsonPrimitive(false)))
    }

    @Test
    fun `an uploaded manifest replaces the catalog and an empty one does not`() {
        val uploaded = listOf(ManifestEntry("board.rows", "int", JsonPrimitive(8), null, null))

        assertEquals(uploaded, uploaded.orCatalog())
        assertEquals(ConfigCatalog.entries, emptyList<ManifestEntry>().orCatalog())
    }
}
