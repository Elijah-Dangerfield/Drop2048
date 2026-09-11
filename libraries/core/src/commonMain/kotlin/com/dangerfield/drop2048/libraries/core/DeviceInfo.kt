package com.dangerfield.drop2048.libraries.core

/**
 * What the device calls itself, and nothing else about it.
 *
 * It exists for one caller: the diagnostics opt-in appends it to a feedback
 * report, because `settings_diagnostics_hint` tells the player that turning the
 * switch on attaches "your device model and build number". Before this the
 * switch attached the build number and lied about the rest.
 *
 * Deliberately a model string rather than a device profile. Anything more
 * specific — a serial, an identifier, a fingerprint — is a second identity for
 * the app to hold, and §1 of `docs/store/data-safety.md` is the claim that it
 * holds exactly one.
 */
expect object DeviceInfo {
    /** Manufacturer and model on Android, Apple's model name on iOS. */
    val model: String

    /** OS name and version, as the platform reports it. */
    val osVersion: String
}
