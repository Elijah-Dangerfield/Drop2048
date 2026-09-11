package com.dangerfield.drop2048.libraries.core

import android.os.Build

/**
 * `Build`'s fields are platform types and they are genuinely null on a plain
 * host JVM, where the android.jar stub leaves every static uninitialised. So
 * everything here goes through [orEmpty] rather than trusting the signature.
 */
actual object DeviceInfo {
    actual val model: String
        get() = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { UNKNOWN }

    actual val osVersion: String
        get() = "Android ${Build.VERSION.RELEASE.orEmpty().ifBlank { UNKNOWN }} (API ${Build.VERSION.SDK_INT})"
}

private const val UNKNOWN = "unknown"
