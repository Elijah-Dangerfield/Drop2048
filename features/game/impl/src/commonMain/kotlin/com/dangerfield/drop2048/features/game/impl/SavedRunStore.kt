package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The one value SPEC 11 keeps out of Room, and the only thing that knows its
 * format.
 *
 * `AppData.savedRun` is a string; this is where it becomes a [SavedRun] and back.
 * Keeping the codec here and not in the ViewModel is what lets [load] treat a
 * blob it cannot read as "no saved run" — the engine's serial shape moves with
 * every chunk, and a run saved by a build with a different `EngineConfig` must
 * cost the player that run, not the app its launch.
 */
interface SavedRunStore {
    suspend fun load(): SavedRun?
    suspend fun save(run: SavedRun)
    suspend fun clear()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AppCacheSavedRunStore(
    private val appCache: AppCache,
) : SavedRunStore {

    override suspend fun load(): SavedRun? {
        val stored = Catching { appCache.get().savedRun }
            .logOnFailure { "Could not read the saved run" }
            .getOrNull()
            ?: return null
        return Catching { json.decodeFromString(SavedRun.serializer(), stored) }
            .logOnFailure { "Saved run could not be decoded; starting fresh" }
            .getOrNull()
    }

    override suspend fun save(run: SavedRun) {
        val encoded = Catching { json.encodeToString(SavedRun.serializer(), run) }
            .logOnFailure { "Could not encode the run in progress" }
            .getOrNull()
            ?: return
        Catching { appCache.update { it.copy(savedRun = encoded) } }
            .logOnFailure { "Could not persist the run in progress" }
    }

    override suspend fun clear() {
        Catching { appCache.update { it.copy(savedRun = null) } }
            .logOnFailure { "Could not clear the saved run" }
    }

    private companion object {
        /**
         * `ignoreUnknownKeys` so a field this build no longer has does not cost
         * the player a run mid-cascade. A field it does not yet have is already
         * covered by the defaults on [SavedRun].
         */
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
