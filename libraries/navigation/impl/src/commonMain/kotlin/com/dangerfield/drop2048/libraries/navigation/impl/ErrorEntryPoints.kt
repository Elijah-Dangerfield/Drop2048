package com.dangerfield.drop2048.libraries.navigation.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.dangerfield.drop2048.features.profile.BugReportRoute
import com.dangerfield.drop2048.libraries.navigation.AccessDeniedRoute
import com.dangerfield.drop2048.libraries.navigation.BlockingErrorRoute
import com.dangerfield.drop2048.libraries.navigation.ErrorDialogAction
import com.dangerfield.drop2048.libraries.navigation.ErrorDialogRoute
import com.dangerfield.drop2048.libraries.navigation.FeatureEntryPoint
import com.dangerfield.drop2048.libraries.navigation.NavigationOptions
import com.dangerfield.drop2048.libraries.navigation.Router
import com.dangerfield.drop2048.libraries.navigation.dialog
import com.dangerfield.drop2048.libraries.navigation.screen
import com.dangerfield.drop2048.libraries.navigation.serializableType
import com.dangerfield.drop2048.libraries.navigation.toRouteOrNull
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.reflect.typeOf

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, multibinding = true)
@Inject
class ErrorEntryPoints : FeatureEntryPoint {

    override fun NavGraphBuilder.buildNavGraph(router: Router) {
        screen<AccessDeniedRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AccessDeniedRoute>()
            AccessDeniedScreen(
                reason = route.reason,
                until = route.until,
                appealUrl = route.appealUrl,
                onAppeal = { url -> router.openWebLink(url) },
            )
        }

        screen<BlockingErrorRoute>(
            typeMap = mapOf()
        ) { backStackEntry ->
            val route = backStackEntry.toRouteOrNull<BlockingErrorRoute>() ?: DEFAULT_BLOCKING_ERROR
            BlockingErrorScreen(
                title = route.title,
                subtitle = route.subtitle,
                errorCode = route.errorCode ?: DEFAULT_BLOCKING_ERROR.errorCode,
                onReportToDevelopers = {
                    router.navigate(
                        BugReportRoute(
                            logId = route.logId,
                            errorCode = route.errorCode ?: DEFAULT_BLOCKING_ERROR.errorCode,
                            contextMessage = route.contextMessage ?: route.subtitle,
                        )
                    )
                }
            )
        }

        dialog<ErrorDialogRoute>(
            typeMap = mapOf(typeOf<ErrorDialogAction>() to serializableType<ErrorDialogAction>(),
            )
        ) { backStackEntry, dialogState ->
            val route = backStackEntry.toRouteOrNull<ErrorDialogRoute>() ?: DEFAULT_ERROR_DIALOG
            var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

            fun dismissWithAction(action: () -> Unit) {
                pendingAction = action
                dialogState.dismiss()
            }

            ErrorDialog(
                state = dialogState,
                title = route.title,
                subtitle = route.subtitle,
                actionTitle = route.actionTitle,
                errorCode = route.errorCode ?: DEFAULT_ERROR_DIALOG.errorCode,
                onDismissRequest = {
                    val action = pendingAction ?: router::goBack
                    pendingAction = null
                    action()
                },
                onAction = {
                    dismissWithAction { handleDialogAction(route.action, router) }
                },
                onReportToDeveloper = {
                    dismissWithAction {
                        router.navigate(
                            BugReportRoute(
                                logId = route.logId,
                                errorCode = route.errorCode ?: DEFAULT_ERROR_DIALOG.errorCode,
                                contextMessage = route.contextMessage ?: route.subtitle,
                            )
                        )
                    }
                }
            )
        }
    }
}

private fun handleDialogAction(action: ErrorDialogAction, router: Router) {
    when (action) {
        ErrorDialogAction.Dismiss,
        ErrorDialogAction.GoBack -> router.goBack()

        is ErrorDialogAction.Navigate -> {
            router.goBack()
            router.navigate(action.route)
        }
    }
}

private val DEFAULT_BLOCKING_ERROR = BlockingErrorRoute(
    title = "Something went wrong",
    subtitle = "Please try again.",
    errorCode = 1000,
    contextMessage = null,
)

private val DEFAULT_ERROR_DIALOG = ErrorDialogRoute(
    title = "Oops",
    subtitle = "Looks like something broke. Please try again.",
    actionTitle = "Dismiss",
    errorCode = 1000,
    contextMessage = null,
)
