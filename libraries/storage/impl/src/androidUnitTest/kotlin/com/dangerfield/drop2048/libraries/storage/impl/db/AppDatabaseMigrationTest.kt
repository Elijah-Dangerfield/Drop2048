package com.dangerfield.drop2048.libraries.storage.impl.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import com.dangerfield.drop2048.libraries.flowroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The only thing that checks the promise in L33.
 *
 * `fallbackToDestructiveMigrationFrom(1, 2, 3, 4)` says two things at once, and
 * a test of either half alone proves nothing (L35). One: a database at
 * [AppDatabase.FIRST_PLAYER_DATA_VERSION] or above **migrates**, because there
 * is no account and no server copy to restore a wiped run history from. Two:
 * a version with no path and no place on the list refuses rather than wiping.
 * Neither half means much alone: Room prefers a migration to a drop, so a
 * database that migrated proves nothing about the list, and an empty rebuilt
 * database is indistinguishable from a migrated one by `SELECT`.
 *
 * Every case goes through [RealAppDatabaseProvider], not through a builder
 * assembled here. A copy of the production configuration would keep passing the
 * day somebody widened the real one.
 *
 * The drop list stops at 4, so **version 5 migrates too** even though it predates
 * the game: `AutoMigration(5, 6)` exists and Room prefers a migration to a drop.
 * Only 1 through 4 have no path and are actually dropped, and there is no
 * exported schema for them under this database's name to build one from.
 *
 * 8 to 9 is the one hand-written step ([MIGRATE_AWAY_FROM_THE_DAILY]) and gets
 * its own case, because it is the first migration that *deletes* rather than
 * adds — and a deletion that half-ran is the failure Room's identity check
 * cannot see.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val driver: SQLiteDriver = AndroidSQLiteDriver()

    /**
     * A version 6 database — the first schema that held anything a player would
     * miss — carrying one finished run, walked up the whole chain to 9.
     */
    @Test
    fun `a version 6 database keeps its run history`() = runTest {
        val path = folder.newFile("player.db")
        path.delete()
        ExportedSchema.createDatabaseAt(version = 6, path = path, driver = driver)
        driver.open(path.absolutePath).use { connection ->
            connection.execSQL(
                "INSERT INTO run_record " +
                    "(endedAt, score, level, blocksPlaced, durationMs, highestTier, cause, " +
                    "longestCascade, bursts, merges, mode, seed) " +
                    "VALUES (1000, 4242, 7, 140, 90000, 512, 'ROW_ZERO_OCCUPIED', 4, 1, 60, " +
                    "'ENDLESS', 99)"
            )
        }

        val database = open(path)

        assertEquals(4242, database.runRecordDao().bestScore())
        assertEquals(99, database.runRecordDao().all().single().seed)
        database.close()
    }

    /**
     * The tables versions 7 and 8 added have to exist afterwards, not just be
     * declared. Version 7's `daily_result` is checked by its own test below,
     * which asserts it is *gone* by 9; these two are what a fresh chain leaves
     * behind.
     */
    @Test
    fun `the tables added after version 6 exist once the chain has run`() = runTest {
        val path = folder.newFile("player.db")
        path.delete()
        ExportedSchema.createDatabaseAt(version = 6, path = path, driver = driver)

        val database = open(path)

        assertEquals(emptyList(), database.achievementDao().facts())
        assertEquals(emptyList(), database.achievementDao().unlocks())
        database.close()
    }

    /**
     * Schema 9, and the three things it has to do at once (D27).
     *
     * A version 8 database is seeded with one Endless run and one Daily run in
     * each of the two tables that carried a mode, plus a row in `daily_result`.
     * After the migration: the Endless rows are intact and re-readable through
     * the DAOs, the Daily rows are gone rather than relabelled, `daily_result`
     * does not exist, and the achievement key has been rewritten from
     * `ENDLESS:2000` to `2000`.
     *
     * The Daily deletion is the half that cannot be done after the fact. Dropping
     * `mode` first would leave those rows behind as ordinary ones, folded into
     * every lifetime total and into `MAX(score)` — and nothing afterwards could
     * tell which they were.
     */
    @Test
    fun `version 9 drops the daily table, the mode column and the daily rows`() = runTest {
        val path = folder.newFile("player.db")
        path.delete()
        ExportedSchema.createDatabaseAt(version = 8, path = path, driver = driver)
        driver.open(path.absolutePath).use { connection ->
            connection.execSQL(
                "INSERT INTO run_record " +
                    "(endedAt, score, level, blocksPlaced, durationMs, highestTier, cause, " +
                    "longestCascade, bursts, merges, mode, seed) " +
                    "VALUES (2000, 4242, 7, 140, 90000, 512, 'ROW_ZERO_OCCUPIED', 4, 1, 60, " +
                    "'ENDLESS', 99)"
            )
            connection.execSQL(
                "INSERT INTO run_record " +
                    "(endedAt, score, level, blocksPlaced, durationMs, highestTier, cause, " +
                    "longestCascade, bursts, merges, mode, seed) " +
                    "VALUES (3000, 999999, 9, 200, 90000, 1024, 'ROW_ZERO_OCCUPIED', 5, 2, 80, " +
                    "'DAILY', 7)"
            )
            connection.execSQL(
                "INSERT INTO daily_result (date, seed, score, attemptsUsed, completed, retriesUsed) " +
                    "VALUES ('2026-09-09', 7, 999999, 1, 1, 0)"
            )
            connection.execSQL(
                "INSERT INTO achievement_fact " +
                    "(`key`, mode, score, level, blocksPlaced, durationMs, highestTier, " +
                    "longestCascade, bursts, merges, boardsCleared, stoneBursts, wildcardBursts, " +
                    "longestDangerRun, dailyStreakDays, endedAt) " +
                    "VALUES ('ENDLESS:2000', 'ENDLESS', 4242, 7, 140, 90000, 512, 4, 1, 60, " +
                    "0, 0, 0, 0, 0, 2000)"
            )
            connection.execSQL(
                "INSERT INTO achievement_fact " +
                    "(`key`, mode, score, level, blocksPlaced, durationMs, highestTier, " +
                    "longestCascade, bursts, merges, boardsCleared, stoneBursts, wildcardBursts, " +
                    "longestDangerRun, dailyStreakDays, endedAt) " +
                    "VALUES ('DAILY:3000', 'DAILY', 999999, 9, 200, 90000, 1024, 5, 2, 80, " +
                    "0, 0, 0, 0, 12, 3000)"
            )
        }

        val database = open(path)

        val runs = database.runRecordDao().all()
        assertEquals(1, runs.size, "the Daily run survived the migration: $runs")
        assertEquals(99, runs.single().seed)
        assertEquals(4242, database.runRecordDao().bestScore(), "a Daily score became the best")

        val facts = database.achievementDao().facts()
        assertEquals(1, facts.size, "the Daily fact survived the migration: $facts")
        assertEquals("2000", facts.single().key, "the fact key was not rewritten")
        database.close()

        assertFalse(
            ExportedSchema.tableExists(path, "daily_result", driver),
            "daily_result is still on disk",
        )
    }

    /**
     * The drop list is live: a version 4 database, which predates the game and
     * has no migration path, is emptied and rebuilt rather than refused.
     *
     * This is the control the two tests above need. Room prefers a migration to a
     * drop, so *every* version that has a path migrates whether or not it is on
     * the list — which means "the version 6 row survived" on its own says nothing
     * about the narrowing. This says the list is wired and does fire.
     */
    @Test
    fun `a version 4 database is emptied rather than refused`() = runTest {
        val path = folder.newFile("player.db")
        path.delete()
        ExportedSchema.createUnknownDatabaseAt(version = 4, path = path, driver = driver)

        val database = open(path)
        assertEquals(emptyList(), database.runRecordDao().all())
        database.close()

        assertFalse(ExportedSchema.tableExists(path, "legacy_thing", driver))
    }

    /**
     * And it is narrow, which is the half L33 is actually about: a version Room
     * has no path to and that is **not** on the list refuses to open, and leaves
     * every row where it was.
     *
     * Version 10 is a player who installed a newer build and rolled back — the
     * shape a blanket `fallbackToDestructiveMigration` (or a
     * `fallbackToDestructiveMigrationOnDowngrade` nobody thought twice about)
     * turns into a silent, unrecoverable wipe of a run history that exists in
     * exactly one place. The assertion that matters is the second one: refusing
     * to open is only the right outcome if nothing was destroyed on the way.
     */
    @Test
    fun `a version with no path and no fallback refuses to open rather than wiping`() = runTest {
        val path = folder.newFile("player.db")
        path.delete()
        ExportedSchema.createUnknownDatabaseAt(version = 10, path = path, driver = driver)

        assertFails { open(path).runRecordDao().all() }

        assertTrue(
            ExportedSchema.tableExists(path, "legacy_thing", driver),
            "the player's data was wiped rather than the open being refused",
        )
    }

    private fun open(path: File): AppDatabase =
        RealAppDatabaseProvider(
            builderFactory = object : AppDatabaseBuilderFactory {
                override fun create(): RoomDatabase.Builder<AppDatabase> =
                    Room.databaseBuilder<AppDatabase>(
                        context = RuntimeEnvironment.getApplication(),
                        name = path.absolutePath,
                    ).setDriver(driver)
            },
            dispatcherProvider = UnconfinedDispatchers,
        ).database

    private object UnconfinedDispatchers : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
        override val mainImmediate: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
    }
}
