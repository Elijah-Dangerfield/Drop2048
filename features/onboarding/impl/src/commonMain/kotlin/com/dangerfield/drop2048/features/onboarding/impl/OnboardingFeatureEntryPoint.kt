package com.dangerfield.drop2048.features.onboarding.impl

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import com.dangerfield.drop2048.features.home.HomeRoute
import com.dangerfield.drop2048.features.onboarding.OnboardingRoute
import com.dangerfield.drop2048.libraries.flowroutines.ObserveEvents
import com.dangerfield.drop2048.libraries.navigation.FeatureEntryPoint
import com.dangerfield.drop2048.libraries.navigation.NavigationOptions
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.screen
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class OnboardingFeatureEntryPoint(
    private val onboardingViewModelFactory: () -> OnboardingViewModel,
) : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<OnboardingRoute> {
            val viewModel: OnboardingViewModel = viewModel { onboardingViewModelFactory() }
            val state = viewModel.stateFlow.collectAsStateWithLifecycle().value

            viewModel.ObserveEvents { event ->
                when (event) {
                    OnboardingEvent.NavigateToHome -> router.navigate(
                        HomeRoute(),
                        NavigationOptions(launchSingleTop = true, clearBackStack = true),
                    )
                }
            }

            OnboardingScreen(
                state = state,
                onAction = viewModel::takeAction,
            )
        }
    }
}
