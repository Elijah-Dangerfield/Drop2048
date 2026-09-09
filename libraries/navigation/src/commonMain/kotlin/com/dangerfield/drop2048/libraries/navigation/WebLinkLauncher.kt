package com.dangerfield.drop2048.libraries.navigation

import com.dangerfield.drop2048.libraries.core.Catching

fun interface WebLinkLauncher {
    fun open(url: String): Catching<Unit>
}
