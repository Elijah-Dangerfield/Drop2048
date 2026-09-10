package com.dangerfield.drop2048.libraries.progress.impl.daily

import com.dangerfield.drop2048.libraries.progress.daily.dailyDayOf
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The date arithmetic behind the Daily, as free functions over an instant.
 *
 * The clock is never read in here. That is the point: midnight, a flight east
 * and a device clock set to 1970 are the same function call with different
 * arguments, so all three are testable and none of them needs a device.
 */
internal fun untilNextUtcDay(now: Instant): Duration =
    (dayAfter(now).atStartOfDayIn(TimeZone.UTC) - now).coerceAtLeast(1.seconds)

/**
 * The day the seed rotates to next.
 *
 * Goes through `atStartOfDayIn` rather than adding 24 hours. UTC has no DST so
 * every one of its days really is 24 hours long, but the arithmetic is written
 * calendar-first anyway, because the failure it avoids is not a leap second: it
 * is someone later widening this to a zone that does have transitions and
 * finding the bug on a spring Sunday.
 *
 * Floored at a second in [untilNextUtcDay] so a clock that somehow puts the next
 * day in the past cannot turn the rollover flow into a busy loop. Being a second
 * late is survivable; spinning a phone's CPU is not.
 */
private fun dayAfter(now: Instant): LocalDate = dailyDayOf(now).nextDay()

internal fun LocalDate.nextDay(): LocalDate = plus(1, DateTimeUnit.DAY)

internal fun LocalDate.previousDay(): LocalDate = minus(1, DateTimeUnit.DAY)
