package com.dangerfield.drop2048.libraries.storage.impl.db

import com.dangerfield.drop2048.libraries.drop2048.storage.db.ClearableDao
import com.dangerfield.drop2048.libraries.progress.db.RunRecordDao
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * SPEC 11's `run_record`, exposed to the graph and enrolled in the wipe set.
 *
 * The [ClearableDao] binding is the load-bearing half: this is the first table
 * in the app that holds something a player would miss, and "reset progress"
 * (C11) finds it by injecting `Set<ClearableDao>` rather than by anyone
 * remembering to add it to a list.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = RunRecordDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideRunRecordDao @Inject constructor(
    provider: AppDatabaseProvider
) : RunRecordDao by provider.database.runRecordDao()
