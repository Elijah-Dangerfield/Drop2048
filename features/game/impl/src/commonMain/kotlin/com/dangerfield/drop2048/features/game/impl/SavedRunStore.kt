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
 * `AppData.savedRun` is a string; this is where it becomes a [SavedRun] and
 * back. Keeping the codec here and not in the ViewModel is what lets [load]
 * treat a blob it cannot read as "no saved run" — the engine's serial shape
 * moves with every chunk, and a run saved by a build with a different
 * `EngineConfig` must cost the player that run, not the app its launch.
 *
 * Two things can make a blob unreadable, and both end here rather than at the
 * launch path: it does not parse, or its [SavedRun.version] is not
 * [SAVE_FORMAT_VERSION]. See [SavedRun] for why the version is checked rather
 * than trusted to `ignoreUnknownKeys`.
 *
 * There were two slots, one per `GameMode`, because abandoning a Daily attempt
 * and starting an Endless run used to overwrite the blob and spend the day with
 * it. D27 removed the Daily, so there is one run a player can have in flight and
 * one slot to keep it in.
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
            ?.takeIf { it.version == SAVE_FORMAT_VERSION }
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
         * `ignoreUnknownKeys` so a stale field is a version mismatch to be
         * refused deliberately rather than a decode crash on the launch path.
         * It is not the invalidation mechanism; [SavedRun.version] is.
         */
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
