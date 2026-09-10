package com.dangerfield.drop2048.libraries.storage.impl.db

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers
import org.robolectric.RuntimeEnvironment

/**
 * A real [AppDatabase] backed by real SQLite, for the length of one test.
 *
 * The project had no Room-backed test on any platform until this one. Every DAO
 * had a hand-rolled fake standing in for it, and a fake mirrors the Kotlin
 * signature rather than the SQL — `FakeRunRecordDao` re-implemented
 * `bestScore()`'s `WHERE mode = 'ENDLESS'` filter in Kotlin, so the filter was
 * covered and the query string was not. The two can disagree, and nothing
 * anywhere would have said so.
 *
 * [AndroidSQLiteDriver] rather than the `BundledSQLiteDriver` the app ships,
 * and that is forced rather than chosen. `sqlite-bundled`'s **android** variant
 * carries `.so` files for device ABIs and loads them with a plain
 * `System.loadLibrary`, so on a host JVM it dies with
 * `no sqliteJni in java.library.path` before a single statement runs. Robolectric
 * 4.16 backs the framework driver with a real SQLite build, so what these tests
 * exercise is real SQL — what they do not pin is the exact SQLite version the
 * app links on device.
 *
 * Queries run on `Dispatchers.Unconfined`, which keeps a `runTest` body free of
 * the "unfinished coroutine" hazard a background query context introduces.
 */
fun inMemoryAppDatabase(): AppDatabase =
    Room.inMemoryDatabaseBuilder<AppDatabase>(context = RuntimeEnvironment.getApplication())
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Unconfined)
        .build()
