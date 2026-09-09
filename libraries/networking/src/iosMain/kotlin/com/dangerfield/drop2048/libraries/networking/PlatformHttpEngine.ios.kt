package com.dangerfield.drop2048.libraries.networking

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual val platformHttpEngineFactory: HttpClientEngineFactory<*> = Darwin
