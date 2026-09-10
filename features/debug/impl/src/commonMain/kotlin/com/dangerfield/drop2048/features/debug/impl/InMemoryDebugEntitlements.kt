package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.features.debug.DebugEntitlements
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class InMemoryDebugEntitlements : DebugEntitlements {

    private val _proGranted = MutableStateFlow(false)
    override val proGranted: StateFlow<Boolean> = _proGranted.asStateFlow()

    override suspend fun setProGranted(granted: Boolean) {
        _proGranted.value = granted
    }

    override suspend fun resetPurchases() {
        _proGranted.value = false
    }
}
