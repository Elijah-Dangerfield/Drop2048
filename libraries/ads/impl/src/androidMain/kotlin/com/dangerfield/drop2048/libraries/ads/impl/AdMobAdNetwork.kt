package com.dangerfield.drop2048.libraries.ads.impl

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.dangerfield.drop2048.libraries.ads.AdConsent
import com.dangerfield.drop2048.libraries.ads.AdFormat
import com.dangerfield.drop2048.libraries.ads.AdNetwork
import com.dangerfield.drop2048.libraries.ads.AdShowOutcome
import com.dangerfield.drop2048.libraries.ads.NoAdConsent
import com.dangerfield.drop2048.libraries.ads.NotWiredAdNetwork
import com.dangerfield.drop2048.libraries.ads.AdShowResult
import com.dangerfield.drop2048.libraries.ads.AdUnits
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.core.logging.KLog
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.DispatcherProvider
import com.dangerfield.drop2048.libraries.drop2048.ActivityProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * AdMob on Android, plus the UMP consent gate that has to run in front of it.
 *
 * ## Order, which is a policy requirement and not a preference
 *
 * [prepare] does consent **then** initialisation, and every show path awaits it
 * before touching the SDK. Requesting an ad before the UMP form has been
 * answered is a Google Play policy violation in the EEA and UK, and the failure
 * mode is a rejected release rather than a crash — so the ordering is enforced
 * here, once, rather than trusted to call sites.
 *
 * `canRequestAds()` is the gate rather than "did the form show": UMP answers
 * `true` for a user outside the EEA who was never shown anything, and `false`
 * for a user who declined. Reading the form's presence instead would block ads
 * for most of the world.
 *
 * The app is general-audience, so `TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE` is
 * set and `tagForUnderAgeOfConsent` is deliberately left unspecified. Setting
 * the latter turns off personalised ads for everyone, which is a revenue
 * decision nobody made.
 *
 * ## Nothing here throws
 *
 * Every failure becomes an [AdShowOutcome], because the rewarded gate above
 * turns those into a granted reward. An exception escaping this class would take
 * the same path — it is caught up there too — but it would arrive without the
 * kind, and the kind is the only thing that makes a no-fill spike diagnosable.
 *
 * ## It also answers the consent row in Settings
 *
 * [AdConsent] is implemented here rather than in a second class because UMP's
 * state lives in this SDK and `isConsentFormAvailable` is only meaningful after
 * `requestConsentInfoUpdate` has run — which is something [prepare] already
 * does, exactly once, behind a lock.
 */
@SingleIn(AppScope::class)
@ContributesBinding(
    scope = AppScope::class,
    boundType = AdNetwork::class,
    replaces = [NotWiredAdNetwork::class],
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = AdConsent::class,
    replaces = [NoAdConsent::class],
)
@Inject
class AdMobAdNetwork(
    private val context: Context,
    private val activityProvider: ActivityProvider,
    private val dispatchers: DispatcherProvider,
    private val appScope: AppCoroutineScope,
) : AdNetwork, AdConsent {

    private val logger = KLog.withTag("AdMob")
    private val prepareLock = Mutex()

    private var initialised = false

    private var rewarded: RewardedAd? = null
    private var interstitial: InterstitialAd? = null

    /**
     * Whether UMP has a form to show. False until [prepare] has asked, which is
     * why Settings draws no consent row on a launch where no ad has been
     * requested yet — an honest answer rather than a row that opens nothing.
     */
    private var consentFormAvailable = false

    override val isAvailable: Boolean get() = consentFormAvailable

    /**
     * Re-presents the form on demand. Both stores require a way back to it, and
     * the SDK's own `showPrivacyOptionsForm` is the only thing that can show it:
     * consent lives in UMP's storage, not in `AppData`, because that is where a
     * store audit will look for it.
     */
    override suspend fun present() {
        prepare()
        val activity = activityProvider.currentActivity() ?: return
        withContext(dispatchers.main) {
            suspendCancellableCoroutine { cont ->
                UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
                    if (error != null) logger.w { "Privacy options form failed: ${error.errorCode}" }
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    override suspend fun prepare() {
        if (initialised) return
        prepareLock.withLock {
            if (initialised) return
            Catching { consentThenInitialise() }
                .logOnFailure { "AdMob prepare failed; ads stay unavailable until the next attempt" }
        }
    }

    override suspend fun show(format: AdFormat): AdShowOutcome {
        prepare()
        if (!initialised) return AdShowOutcome(AdShowResult.NotShown, "sdk_not_ready")

        val activity = activityProvider.currentActivity()
            ?: return AdShowOutcome(AdShowResult.NotShown, "no_foreground_activity")

        return Catching {
            when (format) {
                AdFormat.Rewarded -> showRewarded(activity)
                AdFormat.Interstitial -> showInterstitial(activity)
            }
        }
            .logOnFailure { "AdMob show threw for $format" }
            .getOrElse { AdShowOutcome(AdShowResult.Failed, it::class.simpleName ?: "unknown") }
    }

    /**
     * SPEC 12.3's "skipped silently if not ready", answered without a round
     * trip. A cached ad object is the whole test — AdMob hands one over only
     * once it is showable.
     */
    override fun isReady(format: AdFormat): Boolean = when (format) {
        AdFormat.Rewarded -> rewarded != null
        AdFormat.Interstitial -> interstitial != null
    }

    override fun preload(format: AdFormat) {
        appScope.launch {
            prepare()
            if (!initialised) return@launch
            Catching {
                when (format) {
                    AdFormat.Rewarded -> if (rewarded == null) rewarded = loadRewarded().getOrNull()
                    AdFormat.Interstitial ->
                        if (interstitial == null) interstitial = loadInterstitial().getOrNull()
                }
            }.logOnFailure { "AdMob preload failed for $format" }
        }
    }

    private suspend fun consentThenInitialise() {
        val activity = activityProvider.currentActivity()
        if (activity == null) {
            // Backgrounded, or mid-rotation. Staying un-initialised is correct:
            // the next request retries, and until then every ad "fails", which
            // this app already pays the player for.
            logger.i { "No foreground Activity; deferring consent and AdMob init" }
            return
        }

        val consent = UserMessagingPlatform.getConsentInformation(context)
        withContext(dispatchers.main) {
            requestConsentInfoUpdate(activity, consent)
            consentFormAvailable = consent.isConsentFormAvailable
            if (consentFormAvailable) loadAndShowConsentForm(activity)
        }

        if (!consent.canRequestAds()) {
            logger.i { "UMP says ads may not be requested; leaving the SDK un-initialised" }
            return
        }

        withContext(dispatchers.io) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTagForChildDirectedTreatment(
                        RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE,
                    )
                    .build(),
            )
            suspendCancellableCoroutine { cont ->
                MobileAds.initialize(context) { if (cont.isActive) cont.resume(Unit) }
            }
        }
        initialised = true
        logger.i { "AdMob initialised" }
    }

    private suspend fun requestConsentInfoUpdate(activity: Activity, consent: ConsentInformation) {
        withTimeoutOrNull(CONSENT_TIMEOUT) {
            suspendCancellableCoroutine { cont ->
                consent.requestConsentInfoUpdate(
                    activity,
                    ConsentRequestParameters.Builder().build(),
                    { if (cont.isActive) cont.resume(Unit) },
                    { error ->
                        logger.w { "Consent info update failed: ${error.errorCode} ${error.message}" }
                        if (cont.isActive) cont.resume(Unit)
                    },
                )
            }
        }
    }

    private suspend fun loadAndShowConsentForm(activity: Activity) {
        withTimeoutOrNull(CONSENT_TIMEOUT) {
            suspendCancellableCoroutine { cont ->
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) logger.w { "Consent form failed: ${error.errorCode} ${error.message}" }
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    private suspend fun showRewarded(activity: Activity): AdShowOutcome {
        val ad = rewarded ?: loadRewarded().getOrElse { return it.toOutcome() }
        rewarded = null

        var earned = false
        val dismissal = withContext(dispatchers.main) {
            suspendCancellableCoroutine { cont ->
                ad.fullScreenContentCallback = resumeOnceCallback(cont::isActive) { cont.resume(it) }
                ad.show(activity, OnUserEarnedRewardListener { earned = true })
            }
        }
        preload(AdFormat.Rewarded)

        return when {
            dismissal is Dismissal.Failed -> AdShowOutcome(AdShowResult.Failed, dismissal.kind)
            earned -> AdShowOutcome(AdShowResult.Rewarded)
            else -> AdShowOutcome(AdShowResult.Dismissed)
        }
    }



    /**
     * The one ad the player did not ask for, and the only thing this class does
     * differently for it: **nothing**.
     *
     * Every rule about when an interstitial may appear is in
     * `InterstitialPolicy`, in common code, with a test per rule. Putting any of
     * it here would put half the policy on one platform.
     */
    private suspend fun showInterstitial(activity: Activity): AdShowOutcome {
        val ad = interstitial ?: loadInterstitial().getOrElse { return it.toOutcome() }
        interstitial = null

        val dismissal = withContext(dispatchers.main) {
            suspendCancellableCoroutine { cont ->
                ad.fullScreenContentCallback = resumeOnceCallback(cont::isActive) { cont.resume(it) }
                ad.show(activity)
            }
        }
        preload(AdFormat.Interstitial)

        return when (dismissal) {
            is Dismissal.Failed -> AdShowOutcome(AdShowResult.Failed, dismissal.kind)
            Dismissal.Closed -> AdShowOutcome(AdShowResult.Dismissed)
        }
    }

    private suspend fun loadInterstitial(): Catching<InterstitialAd> = load { cont ->
        InterstitialAd.load(
            context,
            AdUnits.android(AdFormat.Interstitial),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) = cont(Catching.success(ad))
                override fun onAdFailedToLoad(error: LoadAdError) = cont(Catching.failure(error.asThrowable()))
            },
        )
    }

    private suspend fun loadRewarded(): Catching<RewardedAd> = load { cont ->
        RewardedAd.load(
            context,
            AdUnits.android(AdFormat.Rewarded),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) = cont(Catching.success(ad))
                override fun onAdFailedToLoad(error: LoadAdError) = cont(Catching.failure(error.asThrowable()))
            },
        )
    }



    /**
     * A load that never calls back would suspend a rewarded continue forever,
     * with the player looking at a board that has stopped responding. The
     * timeout is what turns "the SDK is wedged" into "the reward was free".
     */
    private suspend fun <T : Any> load(start: (cont: (Catching<T>) -> Unit) -> Unit): Catching<T> =
        withTimeoutOrNull(LOAD_TIMEOUT) {
            withContext(dispatchers.main) {
                suspendCancellableCoroutine { cont ->
                    start { result -> if (cont.isActive) cont.resume(result) }
                }
            }
        } ?: Catching.failure(AdLoadFailure(AdShowResult.NoFill, "load_timeout"))

    private fun resumeOnceCallback(
        isActive: () -> Boolean,
        resume: (Dismissal) -> Unit,
    ): FullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() {
            if (isActive()) resume(Dismissal.Closed)
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            if (isActive()) resume(Dismissal.Failed("show_${error.code}"))
        }
    }

    private sealed interface Dismissal {
        data object Closed : Dismissal
        data class Failed(val kind: String) : Dismissal
    }

    private companion object {
        val LOAD_TIMEOUT = 20.seconds
        val CONSENT_TIMEOUT = 30.seconds
    }
}

/**
 * Carries the SDK's verdict through a [Catching] without giving the layers
 * above a Google type to depend on.
 */
private class AdLoadFailure(
    val result: AdShowResult,
    val kind: String,
) : Exception(kind)

private fun Throwable.toOutcome(): AdShowOutcome = when (this) {
    is AdLoadFailure -> AdShowOutcome(result, kind)
    else -> AdShowOutcome(AdShowResult.Failed, this::class.simpleName ?: "unknown")
}

/**
 * `NETWORK_ERROR` is the SDK's own "there is no route", and it is the one load
 * failure that means something different to the player. Reported honestly
 * because the rewarded path pays out on all three and the *reason* is the only
 * way to tell a bad ad unit from a bad afternoon.
 */
private fun LoadAdError.asThrowable(): AdLoadFailure = AdLoadFailure(
    result = when (code) {
        AdRequest.ERROR_CODE_NO_FILL, AdRequest.ERROR_CODE_MEDIATION_NO_FILL -> AdShowResult.NoFill
        AdRequest.ERROR_CODE_NETWORK_ERROR -> AdShowResult.Offline
        else -> AdShowResult.Failed
    },
    kind = "load_$code",
)
