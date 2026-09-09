package com.dangerfield.drop2048.features.onboarding.impl

import com.dangerfield.drop2048.libraries.drop2048.AppCache
import com.dangerfield.drop2048.libraries.drop2048.AppData
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent

/**
 * Drop 2048 has no accounts, so the whole flow is: a returning player never
 * sees it, and a first-run player leaves it exactly once with the flag set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest : CoroutineTest() {

    @Test
    fun init_alreadyOnboarded_immediatelyNavigatesHome() = runUnitTest {
        val cache = FakeAppCache(initial = AppData(hasUserOnboarded = true))
        val viewModel = OnboardingViewModel(cache)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { viewModel.eventFlow.collect { received += it } }
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToHome, received.firstOrNull())
    }

    @Test
    fun init_firstRun_staysPutUntilStarted() = runUnitTest {
        val cache = FakeAppCache()
        val viewModel = OnboardingViewModel(cache)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { viewModel.eventFlow.collect { received += it } }
        runCurrent()

        assertTrue(received.isEmpty())
        assertEquals(false, cache.get().hasUserOnboarded)
    }

    @Test
    fun start_marksOnboardedAndNavigatesHome() = runUnitTest {
        val cache = FakeAppCache()
        val viewModel = OnboardingViewModel(cache)
        val received = mutableListOf<OnboardingEvent>()
        backgroundScope.launch { viewModel.eventFlow.collect { received += it } }
        runCurrent()

        viewModel.takeAction(OnboardingAction.Start)
        runCurrent()

        assertEquals(OnboardingEvent.NavigateToHome, received.firstOrNull())
        assertTrue(cache.get().hasUserOnboarded)
        assertTrue(viewModel.stateFlow.value.isFinishing)
    }
}

private class FakeAppCache(initial: AppData = AppData()) : AppCache {
    private val state = MutableStateFlow(initial)
    override val updates: Flow<AppData> = state
    override suspend fun get(): AppData = state.value
    override suspend fun set(value: AppData) {
        state.value = value
    }

    override suspend fun clear() {
        state.value = AppData()
    }
}
