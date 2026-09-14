package com.dangerfield.drop2048.libraries.devfeedback.tester

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.dangerfield.drop2048.libraries.core.Catching
import java.io.ByteArrayOutputStream

internal actual fun ImageBitmap.encodeToJpeg(quality: Int): ByteArray? = Catching {
    val stream = ByteArrayOutputStream()
    // A hardware-backed bitmap has no pixel data on the CPU side, so `compress`
    // would fail. Copying to ARGB_8888 first is the documented way round it, and
    // a Compose graphics-layer capture hands back a hardware bitmap on some
    // devices and not others — so this is not a branch that can be tested away.
    val source = asAndroidBitmap()
    val compressible = if (source.config == Bitmap.Config.HARDWARE) {
        source.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        source
    }
    compressible.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    stream.toByteArray()
}.getOrNull()
