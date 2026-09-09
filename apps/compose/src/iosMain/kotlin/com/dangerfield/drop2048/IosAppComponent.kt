package com.dangerfield.drop2048

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
    val nativeViewFactory: NativeViewFactory
) : AppComponent {

    @Provides
    fun providePermissionManager(): PermissionManager = permissionManager

    @Provides
    fun provideReviewLauncher(): ReviewLauncher = reviewLauncher
}


@MergeComponent.CreateComponent
expect fun create(
    permissionManager: PermissionManager,
    reviewLauncher: ReviewLauncher,
    nativeViewFactory: NativeViewFactory
): IosAppComponent
