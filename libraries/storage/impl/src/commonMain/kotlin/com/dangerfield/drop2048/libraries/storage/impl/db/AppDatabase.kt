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
import com.dangerfield.drop2048.libraries.progress.db.DailyResultDao
import com.dangerfield.drop2048.libraries.progress.db.DailyResultEntity
import com.dangerfield.drop2048.libraries.progress.db.RunRecordDao
import com.dangerfield.drop2048.libraries.progress.db.RunRecordEntity

@Database(
    entities = [
        ExampleUserDataEntity::class,
        RunRecordEntity::class,
        DailyResultEntity::class,
        AchievementFactEntity::class,
        AchievementUnlockEntity::class,
    ],
    version = 8,
    /**
     * Every bump from [AppDatabase.FIRST_PLAYER_DATA_VERSION] on has to be listed
     * here.
     *
     * There is no account and no server copy (SPEC 20), so a player's entire run
     * history exists in exactly one place: this file on their phone. A
     * destructive fallback is a silent, unrecoverable wipe on the next release
     * that happens to add a column, and the player's best score goes with it.
     *
     * Both additions so far are new tables, which Room migrates on its own. A
     * change it cannot migrate — a renamed or retyped column — fails the build at
     * this line rather than on a player's device, which is the point.
     *
     * 6 to 7 adds `daily_result`. It is the first table whose loss would cost the
     * player something they cannot replay: a run history can at least be re-earned
     * by playing, and a Daily streak cannot, because the boards it was built on
     * are in the past.
     *
     * 7 to 8 adds `achievement_fact` and `achievement_unlock` (SPEC 15). Two more
     * new tables, so Room migrates them on its own; the facts are what let a badge
     * shipped in a later release back-fill from a player's history, so losing them
     * would silently reset everybody to zero on a release that only added a
     * column.
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
    abstract fun dailyResultDao(): DailyResultDao
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
