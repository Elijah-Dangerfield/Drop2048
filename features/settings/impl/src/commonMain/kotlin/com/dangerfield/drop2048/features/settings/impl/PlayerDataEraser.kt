package com.dangerfield.drop2048.features.settings.impl

import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ClearableDao
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The two destructive controls on the settings screen, and the difference
 * between them.
 *
 * **This is the consumer `Set<ClearableDao>` was waiting for.** C4 declared the
 * multibinding, exposed it on `AppComponent` and read the generated code to
 * prove both DAOs were in the set, precisely because a multibinding nothing
 * reads is never validated (L34). It has had no caller since. Injecting the set
 * rather than naming the DAOs means a table added in C6 or C9 is wiped by this
 * without anyone remembering to come back here.
 *
 * **Neither of these is `fallbackToDestructiveMigration`.** L33 narrowed that to
 * versions 1-4 so a *failed migration* from `FIRST_PLAYER_DATA_VERSION` on
 * crashes rather than silently deleting a player's history. What happens here is
 * the opposite thing wearing the same shape: a deliberate, double-confirmed
 * request from the player. Do not fold one into the other.
 */
@SingleIn(AppScope::class)
@Inject
class PlayerDataEraser(
    private val clearableDaos: Set<ClearableDao>,
    private val appCache: AppCache,
) {

    /**
     * "Reset progress": every table emptied, every run and stat gone.
     *
     * Settings, the legal record and the install id survive, because none of
     * them is progress and re-accepting the terms is not what the player asked
     * for. The in-flight run goes, since it is a run.
     *
     * Each DAO is cleared on its own [Catching] so one failing table cannot
     * leave the rest untouched — a half-refused reset is the worst outcome
     * available here, and the player has already confirmed twice.
     */
    suspend fun resetProgress() {
        clearableDaos.forEach { dao ->
            Catching { dao.deleteAll() }
                .logOnFailure { "Failed to clear a table during reset progress" }
        }
        Catching {
            appCache.update { data -> data.copy(savedRun = null, savedDailyRun = null) }
        }.logOnFailure { "Failed to clear the in-flight run during reset progress" }
    }

    /**
     * "Delete local data": the tables, and the record of this install with them.
     *
     * Everything except the settings the player is standing in — resetting a
     * palette out from under somebody who is mid-tap on "Delete" would look like
     * a bug rather than a deletion. A fresh [AppData] with the accessibility
     * choices carried across, and the next launch runs the tutorial again.
     */
    suspend fun deleteLocalData() {
        clearableDaos.forEach { dao ->
            Catching { dao.deleteAll() }
                .logOnFailure { "Failed to clear a table during delete local data" }
        }
        Catching {
            appCache.update { data ->
                AppData(
                    blockPalette = data.blockPalette,
                    hapticsSetting = data.hapticsSetting,
                    controlScheme = data.controlScheme,
                    reduceMotion = data.reduceMotion,
                    largeBlockNumbers = data.largeBlockNumbers,
                    soundEnabled = data.soundEnabled,
                    musicEnabled = data.musicEnabled,
                    leftHandedControls = data.leftHandedControls,
                    ghostEnabled = data.ghostEnabled,
                    confirmBeforeQuit = data.confirmBeforeQuit,
                    diagnosticsOptIn = data.diagnosticsOptIn,
                )
            }
        }.logOnFailure { "Failed to clear the local record during delete local data" }
    }
}
