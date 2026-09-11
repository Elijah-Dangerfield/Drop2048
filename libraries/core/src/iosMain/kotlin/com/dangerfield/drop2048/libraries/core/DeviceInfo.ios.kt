package com.dangerfield.drop2048.libraries.core

import platform.UIKit.UIDevice

actual object DeviceInfo {
    /**
     * `UIDevice.model` is the family — "iPhone", "iPad" — not the marketing
     * name. Apple offers no public API for the latter, and the alternative is a
     * hardware identifier plus a lookup table, which is a more precise fact
     * about the device than this app has any business holding.
     */
    actual val model: String
        get() = UIDevice.currentDevice.model.ifBlank { "unknown" }

    actual val osVersion: String
        get() = with(UIDevice.currentDevice) { "$systemName $systemVersion" }
}
