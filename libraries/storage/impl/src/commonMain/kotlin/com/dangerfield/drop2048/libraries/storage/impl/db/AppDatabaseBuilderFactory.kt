package com.dangerfield.drop2048.libraries.storage.impl.db

import androidx.room.RoomDatabase

/**
 * Where the database file lives, and what opens it.
 *
 * The driver is set here rather than in [RealAppDatabaseProvider] because it is
 * as platform-shaped as the path is. `sqlite-bundled`'s Android variant loads
 * device `.so` files through `System.loadLibrary`, so a provider that hardcoded
 * it could only ever be constructed on a device or an emulator — which is why
 * the migration promise in [AppDatabase] went untested until a host test could
 * hand in the framework driver instead.
 */
interface AppDatabaseBuilderFactory {
    fun create(): RoomDatabase.Builder<AppDatabase>
}
