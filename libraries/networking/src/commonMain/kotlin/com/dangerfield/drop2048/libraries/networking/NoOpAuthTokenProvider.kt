package com.dangerfield.drop2048.libraries.networking

import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Default [AuthTokenProvider] binding so an app without auth works out of the
 * box (every request goes out unauthenticated). Drop 2048 has no accounts, so
 * this is the only binding.
 *
 * It lives in this api module — not `:impl` — so a future auth library could
 * reference it in
 * `@ContributesBinding(AppScope::class, replaces = [NoOpAuthTokenProvider::class])`
 * without crossing the impl-module boundary.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class NoOpAuthTokenProvider : AuthTokenProvider {
    override suspend fun awaitReady() = Unit
    override suspend fun accessToken(): String? = null
    override suspend fun refreshAccessToken(): String? = null
}
