package com.dangerfield.drop2048.libraries.devfeedback.tester

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.dangerfield.drop2048.libraries.core.Catching
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

internal actual fun ImageBitmap.encodeToJpeg(quality: Int): ByteArray? = Catching {
    Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.JPEG, quality)?.bytes
}.getOrNull()
