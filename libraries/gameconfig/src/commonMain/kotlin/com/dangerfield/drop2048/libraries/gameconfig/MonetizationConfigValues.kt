package com.dangerfield.drop2048.libraries.gameconfig

import com.dangerfield.drop2048.libraries.config.AppConfigMap
import com.dangerfield.drop2048.libraries.config.FlagConfigValue
import com.dangerfield.drop2048.libraries.config.IntConfigValue
import com.dangerfield.drop2048.libraries.config.QaConfigValue
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * SPEC 10's ad, Pro and kill-switch keys.
 *
 * Every default is the number SPEC 12 states, so the compiled-in behaviour with
 * the server unreachable is the behaviour SPEC 12 describes.
 *
 * ## Declaring a key ahead of its reader is over
 *
 * This file used to open by saying these keys had no consumer yet and that this
 * was the point: `:libraries:ads`, `:libraries:billing`, the Daily Challenge and
 * the leaderboards all landed after C7, and declaring the paths early meant each
 * chunk read a value that already existed rather than inventing one under
 * deadline. Every one of those chunks has now landed.
 *
 * What the practice cost is recorded in D25. Two keys stayed unread after their
 * feature shipped, and neither could be seen: a `ConfiguredValue` with no
 * injection site compiles, passes its own unit test, appears in the QA menu, and
 * is editable in the admin console. `feature.leaderboards` was a kill switch
 * that killed nothing for a full release; `pro.price.tier` was deleted outright,
 * because by the time anyone read it the id it defaulted to had stopped being
 * the product id and wiring it would have revoked Pro from paying players.
 *
 * `ConfigValuesHaveReadersTest` in `androidUnitTest` now fails on a key with no
 * production reader. **Add the reader in the same change as the key**, or do not
 * add the key.
 */

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class AdsEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Ads enabled"
    override val description = "Master kill switch for all advertising, rewarded included."
    override val path = "ads.enabled"
    override val default = true
}

/**
 * SPEC 12.3's first gate: no interstitial before this many runs this session.
 * Four means the 4th run of a session is the earliest one that can carry an ad.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class InterstitialMinSessionRuns(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Interstitial: earliest run of a session"
    override val description = "SPEC 12: never before the 4th run of a session."
    override val path = "ads.interstitial.minSessionRuns"
    override val default = 4
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class InterstitialCooldownSeconds(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Interstitial: cooldown (seconds)"
    override val description = "Minimum gap since the last interstitial. SPEC 12: 180s."
    override val path = "ads.interstitial.cooldownSeconds"
    override val default = 180
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class InterstitialRewardedGapSeconds(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Interstitial: gap around a rewarded ad (seconds)"
    override val description = "Never within this of a rewarded ad, in either direction. SPEC 12: 45s."
    override val path = "ads.interstitial.rewardedGapSeconds"
    override val default = 45
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class InterstitialSuppressDaysSinceInstall(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Interstitial: suppress for days after install"
    override val description = "Suppressed entirely for this many days after install. SPEC 12: 3."
    override val path = "ads.interstitial.suppressDaysSinceInstall"
    override val default = 3
}

/** SPEC 12's Continue placement: 1 free per run, a 2nd at higher friction, hard cap 2. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class RewardedContinuesPerRun(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Rewarded cap: continues per run"
    override val description = "Hard cap on rewarded continues in one run. SPEC 12: 2."
    override val path = "ads.rewarded.continuesPerRun"
    override val default = 2
}

/** SPEC 12's Daily retry placement: one extra attempt, once a day. */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class RewardedDailyRetriesPerDay(appConfigMap: AppConfigMap) : IntConfigValue(appConfigMap) {
    override val name = "Rewarded cap: Daily retries per day"
    override val description = "Rewarded Daily Challenge retries per UTC day. SPEC 12: 1."
    override val path = "ads.rewarded.dailyRetriesPerDay"
    override val default = 1
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class ProUpsellEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Pro upsell enabled"
    override val description = "The Settings entry and the once-per-session stacked-out card. SPEC 12."
    override val path = "pro.upsell.enabled"
    override val default = true
}

/**
 * SPEC 10's kill switches for anything with a server or platform dependency.
 *
 * Off means the entry point is not offered, not that a broken screen is shown.
 * Both default **on**: an unreachable server must leave the game exactly as the
 * binary ships it, and a fallback that silently disabled features would make the
 * offline path a different product.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class DailyChallengeEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Daily Challenge enabled"
    override val description = "Kill switch for SPEC 14. Off hides the entry point."
    override val path = "feature.dailyChallenge"
    override val default = true
}

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = QaConfigValue::class, multibinding = true)
class LeaderboardsEnabled(appConfigMap: AppConfigMap) : FlagConfigValue(appConfigMap) {
    override val name = "Leaderboards enabled"
    override val description = "Kill switch for Game Center / Play Games boards. Off hides the entry point."
    override val path = "feature.leaderboards"
    override val default = true
}
