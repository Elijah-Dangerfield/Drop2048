package com.dangerfield.drop2048.libraries.devfeedback

import com.dangerfield.drop2048.libraries.storage.Cache
import com.dangerfield.drop2048.libraries.storage.CacheFactory
import com.dangerfield.drop2048.libraries.storage.versionedJsonSerializer
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Where the floating directive button sits, and whether it is there at all.
 *
 * Its own cache rather than two more fields on `AppData`, because `AppData` is
 * the player's blob and this is developer machinery. A field there would ride
 * along in every player's install, and in every migration of it, for a button
 * they can never see.
 *
 * It is on disk at all because the button is draggable, and a position that
 * resets on every launch puts it back over the board the owner moved it off.
 *
 * It lives in this module rather than in `:libraries:devfeedback:tester` for one
 * reason: the QA screen writes [DevFeedbackFabState.hidden], and the QA screen
 * has to work on a build the tester module is not linked into. There is nothing
 * unsafe about a release build carrying two floats and a boolean nothing reads.
 */
interface DevFeedbackFabCache : Cache<DevFeedbackFabState>

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DevFeedbackFabCache::class)
@Inject
class DevFeedbackFabCacheImpl(
    cacheFactory: CacheFactory,
) : DevFeedbackFabCache, Cache<DevFeedbackFabState> by cacheFactory.persistent(
    name = "dev_feedback_fab",
    serializer = versionedJsonSerializer(
        defaultValue = { DevFeedbackFabState() },
    ),
)

/**
 * [x] and [y] are fractions of the button's *travel*: the container minus the
 * button, so `0f` is flush left or top and `1f` flush right or bottom.
 *
 * Fractions rather than pixels because the same install rotates, splits and runs
 * on a tablet. A stored pixel offset that was against the right edge in portrait
 * is somewhere in the middle in landscape, and one stored on a tablet would be
 * lost off the edge of a phone.
 */
@Serializable
data class DevFeedbackFabState(
    val hidden: Boolean = false,
    val x: Float = DefaultFabPlacement.x,
    val y: Float = DefaultFabPlacement.y,
) {
    /**
     * The stored position, made safe to place.
     *
     * Coerced on the way out rather than trusted, because these two floats are
     * the only thing standing between a bad write and a button parked off screen
     * — and the switch that would hide it again is behind a menu. `NaN` takes
     * the default rather than a clamp, since `NaN.coerceIn` is still `NaN`.
     */
    val placement: FabPlacement
        get() = FabPlacement(
            x = if (x.isFinite()) x.coerceIn(0f, 1f) else DefaultFabPlacement.x,
            y = if (y.isFinite()) y.coerceIn(0f, 1f) else DefaultFabPlacement.y,
        )

    fun withPlacement(placement: FabPlacement): DevFeedbackFabState =
        copy(x = placement.x, y = placement.y)
}

/**
 * Where the button sits, as a fraction of how far it can travel.
 *
 * Two plain floats and no Compose types, so that this module — which every build
 * contains — needs nothing from `compose.ui`. The pixel arithmetic that turns
 * one of these into an offset lives beside the button, in the module only a
 * tester build has.
 */
data class FabPlacement(val x: Float, val y: Float)

/**
 * Right edge, low enough to clear a top bar and high enough to clear a bottom
 * one. On Drop 2048's board that is beside the well rather than over it.
 *
 * Only ever the starting guess. The point of the button is that it moves, so
 * this needs to be somewhere reachable rather than somewhere permanently right.
 */
val DefaultFabPlacement: FabPlacement = FabPlacement(x = 1f, y = 0.62f)
