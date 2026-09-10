package com.dangerfield.drop2048.libraries.progress.impl.daily

import com.dangerfield.drop2048.libraries.progress.daily.DailyRetryAd
import com.dangerfield.drop2048.libraries.progress.daily.DeviceTimeZone
import com.dangerfield.drop2048.libraries.progress.daily.ProEntitlement
import com.dangerfield.drop2048.libraries.progress.daily.RewardOutcome
import kotlinx.datetime.TimeZone
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The device's zone, read on every call.
 *
 * A singleton that caches nothing: `currentSystemDefault()` is the one thing
 * here that is allowed to change under us, and holding onto it is the bug this
 * class exists not to have.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class SystemDeviceTimeZone : DeviceTimeZone {
    override fun current(): TimeZone = TimeZone.currentSystemDefault()
}

/**
 * Pro until C10 ships billing: nobody has it.
 *
 * The binding is replaced then, and the second Daily attempt SPEC 12 promises
 * starts working without a line changing in `DailyRepositoryImpl`.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoEntitlementsYet : ProEntitlement {
    override fun isPro(): Boolean = false
}

/**
 * The rewarded Daily retry until C10 ships ads: never available.
 *
 * [RewardOutcome.Unavailable] rather than [RewardOutcome.Dismissed] and that
 * distinction is the whole seam. Dismissed means the player said no and is the
 * only answer that withholds a reward; unavailable means the app could not ask,
 * which is also what a network with no fill will answer. Getting this wrong now
 * would bake "no fill costs you the retry" into the rule before the ad system
 * exists to argue with it.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoRetryAdYet : DailyRetryAd {
    override suspend fun show(): RewardOutcome = RewardOutcome.Unavailable
}
