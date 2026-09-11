package com.dangerfield.drop2048.features.settings.impl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The contract between the licence generator and the screen that reads it.
 *
 * Two separate things produce and consume `files/licenses.txt` — a Groovy init
 * script and a Compose screen — and nothing in the build type-checks between
 * them. This is the only thing that would notice the day the generator gains a
 * column or the screen starts reading one that was never written.
 *
 * The ordering assertion is the one worth keeping. The whole reason the screen
 * was rewritten is that the proprietary Google SDK rows were invisible while the
 * copy above them claimed everything was Apache or MIT; putting the biggest
 * group first and the smallest last would bury them again under 243 rows of
 * AndroidX.
 */
class LicenceListTest {

    @Test
    fun `groups by licence, biggest first, modules sorted inside`() {
        val groups = parseLicences(
            """
            androidx.activity:activity	1.12.0	Apache-2.0	https://apache.org
            io.ktor:ktor-client-core	3.0.0	Apache-2.0	https://apache.org
            com.google.android.gms:play-services-ads	24.0.0	Android Software Development Kit License	https://developers.google.com
            androidx.annotation:annotation	1.9.1	Apache-2.0	https://apache.org
            """.trimIndent()
        )

        assertEquals(2, groups.size)
        assertEquals("Apache-2.0", groups[0].licence)
        assertEquals(
            listOf("androidx.activity:activity", "androidx.annotation:annotation", "io.ktor:ktor-client-core"),
            groups[0].modules,
        )
        assertEquals("Android Software Development Kit License", groups[1].licence)
        assertEquals("https://developers.google.com", groups[1].url)
    }

    /**
     * A module whose POM declares nothing is written as `UNDECLARED` with an
     * empty URL, which still has to be four tab-separated fields. The generator
     * deliberately does not guess (`docs/store/licenses.md`), so the screen has
     * to be able to show the row it refuses to guess about.
     */
    @Test
    fun `an undeclared row survives with an empty url`() {
        val groups = parseLicences("com.google.guava:guava\t33.0.0\tUNDECLARED\t")

        assertEquals(1, groups.size)
        assertEquals("UNDECLARED", groups.single().licence)
        assertTrue(groups.single().url.isEmpty())
    }

    @Test
    fun `blank lines and short lines are dropped rather than crashing the screen`() {
        val groups = parseLicences("\nnot-a-row\nandroidx.activity:activity\t1.12.0\tMIT\t\n\n")

        assertEquals(listOf("androidx.activity:activity"), groups.single().modules)
    }
}
