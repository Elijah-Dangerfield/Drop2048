package com.dangerfield.drop2048.libraries.storage.impl.db

import com.dangerfield.drop2048.libraries.flowroutines.DispatcherProvider
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAppDatabaseProvider @Inject constructor(
    private val builderFactory: AppDatabaseBuilderFactory,
    private val dispatcherProvider: DispatcherProvider
) : AppDatabaseProvider {

    override val database: AppDatabase by lazy {
        builderFactory
            .create()
            .setQueryCoroutineContext(dispatcherProvider.io)
            // The one migration that is not an @AutoMigration. See its KDoc for
            // why Room cannot generate it.
            .addMigrations(MIGRATE_AWAY_FROM_THE_DAILY)
            // Only the pre-game template schemas may be dropped. Everything from
            // AppDatabase.FIRST_PLAYER_DATA_VERSION up migrates, because there is
            // no account to restore a wiped run history from. See AppDatabase.
            .fallbackToDestructiveMigrationFrom(
                dropAllTables = true,
                *PRE_GAME_SCHEMA_VERSIONS,
            )
            .build()
    }
}

private val PRE_GAME_SCHEMA_VERSIONS = intArrayOf(1, 2, 3, 4)
