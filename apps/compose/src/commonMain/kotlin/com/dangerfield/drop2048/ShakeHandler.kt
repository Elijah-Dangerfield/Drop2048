package com.dangerfield.drop2048

import com.dangerfield.drop2048.libraries.core.ShakeDetector
import com.dangerfield.drop2048.libraries.core.ShakeEvent
import com.dangerfield.drop2048.libraries.core.ShakeMessageContext
import com.dangerfield.drop2048.libraries.core.ShakeMessageProvider
import com.dangerfield.drop2048.libraries.identity.profile.ProfileRepository
import com.dangerfield.drop2048.libraries.identity.profile.displayNameOrNull
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.ShakeDialogRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@Inject
@SingleIn(AppScope::class)
class ShakeHandler(
    private val shakeDetector: ShakeDetector,
    private val shakeMessageProvider: ShakeMessageProvider,
    private val profileRepository: ProfileRepository,
    private val router: Router,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isShowingDialog = false
    // Process-local flavor counter for the shake easter-egg copy.
    private var shakeCount = 0
    
    fun start() {
        shakeDetector.start()
        scope.launch {
            shakeDetector.shakeEvents.collect { event ->
                handleShake(event)
            }
        }
    }
    
    fun stop() {
        shakeDetector.stop()
    }
    
    fun onDialogDismissed() {
        isShowingDialog = false
    }
    
    private suspend fun handleShake(event: ShakeEvent) {
        if (isShowingDialog) return
        
        val profile = profileRepository.current()

        val context = ShakeMessageContext(
            shakeCount = shakeCount,
            intensity = event.intensity,
            isLateNight = false,
            isFirstSession = false,
            userName = profile.displayNameOrNull,
        )
        
        val message = shakeMessageProvider.getMessage(context)
        
        isShowingDialog = true
        router.navigate(
            ShakeDialogRoute(
                headline = message.headline,
                subtext = message.subtext,
            )
        )

        shakeCount++
    }
}
