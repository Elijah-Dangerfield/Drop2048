package com.dangerfield.drop2048.libraries.storage.impl.db

import com.dangerfield.drop2048.libraries.achievements.db.AchievementDao
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ClearableDao
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * SPEC 11's `achievement_fact` and `achievement_unlock`, exposed to the graph and
 * enrolled in the wipe set.
 *
 * The [ClearableDao] binding wipes **both** tables, and the pair has to move
 * together. Clearing the facts alone would leave badges standing with no history
 * behind them; clearing the unlocks alone would re-announce every badge the
 * player already has the first time they finish a run. Either half on its own is
 * worse than neither.
 *
 * [AchievementDao] itself stays a plain Room DAO rather than extending
 * `ClearableDao`, so the rule about what a reset means lives here with the rest
 * of the wipe set instead of as a default method inside a Room interface.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AchievementDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideAchievementDao @Inject constructor(
    provider: AppDatabaseProvider
) : AchievementDao by provider.database.achievementDao(), ClearableDao {

    override suspend fun deleteAll() {
        deleteAllFacts()
        deleteAllUnlocks()
    }
}
