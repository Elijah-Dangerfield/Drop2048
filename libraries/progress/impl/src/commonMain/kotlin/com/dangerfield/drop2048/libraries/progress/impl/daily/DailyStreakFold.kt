package com.dangerfield.drop2048.libraries.progress.impl.daily

import com.dangerfield.drop2048.libraries.progress.daily.DailyResult
import com.dangerfield.drop2048.libraries.progress.daily.DailyStreak
import kotlinx.datetime.LocalDate

/**
 * SPEC 14's streak, folded out of `daily_result` every time it is asked for.
 *
 * A stored counter would be one dropped write or one bad clock away from a
 * number nobody can reconstruct, and the player would have no way to tell us it
 * was wrong. This walks the rows instead, so it is a pure function of what is on
 * disk and it re-derives correctly after any bug we later fix.
 *
 * Rules, in the order they bite:
 *
 * - **Only a completed day counts.** A day whose attempt was started and never
 *   finished is neither a win nor a break: it is still open, and it stops
 *   counting only when the day is over.
 * - **Today does not have to be done yet.** A run through yesterday still counts
 *   all day today; it breaks once today has passed unplayed. So the walk starts
 *   at today when today is completed and at yesterday otherwise.
 * - **A day that was played and lost is still completed.** SPEC 14 scores the
 *   Daily on final score and every run ends stacked out, so there is no losing
 *   state to punish — turning up is the streak.
 * - **Future-dated rows are invisible**, which is what a clock set forward and
 *   back leaves behind. The walk only ever moves backwards from today, so they
 *   sit on disk unread until the date catches up with them.
 */
internal fun streakOn(today: LocalDate, completed: Set<LocalDate>): Int {
    var day = if (today in completed) today else today.previousDay()
    var streak = 0
    while (day in completed) {
        streak++
        day = day.previousDay()
    }
    return streak
}

/**
 * The longest run of consecutive completed days anywhere in the history, for
 * SPEC 15's "Daily streak current and best".
 *
 * Walks the sorted dates once rather than calling [streakOn] per day, and counts
 * every run including the current one — a player whose best is today should see
 * the two numbers agree rather than see "best" trail behind what is on screen.
 */
internal fun bestStreak(completed: Set<LocalDate>): Int {
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    completed.sorted().forEach { day ->
        run = if (previous?.nextDay() == day) run + 1 else 1
        if (run > best) best = run
        previous = day
    }
    return best
}

/**
 * Both numbers from one pass over the table.
 *
 * [today] is passed in rather than read, so a test that moves the clock
 * backwards is a different argument rather than a different environment.
 *
 * Days later than [today] are dropped here, in one place, so *both* numbers
 * ignore them. A clock pushed forward and pulled back leaves real rows in the
 * future, and a "best" that counted them would report a streak the player can
 * see is not theirs while the current one correctly says otherwise.
 */
internal fun streakFrom(today: LocalDate, results: Collection<DailyResult>): DailyStreak {
    val completed = results.filter { it.completed && it.date <= today }.map { it.date }.toSet()
    return DailyStreak(
        current = streakOn(today, completed),
        best = bestStreak(completed),
    )
}
