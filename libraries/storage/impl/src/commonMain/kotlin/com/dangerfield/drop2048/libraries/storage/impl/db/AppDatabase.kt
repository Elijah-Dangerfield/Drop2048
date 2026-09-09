package com.dangerfield.drop2048.libraries.storage.impl.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ExampleUserDataDao
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ExampleUserDataEntity
import com.dangerfield.drop2048.libraries.progress.db.RunRecordDao
import com.dangerfield.drop2048.libraries.progress.db.RunRecordEntity

@Database(
    entities = [
        ExampleUserDataEntity::class,
        RunRecordEntity::class,
    ],
    version = 6,
    /**
     * Every bump from [AppDatabase.FIRST_PLAYER_DATA_VERSION] on has to be listed
     * here.
     *
     * There is no account and no server copy (SPEC 20), so a player's entire run
     * history exists in exactly one place: this file on their phone. A
     * destructive fallback is a silent, unrecoverable wipe on the next release
     * that happens to add a column, and the player's best score goes with it.
     *
     * The addition here is a new table, which Room migrates on its own. A change
     * it cannot migrate — a renamed or retyped column — fails the build at this
     * line rather than on a player's device, which is the point.
     */
    autoMigrations = [
        AutoMigration(from = 5, to = 6),
    ],
    exportSchema = true
)
@TypeConverters(CoreTypeConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exampleUserDataDao(): ExampleUserDataDao
    abstract fun runRecordDao(): RunRecordDao

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
