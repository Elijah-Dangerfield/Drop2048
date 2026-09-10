package com.dangerfield.drop2048.features.debug

import kotlinx.coroutines.flow.StateFlow

/**
 * The second lock on the debug menu, behind the seven taps.
 *
 * Seven taps on the version number is obscurity, not security: it is a documented
 * Android gesture, and a player who reads a forum finds it in a minute. On a debug
 * build that is fine and the menu opens. On a release build the gesture only gets
 * you as far as a passphrase prompt, because the menu can grant Pro, and a
 * store-shipped binary with a free entitlement switch in it is a revenue bug
 * and — since it also silences the analytics session — a data one.
 *
 * The passphrase is checked here rather than in the ViewModel so that "what
 * unlocks this" is one file, and so a release build can be handed a different
 * answer without touching a screen. [isUnlocked] is a flow rather than a boolean
 * because unlocking has to redraw the screen that is already open.
 */
interface DebugMenuGate {

    /** True on a build where the menu needs no passphrase at all. */
    val isOpenBuild: Boolean

    val isUnlocked: StateFlow<Boolean>

    /** Returns whether [passphrase] was right. Wrong answers cost nothing but a retry. */
    fun unlock(passphrase: String): Boolean
}
