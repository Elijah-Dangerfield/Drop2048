package com.dangerfield.drop2048.libraries.progress.impl.daily

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The rollover countdown, at every hour it can be wrong at.
 *
 * None of these needs a device or a clock: midnight, a flight east and a device
 * clock set to 1970 are the same call with different arguments.
 */
class DailyCalendarTest {

    @Test
    fun countsDownToMidnightUtc() {
        assertEquals(
            12.hours,
            untilNextUtcDay(Instant.parse("2026-09-09T12:00:00Z")),
        )
    }

    @Test
    fun aMomentAfterMidnightIsAlmostAWholeDay() {
        assertEquals(
            24.hours - 1.minutes,
            untilNextUtcDay(Instant.parse("2026-09-09T00:01:00Z")),
        )
    }

    @Test
    fun exactlyMidnightIsAWholeDay() {
        assertEquals(24.hours, untilNextUtcDay(Instant.parse("2026-09-09T00:00:00Z")))
    }

    /**
     * A player in Auckland is already on the 10th at this instant. Their board
     * still rotates at the same moment as everyone else's, and the countdown they
     * see says so.
     */
    @Test
    fun theCountdownIsTheSameEverywhere() {
        val moment = Instant.parse("2026-09-09T20:00:00Z")
        assertEquals(4.hours, untilNextUtcDay(moment))
    }

    /**
     * A device clock far enough in the past to make `now` sit before the epoch
     * still gets a positive, finite answer rather than a busy loop. The flow that
     * drives the day rollover `delay`s on this value.
     */
    @Test
    fun anAbsurdClockStillProducesAPositiveDelay() {
        val ancient = untilNextUtcDay(Instant.parse("1970-01-01T00:00:01Z"))
        assertTrue(ancient >= 1.seconds, "got $ancient")
        assertTrue(ancient <= 24.hours, "got $ancient")
    }
}
