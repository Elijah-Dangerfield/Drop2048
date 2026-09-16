package com.dangerfield.drop2048.libraries.achievements.impl

import com.dangerfield.drop2048.libraries.achievements.AchievementsRepository
import com.dangerfield.drop2048.libraries.core.AutoInit
import com.dangerfield.drop2048.libraries.core.Catching
import com.dangerfield.drop2048.libraries.core.logOnFailure
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.leaderboards.Leaderboards
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Mirrors the badges the player has earned onto the platform's profile, so a
 * Game Center achievement exists for each one outside this app.
 *
 * ## Why it is a listener rather than a line in `endRun`
 *
 * The obvious place is next to `Leaderboards.submit`, in `GameViewModel.endRun`,
 * where the newly-unlocked list is already in hand. That version reports each
 * badge exactly once, at the instant it is earned — and loses it for good if the
 * player was signed out at that instant, which is the normal state of a first
 * session, or if the app died between the unlock landing in Room and the report
 * being accepted.
 *
 * Observing the stored set instead makes the correct thing structural. The
 * source of truth is the fact log, which is re-folded on every launch, so the
 * complete earned set is republished on every start and after every run, and
 * `Leaderboards.reportUnlocked` works out what the platform has not been told.
 * That covers three cases the call-site version gets wrong with no extra code:
 * signed in late, killed mid-report, and a badge granted retroactively by a
 * release that widened the catalog.
 *
 * It also keeps the reporting off the run-end path, which is the one place in
 * this app where latency is visible.
 *
 * ## The dependency direction
 *
 * This is the only thing in the achievements stack that knows leaderboards
 * exist, and it is deliberately on this side of the seam: `:libraries:achievements`
 * has a database and an engine, and making `:libraries:leaderboards` depend on
 * all of that so it could name an `AchievementId` would put a Room dependency
 * under a platform shim. So the seam is a set of names, and
 * `platformAchievementId` turns one into a Game Center id. `AchievementId`
 * already promises those names never change.
 *
 * Nothing here is gated on `StartedRun.debug`. It does not need to be: a debug
 * run never reaches the fact log at all (`GameViewModel.endRun`), so there is no
 * unlock for this to see.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class AchievementPlatformSync(
    private val achievements: AchievementsRepository,
    private val leaderboards: Leaderboards,
    private val appScope: AppCoroutineScope,
) : AutoInit {

    init {
        appScope.launch {
            Catching {
                achievements.observe()
                    .map { state -> state.unlocked.keys.mapTo(mutableSetOf()) { it.name } }
                    .distinctUntilChanged()
                    .collect(leaderboards::reportUnlocked)
            }.logOnFailure { "Achievement platform sync stopped" }
        }
    }
}
