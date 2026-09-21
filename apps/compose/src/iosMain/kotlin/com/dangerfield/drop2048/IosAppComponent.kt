package com.dangerfield.drop2048

import com.dangerfield.drop2048.libraries.ads.AdNetwork
import com.dangerfield.drop2048.libraries.billing.StoreBilling
import com.dangerfield.drop2048.libraries.drop2048.PermissionManager
import com.dangerfield.drop2048.libraries.review.ReviewLauncher
import com.dangerfield.drop2048.libraries.ui.nativeviews.NativeViewFactory
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.MergeComponent
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@MergeComponent(AppScope::class)
@SingleIn(AppScope::class)
abstract class IosAppComponent(
    private val permissionManager: PermissionManager,
    private val reviewLauncher: ReviewLauncher,
    private val adNetwork: AdNetwork,
    private val storeBilling: StoreBilling,
    val nativeViewFactory: NativeViewFactory
) : AppComponent {

    @Provides
    fun providePermissionManager(): PermissionManager = permissionManager

    @Provides
    fun provideReviewLauncher(): ReviewLauncher = reviewLauncher

    /**
     * The Swift `IOSAdNetwork`. GoogleMobileAds ships as an iOS framework, and
     * reaching it through cinterop would mean maintaining a Kotlin binding for
     * an SDK Google changes on their own schedule. Swift implements the narrow
     * seam instead and the policy above it stays in common Kotlin.
     */
    @Provides
    fun provideAdNetwork(): AdNetwork = adNetwork

    /**
     * The Swift `IOSStoreBilling`. StoreKit 2 is Swift-only in practice, and
     * the same argument as [provideAdNetwork] applies: Swift implements the
     * narrow seam, and `RealEntitlements` keeps the entitlement policy above it
     * in common Kotlin.
     */
    @Provides
    fun provideStoreBilling(): StoreBilling = storeBilling
}


@MergeComponent.CreateComponent
expect fun create(
    permissionManager: PermissionManager,
    reviewLauncher: ReviewLauncher,
    adNetwork: AdNetwork,
    storeBilling: StoreBilling,
    nativeViewFactory: NativeViewFactory
): IosAppComponent
