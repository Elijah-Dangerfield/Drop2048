package com.dangerfield.drop2048.libraries.storage.impl.db

import com.dangerfield.drop2048.libraries.drop2048.storage.db.ClearableDao
import com.dangerfield.drop2048.libraries.progress.db.DailyResultDao
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * SPEC 11's `daily_result`, exposed to the graph and enrolled in the wipe set.
 *
 * The [ClearableDao] binding matters here for a reason it did not for
 * `run_record`: "reset progress" (C11) has to take the Daily streak with it.
 * A reset that left the streak standing would leave a number on the stats page
 * that no longer has any rows behind it, which is exactly the un-witnessed
 * counter the fold exists to avoid.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DailyResultDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideDailyResultDao @Inject constructor(
    provider: AppDatabaseProvider
) : DailyResultDao by provider.database.dailyResultDao()
