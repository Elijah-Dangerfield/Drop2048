package com.dangerfield.drop2048.libraries.devfeedback.tester

import androidx.compose.ui.graphics.ImageBitmap

/**
 * JPEG-encodes a captured frame for upload, or returns null if the platform
 * encoder refuses it.
 *
 * JPEG rather than PNG because a screenshot of a board full of gradients and
 * shadows is photographic enough that PNG multiplies the payload for no visible
 * gain, and this rides on an event that also carries a log dump.
 *
 * Deliberately no downscale pass: the source is already one screen of pixels,
 * and [SCREENSHOT_QUALITY] puts a phone frame in the low hundreds of KB. A
 * resampling step would be more platform code to get wrong than it saves.
 */
internal expect fun ImageBitmap.encodeToJpeg(quality: Int = SCREENSHOT_QUALITY): ByteArray?

/** Readable enough to see what the reporter meant, small enough not to matter. */
internal const val SCREENSHOT_QUALITY: Int = 70

/**
 * A captured frame, held by identity.
 *
 * Not a data class on purpose. The generated `equals` would compare a
 * multi-hundred-KB array element by element every time the panel's state flow
 * emits, and two distinct captures are never meaningfully "equal" anyway.
 */
class Screenshot(val bytes: ByteArray)
