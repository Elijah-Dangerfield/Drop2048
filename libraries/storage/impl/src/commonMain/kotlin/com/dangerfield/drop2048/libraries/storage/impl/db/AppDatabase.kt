package com.dangerfield.drop2048.libraries.storage.impl.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.dangerfield.drop2048.libraries.achievements.db.AchievementDao
import com.dangerfield.drop2048.libraries.achievements.db.AchievementFactEntity
import com.dangerfield.drop2048.libraries.achievements.db.AchievementUnlockEntity
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ExampleUserDataDao
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ExampleUserDataEntity
import com.dangerfield.drop2048.libraries.progress.db.RunRecordDao
import com.dangerfield.drop2048.libraries.progress.db.RunRecordEntity

@Database(
    entities = [
        ExampleUserDataEntity::class,
        RunRecordEntity::class,
        AchievementFactEntity::class,
        AchievementUnlockEntity::class,
    ],
    version = 9,
    /**
     * Every bump from [AppDatabase.FIRST_PLAYER_DATA_VERSION] on has to be listed
     * here.
     *
     * There is no account and no server copy (SPEC 20), so a player's entire run
     * history exists in exactly one place: this file on their phone. A
     * destructive fallback is a silent, unrecoverable wipe on the next release
     * that happens to add a column, and the player's best score goes with it.
     *
     * A change Room cannot migrate on its own — a renamed or retyped column —
     * fails the build at this line rather than on a player's device, which is the
     * point.
     *
     * 6 to 7 adds `daily_result`, and 7 to 8 adds `achievement_fact` and
     * `achievement_unlock` (SPEC 15). All three are new tables, so Room migrates
     * them on its own; the facts are what let a badge shipped in a later release
     * back-fill from a player's history, so losing them would silently reset
     * everybody to zero on a release that only added a column.
     *
     * **8 to 9 is not here.** It drops `daily_result` and two columns with the
     * Daily Challenge (D27), and it has to delete the rows those columns identify
     * on the way past — which is a thing `@DeleteTable` and `@DeleteColumn` cannot
     * say. It is hand-written in [MIGRATE_AWAY_FROM_THE_DAILY] and added to the
     * builder in `RealAppDatabaseProvider`. A migration living somewhere other
     * than this list is worth knowing about, which is what this paragraph is for.
     */
    autoMigrations = [
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
    ],
    exportSchema = true
)
@TypeConverters(CoreTypeConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exampleUserDataDao(): ExampleUserDataDao
    abstract fun runRecordDao(): RunRecordDao
    abstract fun achievementDao(): AchievementDao

    companion object {
        /**
         * The first schema that held anything a player would miss. Versions below
         * it are template history from before the game existed; no install has
         * ever run them, so they are the only ones the builder may drop.
         */
        const val FIRST_PLAYER_DATA_VERSION = 6
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
