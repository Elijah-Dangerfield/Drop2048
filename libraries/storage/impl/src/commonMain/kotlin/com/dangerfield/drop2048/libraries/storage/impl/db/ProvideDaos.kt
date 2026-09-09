package com.dangerfield.drop2048.libraries.storage.impl.db

import com.dangerfield.drop2048.libraries.drop2048.storage.db.ClearableDao
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ExampleUserDataDao
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The pattern for exposing a DAO to the DI graph: delegate to the database
 * provider AND contribute the DAO into the [ClearableDao] multibinding set, so
 * a bulk wipe (Settings' "reset progress") can empty every table without
 * knowing what they are. Copy this pair of annotations for every DAO you add.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ExampleUserDataDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideExampleUserDataDao @Inject constructor(
    provider: AppDatabaseProvider
) : ExampleUserDataDao by provider.database.exampleUserDataDao()
