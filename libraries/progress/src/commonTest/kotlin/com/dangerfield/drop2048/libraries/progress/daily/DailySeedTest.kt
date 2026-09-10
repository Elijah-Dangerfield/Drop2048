package com.dangerfield.drop2048.libraries.progress.daily

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The Daily Challenge's whole promise, in one file.
 *
 * `commonTest`, so these constants are asserted on JVM, Android and iOS. That is
 * the point rather than a detail: "two devices on the same UTC day play the same
 * game" is a claim about *platforms*, and a test that only ran on one of them
 * would not be making it. A platform whose `Long` arithmetic or unsigned shift
 * diverged would fail here and nowhere else.
 *
 * The pinned seeds below are re-derivable but must not be re-derived casually.
 * From the first recorded Daily score they are a promise about which board a
 * given day was, and moving them re-rolls the past as well as the future.
 */
class DailySeedTest {

    @Test
    fun sameDayProducesTheSameSeed_whichIsTheWholeMode() {
        val day = LocalDate(2026, 9, 9)
        assertEquals(dailySeedFor(day), dailySeedFor(LocalDate.parse("2026-09-09")))
    }

    /**
     * Pinned. If one of these has to change it is a breaking change to every
     * recorded Daily result, not a test fixup.
     */
    @Test
    fun seedsArePinnedPerDay() {
        assertEquals(-7_696_910_208_926_366_575L, dailySeedFor(LocalDate(1970, 1, 1)))
        assertEquals(6_768_401_296_324_266_825L, dailySeedFor(LocalDate(2026, 9, 9)))
        assertEquals(8_067_237_043_722_039_585L, dailySeedFor(LocalDate(2026, 9, 10)))
    }

    /**
     * Adjacent days differ by one bit before the mix. If they differed by one bit
     * after it, consecutive dailies would open on near-identical boards and the
     * mode would feel like the same puzzle every morning.
     */
    @Test
    fun adjacentDaysAreNotAdjacentSeeds() {
        val a = dailySeedFor(LocalDate(2026, 9, 9))
        val b = dailySeedFor(LocalDate(2026, 9, 10))
        val differingBits = (a xor b).countOneBits()
        assertTrue(differingBits in 16..48, "adjacent days differed in $differingBits bits")
    }

    @Test
    fun everyDayOfAYearGetsItsOwnSeed() {
        val first = LocalDate(2026, 1, 1).toEpochDays()
        val seeds = (0 until 365).map { dailySeedFor(LocalDate.fromEpochDays(first + it)) }
        assertEquals(seeds.size, seeds.toSet().size)
    }

    /**
     * The day is UTC and nothing else, which is what makes the seed the same
     * worldwide. The second assertion is the one that gives the first any force:
     * it pins that Auckland really is on a different calendar day at that moment,
     * so a `dailyDayOf` that quietly used the device zone would fail here.
     */
    @Test
    fun theDayIsUtcRegardlessOfWhereTheDeviceIs() {
        val moment = Instant.parse("2026-09-09T12:00:00Z")
        assertEquals(LocalDate(2026, 9, 9), dailyDayOf(moment))
        assertNotEquals(
            LocalDate(2026, 9, 9),
            moment.toLocalDateTime(TimeZone.of("Pacific/Auckland")).date,
            "the fixture is pointless unless Auckland really is on the next day",
        )
    }

    /**
     * 00:00 UTC exactly is the new day, and one millisecond earlier is not.
     * SPEC 14 says the seed rotates at 00:00 UTC, and an off-by-one here is a
     * whole board.
     */
    @Test
    fun theBoundaryIsMidnightUtcExactly() {
        assertEquals(LocalDate(2026, 9, 9), dailyDayOf(Instant.parse("2026-09-09T23:59:59.999Z")))
        assertEquals(LocalDate(2026, 9, 10), dailyDayOf(Instant.parse("2026-09-10T00:00:00Z")))
    }
}
