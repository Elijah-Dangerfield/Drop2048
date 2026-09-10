package com.dangerfield.drop2048.libraries.progress.daily

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The whole content pipeline for SPEC 14, in one pure function.
 *
 * The engine is a seeded state machine (SPEC 4.1), so "today's challenge" is a
 * number rather than a board, a pack or a server call. Two devices agree because
 * they compute the same number from the same date, not because anything told
 * them to.
 *
 * ### Why the day is UTC everywhere, and not the player's local day
 *
 * SPEC 14 says the seed rotates at 00:00 UTC and SPEC 11 keys `daily_result` on
 * a UTC date. Those two together force the rest: the attempt allowance, the row
 * that records it and the streak that folds over those rows are all keyed on the
 * **same** UTC day.
 *
 * A local-day streak over a UTC-day ledger was considered and rejected. It is
 * two clocks: a player in UTC-5 who plays at 19:00 and again at 20:00 would
 * spend two boards in one of their evenings and none in another, so a local day
 * would sometimes hold two rows and sometimes none, and every count over that
 * table would have to decide which of the two days it meant. One clock is the
 * only version of this that a fold can be right about.
 *
 * The player's own zone is used for exactly one thing, and it is the thing that
 * makes the UTC boundary fair rather than surprising: [DailyStatus.resetsIn] is
 * rendered against their clock, so the screen says when the next board arrives
 * in a time they recognise. See `DeviceTimeZone`.
 */
fun dailySeedFor(date: LocalDate): Long = mix(date.toEpochDays() * Stride + Salt)

/** The UTC day [now] falls in. The only definition of "today" the daily has. */
fun dailyDayOf(now: Instant): LocalDate = now.toLocalDateTime(TimeZone.UTC).date

/**
 * splitmix64's finalizer, which is also the engine's mixing step (`Rng`).
 *
 * Reimplemented here rather than depending on `:libraries:cascade` so
 * `:libraries:progress` stays engine-free, which is the same reason
 * `RunRecord.cause` is a string. Three lines of published arithmetic is a
 * cheaper coupling than a module edge, and `DailySeedTest` pins the values.
 *
 * Adjacent days must not produce adjacent-looking runs, which is what the
 * finalizer is for: consecutive epoch days differ by one bit before it and by
 * roughly half their bits after it.
 */
private fun mix(value: Long): Long {
    var z = value
    z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
    z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
    return z xor (z ushr 31)
}

/**
 * Two arbitrary constants, fixed forever from the moment the first Daily score
 * exists. Moving either re-rolls every past and future day.
 *
 * [Stride] is **odd**, so multiplying by it is a bijection on `Long` and no two
 * days can collide. [Salt] exists because the finalizer maps zero to zero, and
 * 1970-01-01 is epoch day zero: without it the first day of the epoch would be
 * seeded 0, which is a legal seed but a conspicuous one to find in a bug report.
 */
private const val Stride = -0x61c8864680b583ebL
private const val Salt = 0x2545F4914F6CDD1DL
