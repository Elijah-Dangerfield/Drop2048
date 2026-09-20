package com.dangerfield.drop2048.admin

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The gameplay half of SPEC 10 is fifteen keys, and exactly one of them is not
 * safe to turn live. That distinction is measured (D9, L19, L28, L40) and it is
 * invisible in the console unless something puts it there, so it is pinned here:
 * a regression that drops the warning is a silent one, and so is a regression
 * that starts warning on the safe keys until nobody reads warnings any more.
 */
class DangerousWarningTest {

    @Test
    fun `changing the clock warns, on any value`() {
        assertNotNull(dangerousWarning("level.blocksPerLevel", "15"))
        assertNotNull(dangerousWarning("level.blocksPerLevel", "20"))
        assertNotNull(dangerousWarning("level.blocksPerLevel", "nonsense"))
    }

    @Test
    fun `the warning says what it actually costs`() {
        val warning = dangerousWarning("level.blocksPerLevel", "15").orEmpty()

        assertTrue(warning.contains("DETERMINISM DIGEST"), "names the hazard")
        assertTrue(warning.contains("bug report"), "names what breaks")
    }

    @Test
    fun `the keys measured safe to change live do not warn`() {
        val safe = listOf(
            "speed.curve" to "[500,470,440]",
            "speed.floorMs" to "90",
            "speed.tailStepMs" to "2",
            "spawn.table" to "{\"bands\":[]}",
            "spawn.cap.divisor" to "16",
            "board.rows" to "7",
            "special.wildcard.perMille" to "30",
            "special.bomb.firstLevel" to "8",
        )

        safe.forEach { (path, value) ->
            assertNull(dangerousWarning(path, value), "$path should not warn")
        }
    }

    @Test
    fun `the ad and Pro keys do not warn`() {
        assertNull(dangerousWarning("ads.enabled", "false"))
        assertNull(dangerousWarning("ads.interstitial.cooldownSeconds", "300"))
        assertNull(dangerousWarning("pro.upsell.enabled", "false"))
        assertNull(dangerousWarning("feature.leaderboards", "false"))
    }

    @Test
    fun `the template's own lockout warnings still fire`() {
        assertNotNull(dangerousWarning("upgrade.maintenanceMode", "\"blocking\""))
        assertNotNull(dangerousWarning("upgrade.maintenanceMode", "\"banner\""))
        assertNull(dangerousWarning("upgrade.maintenanceMode", "\"off\""))
        assertNotNull(dangerousWarning("upgrade.minSupportedVersionCode", "12"))
    }
}
