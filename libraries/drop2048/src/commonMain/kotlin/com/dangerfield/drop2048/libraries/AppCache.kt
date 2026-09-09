package com.dangerfield.drop2048.libraries.drop2048

import com.dangerfield.drop2048.libraries.storage.Cache
import com.dangerfield.drop2048.libraries.storage.CacheFactory
import com.dangerfield.drop2048.libraries.storage.versionedJsonSerializer
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * In-memory + persistent cache for app-wide state that doesn't need to be in the database.
 */
@Serializable
data class AppData(
    // Onboarding
    val hasUserOnboarded: Boolean = false,

    /**
     * Stable per-install identifier, minted on first read and persisted for
     * the app's lifetime on this device (survives sign-out; dies with
     * uninstall). Sent as X-Install-Id on authenticated requests so the
     * server can associate anonymous accounts from the same install.
     * Stored as a string (UUID canonical form) so the JSON serializer
     * doesn't need a Uuid-aware adapter on every cache read.
     */
    val installId: String? = null,

    // Screen visits - automatically tracked for any TrackableRoute
    val screenVisits: Map<String, Int> = emptyMap(),

    // User actions
    val feedbacksGiven: Int = 0,
    val bugsReported: Int = 0,

    /** Epoch-ms — first observed by the review coordinator. 0 = uncaptured. */
    val reviewInstallAt: Long = 0L,

    /** Epoch-ms — last review prompt the coordinator forwarded to the platform. 0 = never. */
    val lastReviewPromptAt: Long = 0L,

    /**
     * The run in progress, serialized (SPEC 11).
     *
     * Here rather than in Room because it is one value, it is overwritten
     * constantly, and it is worthless the moment the run ends. Because the
     * engine is a pure function of state and a seed (SPEC 4.1), resuming is
     * deserializing one object.
     *
     * A **string**, not a typed field, for two reasons that both point the same
     * way. The shape belongs to `:features:game:impl` — it holds a `GameState`
     * and a playback snapshot, and typing it here would drag the engine into the
     * app-wide cache and make this module depend on a feature's private format.
     * More importantly it isolates the blast radius: the engine's serial shape
     * changes every chunk, and a nested field that fails to decode takes the
     * whole of `AppData` with it, losing the install id and the onboarding flag
     * over an abandoned run. A string decodes on its own, and a failure is
     * "no saved run" instead.
     */
    val savedRun: String? = null,

    /** SPEC 6's left-handed mirror. Moves to the settings screen in C11. */
    val leftHandedControls: Boolean = false,

    /** SPEC 6's landing outline. On by default, toggleable. Moves to settings in C11. */
    val ghostEnabled: Boolean = true,
) {
    /**
     * Get the visit count for a screen by its tracking key.
     */
    fun getVisitCount(trackingKey: String): Int = screenVisits[trackingKey] ?: 0
    
    /**
     * Increment the visit count for a screen.
     */
    fun incrementVisit(trackingKey: String): AppData = copy(
        screenVisits = screenVisits + (trackingKey to (getVisitCount(trackingKey) + 1))
    )
}

interface AppCache : Cache<AppData>

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AppCache::class)
@Inject
class AppCacheImpl(
    cacheFactory: CacheFactory
) : AppCache, Cache<AppData> by cacheFactory.persistent(
    name = "app_data",
    serializer = versionedJsonSerializer(
        defaultValue = { AppData() },
    )
)
