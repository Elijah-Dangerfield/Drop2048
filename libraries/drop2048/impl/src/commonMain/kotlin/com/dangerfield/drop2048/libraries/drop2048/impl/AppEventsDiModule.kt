package com.dangerfield.drop2048.libraries.drop2048.impl

import com.dangerfield.drop2048.libraries.drop2048.AppEventBus
import com.dangerfield.drop2048.libraries.drop2048.AppEvents
import me.tatarka.inject.annotations.Provides
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesTo
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@ContributesTo(AppScope::class)
interface AppEventsDiModule {

    @Provides
    @SingleIn(AppScope::class)
    fun provideAppEvents(bus: AppEventBus): AppEvents = AppEvents(bus)
}
