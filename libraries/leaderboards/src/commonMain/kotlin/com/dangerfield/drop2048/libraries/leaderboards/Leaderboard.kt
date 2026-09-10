package com.dangerfield.drop2048.libraries.leaderboards

/**
 * Every board this game keeps, and the id it is filed under in the store's
 * console. SPEC 15 asks for exactly these three.
 *
 * A leaderboard is a shared room, and splitting a small player base across many
 * rooms empties all of them. So the test each candidate had to pass was not
 * "could this be a leaderboard" but "would a stranger's name be next to yours on
 * it in week one".
 *
 * - **[AllTimeScore]** is the headline, and it is **Endless only** (decision
 *   D19). A Daily score is set on a seed everybody else also played, so it is
 *   not comparable to an Endless one and it has its own board below.
 * - **[WeeklyScore]** is the same number in a rolling window. An all-time board
 *   is unwinnable for a newcomer and the standard answer is a window everybody
 *   starts level in. Game Center supports exactly that natively as a recurring
 *   leaderboard, so this is the same submitted value on a board the platform
 *   resets — nothing here computes a week.
 * - **[DailyScore]** is the Daily Challenge. Sodogku rejected the equivalent
 *   board and was right to: its daily rolls at *device-local* midnight, so a
 *   player in Auckland would be ranked against a puzzle nobody else had yet.
 *   SPEC 14 rolls ours at 00:00 UTC worldwide, which is the same instant the
 *   platform's recurring window can be set to, so the objection does not carry
 *   over. **Do not submit an Endless score here, or a Daily score to the other
 *   two** — `RealLeaderboards` has no idea what mode a value came from and it is
 *   the caller's job to pick the board.
 *
 * ## The ids
 *
 * Committed constants rather than remote config: an id is a store operation, and
 * a config outage that blanked one would take the board down with it. They are
 * typed by hand into App Store Connect and must match exactly.
 *
 * **None of the three exist in App Store Connect yet** — that is an owner task
 * and it is on `docs/OWNER-TODO.md`. Until they do, every submission fails and
 * fails silently, which is the same thing that happens when a player is signed
 * out and is exactly what the fail-open design in [Leaderboards] is for. The
 * call site ships regardless: an id that is wrong shows up as a board nobody is
 * on, and a `submit` with no caller shows up as nothing at all, which is the
 * failure this chunk exists to avoid.
 *
 * [id] is the Game Center id. Play Games is not in v1, and when it is its console
 * mints its own ids; that arrives as a second property here and a resolver, not
 * as a different interface.
 */
enum class Leaderboard(val id: String) {

    /** Best Endless score, all time. */
    AllTimeScore("com.dangerfield.drop2048.leaderboard.score_alltime"),

    /** Best Endless score this week, on the platform's own recurring window. */
    WeeklyScore("com.dangerfield.drop2048.leaderboard.score_weekly"),

    /** Best score on today's Daily board (SPEC 14). */
    DailyScore("com.dangerfield.drop2048.leaderboard.daily"),
}
