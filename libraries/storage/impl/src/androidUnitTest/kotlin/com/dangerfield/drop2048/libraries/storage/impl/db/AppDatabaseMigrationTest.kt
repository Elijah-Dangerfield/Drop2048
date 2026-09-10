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
import kotlin.test.assertNull

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
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val driver: SQLiteDriver = AndroidSQLiteDriver()

    /**
     * A version 6 database — the first schema that held anything a player would
     * miss — carrying one finished run, walked up the `AutoMigration` chain to 8.
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
     * declared. A migration that ran but left `daily_result` behind fails here on
     * the query rather than three chunks later on a player's phone.
     */
    @Test
    fun `the tables added after version 6 exist once the chain has run`() = runTest {
        val path = folder.newFile("player.db")
        path.delete()
        ExportedSchema.createDatabaseAt(version = 6, path = path, driver = driver)

        val database = open(path)

        assertNull(database.dailyResultDao().forDate("2026-09-09"))
        assertEquals(emptyList(), database.achievementDao().facts())
        assertEquals(emptyList(), database.achievementDao().unlocks())
        database.close()
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
     * Version 9 is a player who installed a newer build and rolled back — the
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
        ExportedSchema.createUnknownDatabaseAt(version = 9, path = path, driver = driver)

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
