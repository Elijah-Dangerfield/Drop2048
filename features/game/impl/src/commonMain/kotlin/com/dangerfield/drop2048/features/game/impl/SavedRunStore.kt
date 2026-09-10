package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.progress.GameMode
import kotlinx.serialization.json.Json
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The one value SPEC 11 keeps out of Room, and the only thing that knows its
 * format.
 *
 * `AppData.savedRun` and `AppData.savedDailyRun` are strings; this is where they
 * become a [SavedRun] and back. Keeping the codec here and not in the ViewModel
 * is what lets [load] treat a blob it cannot read as "no saved run" — the
 * engine's serial shape moves with every chunk, and a run saved by a build with a
 * different `EngineConfig` must cost the player that run, not the app its launch.
 *
 * Three things can make a blob unreadable, and all three end here rather than at
 * the launch path: it does not parse, its [SavedRun.version] is not
 * [SAVE_FORMAT_VERSION], or its [SavedRun.mode] is not the mode of the slot it
 * was read out of. See [SavedRun] for why the version is checked rather than
 * trusted to `ignoreUnknownKeys`.
 *
 * ### Why there are two slots
 *
 * There was one, and it lost a Daily attempt. Abandoning a Daily and starting an
 * Endless run overwrote the blob, so the attempt was gone **and the day was
 * spent** — SPEC 14 allows one attempt a day and nothing gives it back. Endless
 * and the Daily are two runs a player is genuinely allowed to have in flight at
 * once, so they get a slot each.
 */
interface SavedRunStore {
    suspend fun load(mode: GameMode): SavedRun?
    suspend fun save(run: SavedRun)
    suspend fun clear(mode: GameMode)

    /** Both slots, for the tutorial and for "reset progress". */
    suspend fun clearAll()
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AppCacheSavedRunStore(
    private val appCache: AppCache,
) : SavedRunStore {

    override suspend fun load(mode: GameMode): SavedRun? {
        val stored = Catching { appCache.get().slot(mode) }
            .logOnFailure { "Could not read the saved run" }
            .getOrNull()
            ?: return null
        return Catching { json.decodeFromString(SavedRun.serializer(), stored) }
            .logOnFailure { "Saved run could not be decoded; starting fresh" }
            .getOrNull()
            ?.takeIf { it.version == SAVE_FORMAT_VERSION && it.mode == mode }
    }

    override suspend fun save(run: SavedRun) {
        val encoded = Catching { json.encodeToString(SavedRun.serializer(), run) }
            .logOnFailure { "Could not encode the run in progress" }
            .getOrNull()
            ?: return
        Catching { appCache.update { it.withSlot(run.mode, encoded) } }
            .logOnFailure { "Could not persist the run in progress" }
    }

    override suspend fun clear(mode: GameMode) {
        Catching { appCache.update { it.withSlot(mode, null) } }
            .logOnFailure { "Could not clear the saved run" }
    }

    override suspend fun clearAll() {
        Catching { appCache.update { it.copy(savedRun = null, savedDailyRun = null) } }
            .logOnFailure { "Could not clear the saved runs" }
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

private fun AppData.slot(mode: GameMode): String? = when (mode) {
    GameMode.DAILY -> savedDailyRun
    GameMode.ENDLESS -> savedRun
}

private fun AppData.withSlot(mode: GameMode, blob: String?): AppData = when (mode) {
    GameMode.DAILY -> copy(savedDailyRun = blob)
    GameMode.ENDLESS -> copy(savedRun = blob)
}
