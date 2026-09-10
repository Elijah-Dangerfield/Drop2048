package com.dangerfield.drop2048.libraries.progress.impl.daily

import com.dangerfield.drop2048.libraries.progress.daily.DailyResult
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SPEC 14's streak, and the four ways a streak gets counted wrong.
 *
 * Every case is a set of rows and a date. There is no clock and no repository
 * here on purpose: the fold is the thing that has to be right, and a test that
 * had to arrange a database to ask it a question would be testing the database.
 */
class DailyStreakFoldTest {

    @Test
    fun noRowsIsNoStreak() {
        assertEquals(0, streakFrom(Today, emptyList()).current)
    }

    @Test
    fun consecutiveCompletedDaysCount() {
        val streak = streakFrom(Today, completed(9, 8, 7))
        assertEquals(3, streak.current)
        assertEquals(3, streak.best)
    }

    /**
     * A run through yesterday still counts all of today. Breaking it the moment
     * the clock passes midnight would punish a player for not having played yet
     * on a day they still have fourteen hours of.
     */
    @Test
    fun todayDoesNotHaveToBeDoneYet() {
        assertEquals(2, streakFrom(Today, completed(8, 7)).current)
    }

    /** A gap ends it, whatever is behind the gap. */
    @Test
    fun aMissedDayBreaksIt() {
        assertEquals(0, streakFrom(Today, completed(7, 6, 5)).current)
    }

    /**
     * A day that was opened and never finished is not a win and not yet a break.
     * The player still has the rest of the day.
     */
    @Test
    fun anUnfinishedTodayLeavesYesterdaysRunStanding() {
        val rows = completed(8, 7) + DailyResult(
            date = day(9),
            seed = 1,
            score = 0,
            attemptsUsed = 1,
            completed = false,
        )
        assertEquals(2, streakFrom(Today, rows).current)
    }

    /**
     * The device clock moved backwards. A clock pushed forward, played on, and
     * pulled back leaves real rows dated in the future — and neither number may
     * count them, or the page would show a streak the player can see is not
     * theirs.
     */
    @Test
    fun futureDatedRowsAreInvisibleToBothNumbers() {
        val rows = completed(9, 8) + completed(10, 11, 12)
        val streak = streakFrom(Today, rows)
        assertEquals(2, streak.current)
        assertEquals(2, streak.best)
    }

    /** SPEC 15's "best". A broken run in the past still holds the record. */
    @Test
    fun bestIsTheLongestRunAnywhereInHistory() {
        val rows = completed(1, 2, 3, 4, 5) + completed(8, 9)
        val streak = streakFrom(Today, rows)
        assertEquals(2, streak.current)
        assertEquals(5, streak.best)
    }

    /**
     * When the current run *is* the best, the two numbers agree. A "best" that
     * only counted finished runs would trail behind the number beside it, which
     * reads as a bug on the day it matters most.
     */
    @Test
    fun bestIncludesTheRunInProgress() {
        val streak = streakFrom(Today, completed(9, 8, 7, 6))
        assertEquals(4, streak.current)
        assertEquals(4, streak.best)
    }

    /**
     * A month boundary is not a break. Calendar arithmetic done by subtracting
     * day-of-month would say otherwise.
     */
    @Test
    fun aMonthBoundaryIsNotAGap() {
        val rows = listOf(
            LocalDate(2026, 8, 30),
            LocalDate(2026, 8, 31),
            LocalDate(2026, 9, 1),
        ).map { completedOn(it) }
        assertEquals(3, streakFrom(LocalDate(2026, 9, 1), rows).current)
    }

    private fun completed(vararg daysOfSeptember: Int) = daysOfSeptember.map { completedOn(day(it)) }

    private fun completedOn(date: LocalDate) = DailyResult(
        date = date,
        seed = 1,
        score = 100,
        attemptsUsed = 1,
        completed = true,
    )

    private fun day(dayOfSeptember: Int) = LocalDate(2026, 9, dayOfSeptember)

    private companion object {
        val Today = LocalDate(2026, 9, 9)
    }
}
