package com.dangerfield.drop2048.libraries.ads.impl

import com.dangerfield.drop2048.libraries.ads.AdFormat
import com.dangerfield.drop2048.libraries.ads.AdNetwork
import com.dangerfield.drop2048.libraries.ads.AdShowOutcome
import com.dangerfield.drop2048.libraries.ads.AdShowResult
import com.dangerfield.drop2048.libraries.ads.HouseAds
import com.dangerfield.drop2048.libraries.ads.RunActivity
import com.dangerfield.drop2048.libraries.billing.Entitlements
import com.dangerfield.drop2048.libraries.billing.PurchaseOutcome
import com.dangerfield.drop2048.libraries.billing.RestoreOutcome
import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.drop2048.Session
import com.dangerfield.drop2048.libraries.drop2048.SessionStartReason
import com.dangerfield.drop2048.libraries.drop2048.SessionTracker
import com.dangerfield.drop2048.libraries.storage.Cache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The network, as something a test can interrogate rather than mock.
 *
 * What it is built to record is **what reached the SDK**, in order, because most
 * of what the gates do is decline to show things — so nearly every assertion is
 * about the contents of [shown] rather than about a return value, and a gate
 * that showed everything unconditionally has to fail them.
 */
class FakeAdNetwork(
    private var rewardedResult: AdShowResult = AdShowResult.Rewarded,
    private var interstitialResult: AdShowResult = AdShowResult.Dismissed,
) : AdNetwork {

    /** Every format that actually reached `show`, in order. */
    val shown = mutableListOf<AdFormat>()
    val preloaded = mutableSetOf<AdFormat>()

    var prepareCalls = 0
        private set

    /** What [isReady] answers. Nothing is loaded until a test says so. */
    var ready = mutableSetOf<AdFormat>()

    /** Models an SDK that blows up, which must never escape the gates. */
    var throwOnShow = false

    fun answersRewarded(result: AdShowResult) {
        rewardedResult = result
    }

    override suspend fun prepare() {
        prepareCalls++
    }

    override suspend fun show(format: AdFormat): AdShowOutcome {
        shown += format
        if (throwOnShow) error("ad sdk exploded")
        return AdShowOutcome(
            when (format) {
                AdFormat.Rewarded -> rewardedResult
                AdFormat.Interstitial -> interstitialResult
            }
        )
    }

    override fun isReady(format: AdFormat): Boolean = format in ready

    override fun preload(format: AdFormat) {
        preloaded += format
    }
}

/**
 * A [HouseAds] a test can hand a network to, standing in for the module a
 * release build does not contain.
 *
 * `Surface` is not overridden with anything: the seam's composable half is the
 * drawing, and what the gates care about is only which network they end up
 * talking to.
 */
class TestHouseAds(
    override val network: AdNetwork? = null,
    selected: Boolean = network != null,
) : HouseAds {
    private val selectedState = MutableStateFlow(selected)
    override val isSelected: StateFlow<Boolean> = selectedState.asStateFlow()

    override fun select(useHouseAds: Boolean) {
        selectedState.value = useHouseAds
    }

    override val forcedOutcome: StateFlow<AdShowResult?> = MutableStateFlow(null)

    override fun forceOutcome(result: AdShowResult?) = Unit

    override val reportsReady: StateFlow<Boolean> = MutableStateFlow(true)

    override fun reportReady(ready: Boolean) = Unit

    @androidx.compose.runtime.Composable
    override fun Surface() = Unit
}

class FakeEntitlements(pro: Boolean = false) : Entitlements {
    private val state = MutableStateFlow(pro)

    override val isPro: StateFlow<Boolean> = state.asStateFlow()

    fun becomes(pro: Boolean) {
        state.value = pro
    }

    override suspend fun purchasePro(trigger: String?): PurchaseOutcome = PurchaseOutcome.Unavailable

    override suspend fun restore(): RestoreOutcome = RestoreOutcome.Unavailable
}

class FakeRunActivity(alive: Boolean = false) : RunActivity {
    override var isRunAlive: Boolean = alive
        private set

    override fun runStarted() {
        isRunAlive = true
    }

    override fun runEnded() {
        isRunAlive = false
    }
}

/** A session that never rolls unless a test rolls it. */
class FakeSessionTracker : SessionTracker {
    private val state = MutableStateFlow(
        Session(id = 1, startedAtMs = 0, reason = SessionStartReason.ColdBoot, uuid = "test")
    )

    override val current: Session get() = state.value

    override fun observe(): Flow<Session> = state

    fun roll() {
        state.value = state.value.copy(id = state.value.id + 1)
    }
}

/** An in-memory [AdStateCache]; the gates only ever read and rewrite one value. */
class FakeAdStateCache(initial: AdState = AdState()) : AdStateCache {
    private val state = MutableStateFlow(initial)

    override val updates: Flow<AdState> = state

    override suspend fun get(): AdState = state.value

    override suspend fun set(value: AdState) {
        state.value = value
    }

    override suspend fun clear() {
        state.value = AdState()
    }
}

/**
 * A clock a test can move, because every rule in SPEC 12.3 that is not a
 * counter is a duration.
 */
@OptIn(ExperimentalTime::class)
class MovableClock(private var instant: Instant) : Clock {
    override fun now(): Instant = instant

    fun advance(millis: Long) {
        instant = Instant.fromEpochMilliseconds(instant.toEpochMilliseconds() + millis)
    }
}

/**
 * Config built from dotted paths, so a test's intent
 * (`"ads.interstitial.cooldownSeconds" to 0`) stays one line. An absent key
 * resolves to the compiled default, which is the same path a device with the
 * server unreachable takes.
 */
class TestAdConfigMap(overrides: Map<String, Any?> = emptyMap()) : AppConfigMap() {

    override val map: Map<String, *> = overrides.entries.fold(emptyMap<String, Any?>()) { acc, entry ->
        acc.merge(entry.key.split('.'), entry.value)
    }

    private fun Map<String, Any?>.merge(path: List<String>, value: Any?): Map<String, Any?> {
        val head = path.first()
        if (path.size == 1) return this + (head to value)
        @Suppress("UNCHECKED_CAST")
        val child = (this[head] as? Map<String, Any?>) ?: emptyMap()
        return this + (head to child.merge(path.drop(1), value))
    }
}
