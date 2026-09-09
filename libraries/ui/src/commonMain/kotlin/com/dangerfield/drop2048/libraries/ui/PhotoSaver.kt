package com.dangerfield.drop2048.libraries.ui

interface PhotoSaver {
    suspend fun savePhoto(photoData: ByteArray): String?
}
