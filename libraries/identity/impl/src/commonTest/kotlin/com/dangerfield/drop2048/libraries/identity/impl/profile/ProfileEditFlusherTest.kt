package com.dangerfield.drop2048.libraries.identity.impl.profile

import com.dangerfield.drop2048.libraries.drop2048.AppEvent
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.testing.CoroutineTest
import com.dangerfield.drop2048.libraries.identity.profile.Profile
import com.dangerfield.drop2048.libraries.identity.profile.ProfileRepository
import com.dangerfield.drop2048.libraries.identity.profile.UpdateProfileOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileEditFlusherTest : CoroutineTest() {

    @Test
    fun warmForeground_flushes() = runUnitTest {
        val repo = RecordingProfileRepository()
        val flusher = build(repo)
        flusher.onForeground(AppEvent.OnForeground(isColdBoot = false))
        advanceUntilIdle()
        assertEquals(1, repo.flushCalls)
    }

    @Test
    fun coldBootForeground_doesNotFlush() = runUnitTest {
        val repo = RecordingProfileRepository()
        val flusher = build(repo)
        flusher.onForeground(AppEvent.OnForeground(isColdBoot = true))
        advanceUntilIdle()
        assertEquals(0, repo.flushCalls, "cold boot already re-resolves; no extra flush")
    }

    @Test
    fun connectivityRegained_flushes() = runUnitTest {
        val repo = RecordingProfileRepository()
        val flusher = build(repo)
        flusher.onConnectivityRegained(AppEvent.ConnectivityRegained)
        advanceUntilIdle()
        assertEquals(1, repo.flushCalls)
    }

    private fun build(repo: ProfileRepository) = ProfileEditFlusher(
        profileRepositoryProvider = { repo },
        appScope = AppCoroutineScope(dispatchers),
    )

    private class RecordingProfileRepository : ProfileRepository {
        var flushCalls = 0
            private set
        override suspend fun flushPendingEdits() { flushCalls++ }
        override suspend fun current(): Profile = Profile.Fallback(id = "x")
        override fun observe(): Flow<Profile> = emptyFlow()
        override suspend fun update(
            displayName: String?,
        ): UpdateProfileOutcome = error("unused")
    }
}
