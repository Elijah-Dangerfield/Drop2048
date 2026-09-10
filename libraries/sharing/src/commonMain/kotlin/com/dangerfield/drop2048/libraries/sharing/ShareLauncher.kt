package com.dangerfield.drop2048.libraries.sharing

/**
 * Hands a finished share string to the platform's own share UI — the Android
 * chooser, the iOS activity sheet.
 *
 * Returns nothing, and that is deliberate. A share sheet that fails to present
 * has no recovery a caller could offer and no second thing to try, so the
 * implementations log and move on, which keeps this interface free of
 * `:libraries:core` and keeps this module's dependency list empty.
 */
fun interface ShareLauncher {
    fun share(text: String)
}
