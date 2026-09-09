package com.dangerfield.drop2048.integration.helpers

import com.dangerfield.drop2048.features.home.impl.HomeViewModel
import com.dangerfield.drop2048.libraries.core.AuthGate
import com.dangerfield.drop2048.libraries.core.AuthRequirement
import com.dangerfield.drop2048.libraries.core.AuthVerdict
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.DefaultDispatcherProvider
import com.dangerfield.drop2048.libraries.identity.auth.AuthRepository
import com.dangerfield.drop2048.libraries.identity.auth.AuthState
import com.dangerfield.drop2048.libraries.identity.auth.OAuthProvider
import com.dangerfield.drop2048.libraries.identity.impl.HttpProfileApi
import com.dangerfield.drop2048.libraries.identity.impl.auth.PendingGuestAccountStore
import com.dangerfield.drop2048.libraries.identity.impl.profile.PendingProfileEditStore
import com.dangerfield.drop2048.libraries.identity.impl.profile.ProfileCache
import com.dangerfield.drop2048.libraries.identity.impl.profile.ProfileRepositoryImpl
import com.dangerfield.drop2048.libraries.identity.profile.ProfileRepository
import com.dangerfield.drop2048.libraries.networking.AuthTokenProvider
import com.dangerfield.drop2048.libraries.networking.InstallIdProvider
import com.dangerfield.drop2048.libraries.networking.NetworkConfig
import com.dangerfield.drop2048.libraries.networking.SessionIdProvider
import com.dangerfield.drop2048.libraries.networking.impl.AccessDeniedBusImpl
import com.dangerfield.drop2048.libraries.networking.impl.DefaultClientHeadersProvider
import com.dangerfield.drop2048.libraries.networking.impl.NetworkClientImpl
import com.dangerfield.drop2048.libraries.networking.impl.NetworkReachabilityImpl
import com.dangerfield.drop2048.libraries.networking.impl.SessionRejectionBusImpl
import com.dangerfield.drop2048.libraries.storage.Cache
import com.dangerfield.drop2048.libraries.storage.CacheFactory
import com.dangerfield.drop2048.libraries.storage.CacheJsonSerializer
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

/**
 * One end-to-end test user: the REAL client stack — [NetworkClientImpl] with the
 * real headers provider / reachability / buses, a real [HttpProfileApi], and a
 * real [ProfileRepositoryImpl] driving `GET /v1/me` — pointed at [serverUrl].
 * Build a real [HomeViewModel] on demand with [homeVm]. This is the worked
 * example of the harness shape: real client → real TCP → real server → real DB.
 *
 * [userId] is the JWT `sub` AND the identity the fake [AuthRepository] reports,
 * so what the client believes lines up with what the server sees.
 *
 * The seams that stay fake are exactly the ones a device would own: auth-state
 * resolution (a canned Authenticated session — the token side is real, see
 * [IntegrationAuth]), and on-disk persistence (in-memory [CacheFactory]).
 */
class TestClient(
    serverUrl: String,
    val userId: String = randomUserId(),
) {
    // One app-lifetime scope per client. The repository's background collectors
    // live here so [close] can quiesce a client's work before the server stops.
    private val appScope = AppCoroutineScope(DefaultDispatcherProvider())

    private val config = object : NetworkConfig {
        override val baseUrl: String = serverUrl
    }

    private val networkClient = NetworkClientImpl(
        config = config,
        tokenProvider = TokenProvider(userId),
        headersProvider = DefaultClientHeadersProvider(FixedInstallId, FixedSessionId),
        reachability = NetworkReachabilityImpl(appScope),
        accessDeniedBus = AccessDeniedBusImpl(),
        sessionRejectionBus = SessionRejectionBusImpl(),
        authGate = { AlwaysReadyAuthGate },
    )

    private val cacheFactory: CacheFactory = InMemoryCacheFactory()

    /**
     * The real repository over the real API — its init collector resolves the
     * profile from `/v1/me` as soon as the fake auth emits Authenticated.
     */
    val profileRepository: ProfileRepository = ProfileRepositoryImpl(
        authRepository = FakeAuthRepository(userId),
        profileApi = HttpProfileApi(networkClient),
        profileCache = ProfileCache(cacheFactory),
        pendingGuestAccountStore = PendingGuestAccountStore(cacheFactory),
        pendingProfileEditStore = PendingProfileEditStore(cacheFactory),
        appScope = appScope,
    )

    /** Build the real home VM for this user. Mirrors how the entry point constructs it. */
    fun homeVm(): HomeViewModel = HomeViewModel(profileRepository)

    /** Cancel this client's background work (profile resolve collector, etc.). */
    fun close() = appScope.cancel()

    private class TokenProvider(private val userId: String) : AuthTokenProvider {
        override suspend fun awaitReady() = Unit
        override suspend fun accessToken(): String = IntegrationAuth.mintJwt(userId)
        override suspend fun refreshAccessToken(): String = IntegrationAuth.mintJwt(userId)
    }

    private object FixedInstallId : InstallIdProvider {
        private val id = UUID.randomUUID().toString()
        override fun current(): String = id
    }

    private object FixedSessionId : SessionIdProvider {
        private val id = UUID.randomUUID().toString()
        override fun current(): String = id
    }

    /** Always signed in as [userId]; the rest is unused by the profile flow. */
    private class FakeAuthRepository(userId: String) : AuthRepository {
        private val state: AuthState =
            AuthState.Authenticated(userId = userId, isAnonymous = true, email = null)

        override suspend fun current(): AuthState = state
        override fun observe(): Flow<AuthState> = flowOf(state)
        override suspend fun retry(): AuthState = state
        override suspend fun signInWithEmail(email: String, password: String) = error("unused")
        override suspend fun signUpWithEmail(email: String, password: String) = error("unused")
        override suspend fun refreshSession() = error("unused")
        override suspend fun resendVerificationEmail(email: String) = error("unused")
        override suspend fun sendPasswordResetEmail(email: String) = error("unused")
        override suspend fun signOut() = Unit
        override suspend fun deleteAccount() = error("unused")
        override suspend fun linkOAuthIdentity(provider: OAuthProvider) = error("unused")
        override suspend fun signInWithOAuth(provider: OAuthProvider) = error("unused")
        override suspend fun linkEmailIdentity(email: String, password: String) = error("unused")
    }
}

internal fun randomUserId(): String = UUID.randomUUID().toString()

/** The suite's default: never gates — every authed call fires against the real server. */
internal object AlwaysReadyAuthGate : AuthGate {
    override fun verdict(requirement: AuthRequirement): AuthVerdict = AuthVerdict.Ready
    override suspend fun awaitVerdict(requirement: AuthRequirement): AuthVerdict = AuthVerdict.Ready
}

/**
 * In-memory [CacheFactory] — the production stores ([ProfileCache], the pending
 * stores) are concrete classes over this seam, so an in-memory factory lets the
 * harness run the REAL store logic with no file system underneath.
 */
private class InMemoryCacheFactory : CacheFactory {

    override fun <T : Any> inMemory(defaultValue: () -> T): Cache<T> =
        HarnessCache { defaultValue() }

    override fun <T : Any> persistent(
        name: String,
        serializer: CacheJsonSerializer<T>,
        loadEagerly: Boolean,
    ): Cache<T> = HarnessCache { serializer.read(null) }

    /** Null state = "not yet loaded"; [get] seeds it from the default lazily. */
    private class HarnessCache<T : Any>(
        private val default: suspend () -> T,
    ) : Cache<T> {
        private val state = MutableStateFlow<T?>(null)

        override val updates: Flow<T> = state.filterNotNull()

        override suspend fun get(): T {
            state.compareAndSet(null, default())
            return checkNotNull(state.value)
        }

        override suspend fun set(value: T) {
            state.value = value
        }

        override suspend fun clear() {
            state.value = default()
        }
    }
}
