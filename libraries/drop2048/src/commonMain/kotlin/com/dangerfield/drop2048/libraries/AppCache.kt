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

    /** SPEC 6's landing outline. On by default, toggleable. */
    val ghostEnabled: Boolean = true,

    /**
     * SPEC 16's palette choice and SPEC 9's haptic strength, by enum **name**.
     *
     * Strings rather than the enums themselves for the same reason [savedRun] is
     * a string: `AppData` is one serialized blob, and a field that fails to
     * decode takes the install id and the onboarding flag down with it. An enum
     * renamed or removed in `:libraries:ui` would do exactly that. Decoded
     * leniently on read, so an unknown name falls back to the default palette
     * instead of losing the player's whole record over a colour.
     *
     * It also keeps `:libraries:drop2048` free of a dependency on the design
     * system, which it has never had.
     */
    val blockPalette: String? = null,
    val hapticsSetting: String? = null,

    /** SPEC 6's control scheme, by enum name. Leniently decoded, as above. */
    val controlScheme: String? = null,

    /** SPEC 16's larger numerals on block faces. */
    val largeBlockNumbers: Boolean = false,

    val soundEnabled: Boolean = true,

    /**
     * SPEC 17. Off by default: diagnostics attached to feedback are opt-in.
     *
     * Written from the feedback and bug-report forms, which is where the player
     * meets it. The owner ruling of 2026-09-20 took the duplicate switch off the
     * settings screen; the preference itself is unchanged and still the only
     * thing that lets a session log leave with a player's report.
     */
    val diagnosticsOptIn: Boolean = false,

    /** Seven taps on the version number (C12). Persisted so it survives a launch. */
    val debugMenuUnlocked: Boolean = false,

    /**
     * SPEC 12's one-time Pro purchase, cached so the first frame of the app does
     * not render as a free player while the store is being asked.
     *
     * A fact about the player rather than machinery, which is why it is here
     * and the ad-frequency timestamps are in their own cache. It is only ever
     * cleared by a store that explicitly said "not owned" — see
     * `RealEntitlements`.
     */
    val isProEntitled: Boolean = false,

    /**
     * The legal record the launch gates measure against.
     *
     * [legalAcceptedAt] being 0 means this device has never recorded an
     * acceptance, which is what makes the blocking re-accept gate unreachable on
     * a fresh install whatever `legal.forceReacceptBelow` says: a first launch
     * has nothing to be out of date *against*.
     */
    val acceptedTermsVersion: Int = 0,
    val acceptedPrivacyVersion: Int = 0,
    val legalAcceptedAt: Long = 0L,

    /**
     * The soft-update banner, dismissed at a version rather than forever. Raising
     * `upgrade.softUpdateVersionCode` asks again.
     */
    val softUpdateDismissedFor: Int = 0,

    /**
     * Epoch-ms of the first foreground this install ever had, and the milestone
     * days already reported. Together they are SPEC 17's day 1 / 3 / 7 return
     * funnel (`RetentionReporter` in `:libraries:telemetry:impl`).
     *
     * Deliberately **not** reusing [reviewInstallAt]. That belongs to the review
     * coordinator, which is free to reset or re-stamp it for its own reasons,
     * and a retention curve quietly rebased by an unrelated prompt policy is
     * the kind of wrong number nobody catches.
     *
     * The reported set is what makes the events once-per-milestone rather than
     * once-per-foreground. A player who opens the app nine times on day 3 is
     * one day-3 return, and without this it would be nine.
     */
    val firstLaunchAt: Long = 0L,
    val returnDaysReported: Set<Int> = emptySet(),

    /**
     * Whether the player has ever finished a run (SPEC 17's funnel). Written at
     * `endRun`, so it counts a run *played to the end* rather than one that was
     * started — the funnel question is whether a new player ever reaches the
     * stacked-out sheet, and every abandoned run is one who did not.
     *
     * A flag rather than the "epoch-ms, 0 means never" shape used elsewhere in
     * this class, because there is exactly one bit of information here and the
     * sentinel is a trap: a clock reading zero is indistinguishable from "not
     * yet", so a device with an unset clock would report a first run on every
     * run it ever played.
     */
    val hasCompletedARun: Boolean = false,
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
