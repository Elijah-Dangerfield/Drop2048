package com.dangerfield.drop2048.libraries.storage.impl.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ExampleUserDataDao
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ExampleUserDataEntity

@Database(
    entities = [
        ExampleUserDataEntity::class,
    ],
    version = 5, // Bumped: demo User/Session tables replaced by the example table
    exportSchema = true
)
@TypeConverters(CoreTypeConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exampleUserDataDao(): ExampleUserDataDao
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
