package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.features.debug.DebugMenuGate
import com.dangerfield.drop2048.libraries.core.BuildInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Debug builds open the menu; release builds ask for a passphrase.
 *
 * The passphrase is a compiled-in constant and that is honestly all it is worth
 * being. It is not protecting a secret, it is raising the cost of an accident:
 * the realistic threat is a curious player who found the seven-tap gesture on a
 * forum, not somebody with a disassembler. Anyone willing to unpack the binary
 * can already patch the check out, and there is nothing behind it that is worth
 * a key exchange — the entitlement it grants is local, and the analytics session
 * it silences is this device's own.
 *
 * What matters is that it is checked on a *release* build at all, because the
 * menu can grant Pro and switch off the run recording, and a store binary where
 * seven taps does both is a revenue bug and a data bug in one gesture.
 *
 * The unlock is per-process. It is not persisted, so it does not survive a
 * relaunch, and it deliberately does not live in `PlayerSettings` next to
 * `debugMenuUnlocked` — the tap gesture is a preference, and standing at the
 * door with the password is not.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class BuildTypeDebugMenuGate : DebugMenuGate {

    override val isOpenBuild: Boolean = BuildInfo.isDebug

    private val _isUnlocked = MutableStateFlow(BuildInfo.isDebug)
    override val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    override fun unlock(passphrase: String): Boolean {
        val correct = passphrase.trim().equals(PASSPHRASE, ignoreCase = true)
        if (correct) _isUnlocked.value = true
        return correct
    }

    private companion object {
        const val PASSPHRASE = "cascade"
    }
}
