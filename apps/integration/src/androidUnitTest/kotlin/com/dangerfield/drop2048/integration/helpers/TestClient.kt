package com.dangerfield.drop2048.integration.helpers

import com.dangerfield.drop2048.libraries.config.impl.data.RemoteConfigDataSource
import com.dangerfield.drop2048.libraries.config.impl.data.RemoteConfigRemoteDataSource
import com.dangerfield.drop2048.libraries.config.impl.serialization.ConfigJsonConverter
import com.dangerfield.drop2048.libraries.flowroutines.AppCoroutineScope
import com.dangerfield.drop2048.libraries.flowroutines.DefaultDispatcherProvider
import com.dangerfield.drop2048.libraries.networking.AlwaysReadyAuthGate
import com.dangerfield.drop2048.libraries.networking.InstallIdProvider
import com.dangerfield.drop2048.libraries.networking.NetworkConfig
import com.dangerfield.drop2048.libraries.networking.NoOpAuthTokenProvider
import com.dangerfield.drop2048.libraries.networking.SessionIdProvider
import com.dangerfield.drop2048.libraries.networking.impl.AccessDeniedBusImpl
import com.dangerfield.drop2048.libraries.networking.impl.DefaultClientHeadersProvider
import com.dangerfield.drop2048.libraries.networking.impl.NetworkClientImpl
import com.dangerfield.drop2048.libraries.networking.impl.NetworkReachabilityImpl
import com.dangerfield.drop2048.libraries.networking.impl.SessionRejectionBusImpl
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * One end-to-end test client: the REAL client stack — [NetworkClientImpl] with
 * the real headers provider, reachability and buses, driving the real
 * [RemoteConfigRemoteDataSource] against [serverUrl]. Real client → real TCP →
 * real server → real Postgres.
 *
 * [installId] is what the server's targeting engine buckets rollouts on, so a
 * test can pin a client into a rollout by choosing it.
 *
 * The only fake seam is on-disk persistence, which a device would own.
 */
class TestClient(
    serverUrl: String,
    val installId: String = randomInstallId(),
) {
    // One app-lifetime scope per client, so [close] can quiesce a client's
    // background work before the server stops.
    private val appScope = AppCoroutineScope(DefaultDispatcherProvider())

    private val config = object : NetworkConfig {
        override val baseUrl: String = serverUrl
    }

    private val networkClient = NetworkClientImpl(
        config = config,
        tokenProvider = NoOpAuthTokenProvider(),
        headersProvider = DefaultClientHeadersProvider(FixedInstallId(installId), FixedSessionId),
        reachability = NetworkReachabilityImpl(appScope),
        accessDeniedBus = AccessDeniedBusImpl(),
        sessionRejectionBus = SessionRejectionBusImpl(),
        authGate = { AlwaysReadyAuthGate() },
    )

    /** The real remote-config data source over the real API. */
    val remoteConfig: RemoteConfigDataSource = RemoteConfigRemoteDataSource(
        dispatcherProvider = DefaultDispatcherProvider(),
        networkClient = networkClient,
        authTokenProvider = NoOpAuthTokenProvider(),
        converter = ConfigJsonConverter(Json),
    )

    /** Cancel this client's background work. */
    fun close() = appScope.cancel()

    private class FixedInstallId(private val id: String) : InstallIdProvider {
        override fun current(): String = id
    }

    private object FixedSessionId : SessionIdProvider {
        private val id = UUID.randomUUID().toString()
        override fun current(): String = id
    }
}

internal fun randomInstallId(): String = UUID.randomUUID().toString()
