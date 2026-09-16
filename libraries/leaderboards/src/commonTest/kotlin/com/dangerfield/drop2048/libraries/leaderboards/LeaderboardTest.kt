package com.dangerfield.drop2048.libraries.leaderboards

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The board ids, which nothing else can check.
 *
 * They are typed by hand into App Store Connect and matched by string, so the
 * two mistakes available are a blank and a duplicate. Both are silent: a blank
 * id is a submission the platform rejects, and a shared id quietly merges two
 * boards into one that ranks this week's runs against every run ever played.
 * Neither shows up as a failure anywhere else, which is the whole reason this
 * file exists.
 *
 * What it cannot check is whether the ids *exist*. They do not yet — creating
 * them is an owner task — and no test on this side of the network can tell a
 * typo from a board nobody has made.
 *
 * Every assertion is guarded by the count, so deleting the enum entries fails
 * the test rather than making it vacuously true over an empty list.
 */
class LeaderboardTest {

    @Test
    fun thereAreExactlyTwoBoards() {
        assertEquals(BoardCount, Leaderboard.entries.size)
    }

    /**
     * The flag that exempts a board from `RealLeaderboards`' already-submitted
     * gate, so getting it wrong is a silently skipped submission rather than a
     * compile error. Pinned by name here because the enum is the only place the
     * two answers can be told apart.
     */
    @Test
    fun onlyTheWeeklyBoardRecurs() {
        assertEquals(BoardCount, Leaderboard.entries.size)
        assertEquals(
            listOf(Leaderboard.WeeklyScore),
            Leaderboard.entries.filter { it.recurring },
        )
    }

    @Test
    fun everyBoardHasAnId() {
        assertEquals(BoardCount, Leaderboard.entries.size)
        Leaderboard.entries.forEach { board ->
            assertTrue(board.id.isNotBlank(), "${board.name} has no leaderboard id")
        }
    }

    @Test
    fun noTwoBoardsShareAnId() {
        assertEquals(BoardCount, Leaderboard.entries.size)
        assertEquals(
            Leaderboard.entries.size,
            Leaderboard.entries.map { it.id }.toSet().size,
        )
    }

    private companion object {
        /**
         * All-time high score and weekly high score. SPEC 15 asked for a third,
         * the Daily Challenge, and D24 dropped it — a board over one seed with a
         * capped attempt count ranks the luck of the drops rather than the
         * player.
         */
        const val BoardCount = 2
    }
}
