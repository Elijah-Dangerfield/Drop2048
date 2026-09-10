package com.dangerfield.drop2048.libraries.progress.daily

import kotlinx.datetime.TimeZone

/**
 * The zone the player's device is currently in.
 *
 * Not what decides the day — the day is UTC (see `dailySeedFor`). This is what
 * turns "00:00 UTC" into a sentence the player recognises: the countdown on the
 * Daily screen and the date it prints are rendered in their own zone, so the
 * board arriving at 19:00 their time is a fact they can see rather than one they
 * discover by losing a streak.
 *
 * A seam rather than a `TimeZone` in the graph because the zone *changes* while
 * the app is running. A player who flies east and back would otherwise keep
 * being told the wrong local time for the rollover until they force-quit, and it
 * is the only reason any of this is testable without a device.
 *
 * Lives here rather than in `:libraries:core` because the daily is the only
 * caller. Move it up if a second one appears.
 */
fun interface DeviceTimeZone {
    fun current(): TimeZone
}
