package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.libraries.ads.AdDiagnostics
import com.dangerfield.drop2048.libraries.ads.AdGate
import com.dangerfield.drop2048.libraries.ads.AdGateSnapshot
import com.dangerfield.drop2048.libraries.ads.AdPlacement
import com.dangerfield.drop2048.libraries.ads.InterstitialGate
import com.dangerfield.drop2048.libraries.ads.RewardOutcome
import com.dangerfield.drop2048.libraries.config.ConfigOverride
import com.dangerfield.drop2048.libraries.config.ConfigOverrideRepository
import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.progress.daily.DailyAttempt
import com.dangerfield.drop2048.libraries.progress.daily.DailyRepository
import com.dangerfield.drop2048.libraries.progress.daily.DailyResult
import com.dangerfield.drop2048.libraries.progress.daily.DailyRetryResult
import com.dangerfield.drop2048.libraries.progress.daily.DailyStatus
import com.dangerfield.drop2048.libraries.progress.daily.DailyStreak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlin.time.Duration

/**
 * Hand-rolled, per `docs/practices/testing.md`, and only as much of each seam as
 * the debug menu touches: the streak it shows, and the reset it offers.
 */
internal class StubDailyRepository : DailyRepository {
    var resets = 0
        private set

    override fun observe(): Flow<DailyStatus> = MutableStateFlow(today)

    override suspend fun status(): DailyStatus = today

    override suspend fun startAttempt(): DailyAttempt = DailyAttempt.NoAttemptsLeft

    override suspend fun recordAttempt(date: LocalDate, score: Long) = Unit

    override suspend fun grantRetry(): DailyRetryResult = DailyRetryResult.Unavailable

    override suspend fun history(): List<DailyResult> = emptyList()

    override suspend fun reset() {
        resets += 1
    }

    private val today = DailyStatus(
        date = LocalDate(2026, 9, 9),
        seed = 1,
        result = null,
        streak = DailyStreak(current = 3, best = 5),
        attemptsAllowed = 1,
        retryOffered = false,
        resetsIn = Duration.ZERO,
        enabled = true,
    )
}

internal class StubAppCache(initial: AppData = AppData()) : AppCache {
    private val state = MutableStateFlow(initial)

    val value: AppData get() = state.value

    override val updates: Flow<AppData> = state

    override suspend fun get(): AppData = state.value

    override suspend fun set(value: AppData) {
        state.value = value
    }

    override suspend fun clear() {
        state.value = AppData()
    }
}

/** Records which placement was asked for, and answers whatever a test wants. */
internal class StubAdGate(
    var outcome: RewardOutcome = RewardOutcome.Rewarded,
) : AdGate {
    val shown = mutableListOf<AdPlacement>()

    override suspend fun showRewarded(placement: AdPlacement): RewardOutcome {
        shown += placement
        return outcome
    }

    override fun preload(placement: AdPlacement) = Unit
}

/**
 * The interstitial gate, as something a test can interrogate.
 *
 * [runsFinished] is here because the menu's "count a finished run" is the only
 * control in it that feeds a *production* counter rather than a debug one, and
 * the failure worth catching is it quietly doing nothing.
 */
internal class StubInterstitialGate(var showsOne: Boolean = false) : InterstitialGate {
    var runsFinished = 0
        private set
    var showAttempts = 0
        private set

    override fun noteRunFinished() {
        runsFinished += 1
    }

    override suspend fun showIfReady(): Boolean {
        showAttempts += 1
        return showsOne
    }

    override fun noteRewardedShown() = Unit

    override fun preload() = Unit
}

/** A snapshot a test can set, so the menu's readout has something to be wrong about. */
internal class StubAdDiagnostics(var snapshot: AdGateSnapshot = Blocked) : AdDiagnostics {
    override suspend fun snapshot(): AdGateSnapshot = snapshot

    companion object {
        val Blocked = AdGateSnapshot(
            adsEnabled = true,
            isPro = false,
            runAlive = false,
            runsThisSession = 1,
            minSessionRuns = 4,
            secondsSinceLastInterstitial = null,
            cooldownSeconds = 180,
            secondsSinceRewarded = null,
            rewardedInFlight = false,
            rewardedGapSeconds = 45,
            daysSinceInstall = 0,
            suppressDaysSinceInstall = 3,
            interstitialPreloaded = true,
            blockedReason = "new_install",
            networkName = "house",
        )
    }
}

/** In-memory overrides, which is what the repository is on a device anyway. */
internal class StubConfigOverrideRepository : ConfigOverrideRepository {
    private val state = MutableStateFlow<List<ConfigOverride<Any>>>(emptyList())

    override fun getOverrides(): List<ConfigOverride<Any>> = state.value

    override fun getOverridesFlow(): Flow<List<ConfigOverride<Any>>> = state

    override suspend fun addOverride(override: ConfigOverride<Any>) {
        state.value = state.value.filter { it.path != override.path } + override
    }

    override suspend fun removeOverride(path: String) {
        state.value = state.value.filter { it.path != path }
    }

    override suspend fun clearAll() {
        state.value = emptyList()
    }
}
