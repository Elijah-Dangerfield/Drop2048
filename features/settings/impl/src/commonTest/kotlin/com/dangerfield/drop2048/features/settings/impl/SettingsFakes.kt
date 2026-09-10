package com.dangerfield.drop2048.features.settings.impl

import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ClearableDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeAppCache(initial: AppData = AppData()) : AppCache {
    private val stored = MutableStateFlow(initial)

    /** The current value without suspending, for assertions outside a coroutine. */
    val snapshot: AppData get() = stored.value

    override val updates: Flow<AppData> = stored
    override suspend fun get(): AppData = stored.value
    override suspend fun set(value: AppData) {
        stored.value = value
    }

    override suspend fun clear() {
        stored.value = AppData()
    }
}

/** One table, counting how many times it was asked to empty itself. */
internal class RecordingDao(private val failing: Boolean = false) : ClearableDao {
    var cleared = 0
        private set

    override suspend fun deleteAll() {
        cleared += 1
        if (failing) error("this table refuses")
    }
}
