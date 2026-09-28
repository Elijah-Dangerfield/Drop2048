package com.dangerfield.drop2048.libraries.ui.system

/**
 * WAV, because iOS has no Vorbis decoder.
 *
 * `AVAudioPlayer` refuses an `ogg` outright, and the loader's only symptom is a
 * log line, so the game simply runs silent. That is what shipped before
 * 2026-09-28. Do not "unify" this back to `ogg` on the strength of a macOS
 * test: macOS decodes Vorbis and the device does not.
 */
actual val SoundFileExtension: String = "wav"
