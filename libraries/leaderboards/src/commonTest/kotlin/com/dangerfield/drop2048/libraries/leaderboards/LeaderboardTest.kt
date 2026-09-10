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
 * boards into one that ranks an Endless run against a Daily. Neither shows up as
 * a failure anywhere else, which is the whole reason this file exists.
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
    fun thereAreExactlyThreeBoards() {
        assertEquals(BoardCount, Leaderboard.entries.size)
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
        /** SPEC 15: all-time high score, weekly high score, Daily Challenge. */
        const val BoardCount = 3
    }
}
