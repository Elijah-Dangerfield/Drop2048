package com.dangerfield.drop2048.libraries.ads.impl

import com.dangerfield.drop2048.libraries.drop2048.SessionTracker
import com.dangerfield.drop2048.libraries.storage.Cache
import com.dangerfield.drop2048.libraries.storage.CacheFactory
import com.dangerfield.drop2048.libraries.storage.versionedJsonSerializer
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The ad-frequency bookkeeping that has to survive a force-quit.
 *
 * A separate cache from `AppData` on purpose. `AppData` is settings and counters
 * a person could recognise — haptics, the saved run, the palette — and this is
 * machinery: three timestamps nobody would ever want to read, two of which are
 * meaningless without the config values they are compared against. Keeping them
 * apart also means the ad layer can be reasoned about, and force-reset in QA,
 * without touching the file the whole app writes to.
 *
 * The **entitlement** is not here. That is a fact about the player and it lives
 * behind `Entitlements`, which asks the store.
 *
 * Per-*session* counters are deliberately not here either: a run count that
 * survived a restart would let a player who force-quits twice see an interstitial
 * on their first run of the day, which is the exact opposite of what SPEC 12's
 * fourth-run rule is for. Those live in [AdSession], in memory.
 */
@Serializable
data class AdState(
    /**
     * Epoch-ms of the first launch the ad layer ever saw. SPEC 12's three-day
     * install suppression counts from here.
     *
     * An install that predates ads reads its *first launch with this build* as
     * day zero, so an existing player gets three more ad-free days. That is the
     * safe direction and it self-corrects in three days; the alternative — a
     * best-guess install date — would be a number with no source that could
     * only ever be wrong in the direction of showing ads too early.
     */
    val firstSeenAtMs: Long = 0L,

    /** Epoch-ms of the last interstitial actually shown. 0 = never. */
    val lastInterstitialAtMs: Long = 0L,

    /**
     * Epoch-ms of the last rewarded ad that reached the network, whatever it
     * answered. 0 = never.
     *
     * Recorded on dismissal and on failure as well as on a reward, because SPEC
     * 12's 45-second rule is about what the *player* just sat through, and
     * sitting through five seconds of a rewarded ad and closing it is still five
     * seconds of advertising.
     */
    val lastRewardedAtMs: Long = 0L,

    /**
     * Epoch-ms of the last banner that reported a fill and was therefore on the
     * player's screen. 0 = never.
     *
     * Here rather than in memory because it answers [AdImpressions], which is
     * what the Pro upsell card is gated on since D28, and "tired of the ads" is
     * a thing a player stays after a restart. It is **not** a frequency gate:
     * nothing reads the value, only whether it is zero. It is a timestamp anyway
     * so it matches its three neighbours and so a future question about recency
     * has an answer rather than a migration.
     */
    val lastBannerAtMs: Long = 0L,
)

interface AdStateCache : Cache<AdState>

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AdStateCache::class)
@Inject
class AdStateCacheImpl(
    cacheFactory: CacheFactory,
) : AdStateCache, Cache<AdState> by cacheFactory.persistent(
    name = "ad_state",
    serializer = versionedJsonSerializer(
        defaultValue = { AdState() },
    ),
)

/**
 * SPEC 12's "not before the 4th run of a session", and the reason it is counted
 * in memory.
 *
 * A ceiling that survived a process restart would mean a player who force-quits
 * twice on a bad day never sees another interstitial, and a player who leaves the
 * app open for a week never stops seeing them. [SessionTracker] already owns the
 * definition of a session — cold boot, or a foreground after fifteen minutes
 * away — so this reads its id rather than inventing a second answer.
 *
 * The id is compared lazily instead of collected: there is no work to do at a
 * session boundary except forget a number, and a collector would be a coroutine
 * that exists to set a field to zero.
 */
@SingleIn(AppScope::class)
@Inject
class AdSession(
    private val sessionTracker: SessionTracker,
) {
    private var observedSessionId: Long = 0L
    private var runsFinished: Int = 0

    /** How many runs have ended in the session in progress. */
    fun runsFinished(): Int {
        rollIfNeeded()
        return runsFinished
    }

    fun noteRunFinished() {
        rollIfNeeded()
        runsFinished++
    }

    private fun rollIfNeeded() {
        val current = sessionTracker.current.id
        if (current != observedSessionId) {
            observedSessionId = current
            runsFinished = 0
        }
    }
}
