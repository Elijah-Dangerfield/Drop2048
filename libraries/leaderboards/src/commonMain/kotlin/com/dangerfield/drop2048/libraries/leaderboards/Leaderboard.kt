package com.dangerfield.drop2048.libraries.leaderboards

/**
 * Every board this game keeps, and the id it is filed under in the store's
 * console.
 *
 * A leaderboard is a shared room, and splitting a small player base across many
 * rooms empties all of them. So the test each candidate had to pass was not
 * "could this be a leaderboard" but "would a stranger's name be next to yours on
 * it in week one".
 *
 * - **[AllTimeScore]** is the headline and the board that matters. It is
 *   **Endless only** (decision D19): a Daily score is set on a seed everybody
 *   else also played, so it is not the same quantity and must not land here.
 * - **[WeeklyScore]** is the same number in a rolling window. An all-time board
 *   is unwinnable for a newcomer and the standard answer is a window everybody
 *   starts level in. Game Center supports exactly that natively as a recurring
 *   leaderboard, so this is the same submitted value on a board the platform
 *   resets — nothing here computes a week.
 *
 * **There is no Daily board** (D24). SPEC 15 asked for one and it was dropped:
 * the Daily Challenge is a single seed with a capped number of attempts, so its
 * ceiling is a property of the seed rather than of the player, and a board of it
 * ranks who got the luckiest drops on one board rather than who plays well. The
 * Daily's own streak badges are what reward playing it. Nothing else about SPEC
 * 14 changes, and a Daily run now posts to no board at all — `GameViewModel`
 * owns that rule, because nothing below it knows what mode a value came from.
 *
 * ## Recurring boards are not deduplicated
 *
 * [recurring] says the platform resets this board's window on its own schedule.
 * `RealLeaderboards` remembers the best value it has already had accepted and
 * declines to resend anything no better — which is right for an all-time board
 * and **wrong** for a recurring one, because the reset happens on the platform's
 * clock and nothing on this side can see it. A player whose app was alive across
 * a Monday boundary would have their first score of the new week dropped for not
 * beating last week's, and would then be absent from the new board entirely.
 *
 * The fix is to not deduplicate these at all rather than to model the window
 * here. Modelling it means agreeing with a recurrence start the owner typed into
 * App Store Connect, and a disagreement is the same silent skip in a form that
 * is harder to find. The cost of not modelling it is one network call per run on
 * a fire-and-forget path.
 *
 * ## The ids
 *
 * Committed constants rather than remote config: an id is a store operation, and
 * a config outage that blanked one would take the board down with it. They are
 * typed by hand into App Store Connect and must match exactly.
 *
 * **Neither exists in App Store Connect yet** — that is an owner task and it is
 * on `docs/OWNER-TODO.md`. Until they do, every submission fails and fails
 * silently, which is the same thing that happens when a player is signed out and
 * is exactly what the fail-open design in [Leaderboards] is for.
 *
 * [id] is the Game Center id. Play Games is not in v1, and when it is its console
 * mints its own ids; that arrives as a second property here and a resolver, not
 * as a different interface.
 */
enum class Leaderboard(val id: String, val recurring: Boolean) {

    /** Best Endless score, all time. The board this feature is for. */
    AllTimeScore("com.dangerfield.drop2048.leaderboard.score_alltime", recurring = false),

    /** Best Endless score this week, on the platform's own recurring window. */
    WeeklyScore("com.dangerfield.drop2048.leaderboard.score_weekly", recurring = true),
}
