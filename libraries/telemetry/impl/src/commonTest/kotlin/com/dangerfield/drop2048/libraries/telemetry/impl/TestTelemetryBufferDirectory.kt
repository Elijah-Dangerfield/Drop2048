package com.dangerfield.drop2048.libraries.telemetry.impl

import okio.Path

internal expect fun testTelemetryBufferDirectory(name: String): Path
