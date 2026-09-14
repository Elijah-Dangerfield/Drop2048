package com.dangerfield.drop2048

import com.dangerfield.drop2048.features.gate.impl.LaunchGateViewModel
import com.dangerfield.drop2048.features.settings.PlayerSettingsStore
import com.dangerfield.drop2048.libraries.core.AppState
import com.dangerfield.drop2048.libraries.core.AutoInit
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ClearableDao
import com.dangerfield.drop2048.libraries.navigation.DeepLinkBridge
import com.dangerfield.drop2048.libraries.navigation.impl.DelegatingRouter
import com.dangerfield.drop2048.libraries.telemetry.impl.JankMonitor
import com.dangerfield.drop2048.libraries.telemetry.impl.StartupReporter
import com.dangerfield.drop2048.libraries.drop2048.Telemetry
import com.dangerfield.drop2048.libraries.ads.HouseAds
import com.dangerfield.drop2048.libraries.navigation.FeatureEntryPoint
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock

@ContributesTo(AppScope::class)
@SingleIn(AppScope::class)
interface AppComponent {
    val featureEntryPoints: Set<FeatureEntryPoint>
    val appViewModel: AppViewModel  // Singleton, shared between MainActivity and App
    val delegatingRouter: DelegatingRouter
    val telemetry: Telemetry

    /**
     * Real-user frame timing, reported per screen. Android binds JankStats; iOS
     * binds a no-op (see [JankMonitor]).
     */
    val jankMonitor: JankMonitor

    /**
     * Cold-start duration, reported once per process when the first usable
     * frame is on screen. Android measures from OS process creation; iOS
     * reports nothing on purpose (see [ProcessStartTimeProvider]).
     */
    val startupReporter: StartupReporter
    /**
     * The house ad network's placeholder, drawn at the root of the app.
     *
     * On the component rather than reached for inside a feature because a
     * full-screen ad is above every feature, and because the binding that
     * answers it is `NoHouseAds` in any build that does not contain the house
     * network — which is every release build. See `HouseAds`.
     *
     * `App` draws it last, above the dialog host, because a full-screen ad is
     * above everything — that is what makes it a full-screen ad, and a
     * placeholder a dialog could cover would be lying about the surface it
     * stands in for. In a release build it is one resolved binding and an empty
     * composable.
     */
    val houseAds: HouseAds

    val shakeHandler: ShakeHandler
    val deepLinkBridge: DeepLinkBridge

    /**
     * Production app-wide state (offline banner etc.). Backed by
     * AppStateImpl — platform connectivity combined with witnessed
     * request reachability.
     */
    val appState: AppState

    /**
     * Singletons that need to construct at app boot rather than lazily
     * on first injection. Anvil populates this set via the
     * `@ContributesBinding(... AutoInit::class, multibinding = true)`
     * annotation on each implementer — see [AutoInit] for the
     * contract and when to opt in.
     *
     * The set is resolved once on first composition in `App.kt` (and in
     * `Application.onCreate` on Android); the act of resolving forces
     * every contributor to construct, which runs each implementer's
     * `init {}` block. That's how
     * [com.dangerfield.drop2048.libraries.drop2048.impl.AppEventDispatcher]
     * registers its lifecycle listener at boot.
     */
    val autoInits: Set<AutoInit>

    /**
     * Every table holding wipeable data, for Settings' "reset progress" (C11).
     *
     * Exposed here before C11 needs it on purpose. A multibinding with no
     * accessor anywhere is not validated by anything — it compiles whether or not
     * a DAO remembered to contribute itself, which is precisely how this repo has
     * shipped an empty set before. Naming it here makes the graph check it at
     * build time, and C11 injects `Set<ClearableDao>` rather than a list.
     */
    val clearableDaos: Set<ClearableDao>

    /**
     * The player's accessibility and control settings, read at the root of the
     * composition and handed to `AppThemeProvider`.
     *
     * On the component rather than reached for inside a feature because the
     * theme is above every feature. This is the wire that makes C2's five
     * palettes, its reduce-motion signal, its large-numbers scale and its haptic
     * engine reachable at all — before C11 the provider was called on defaults.
     */
    val playerSettingsStore: PlayerSettingsStore

    /**
     * Force update, maintenance and legal re-accept.
     *
     * Held here and driven from `App` rather than registered as a destination:
     * a blocking gate is rendered *instead of* the nav host, so there is no back
     * stack entry to pop and no deep link that can land behind it.
     */
    val launchGateViewModel: LaunchGateViewModel

    @Provides
    fun provideClock(): Clock = Clock.System

}
