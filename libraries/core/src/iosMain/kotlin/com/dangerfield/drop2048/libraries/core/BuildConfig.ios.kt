package com.dangerfield.drop2048.libraries.core

import com.dangerfield.drop2048.buildinfo.Drop2048BuildConfig
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform as NativePlatform

@OptIn(ExperimentalNativeApi::class)
actual object BuildInfo {
    actual val isDebug: Boolean
        get() = NativePlatform.isDebugBinary

    actual val platform: Platform = Platform.iOS

    actual val applicationId: String
        get() = Drop2048BuildConfig.APPLICATION_ID

    actual val versionName: String
        get() = Drop2048BuildConfig.VERSION_NAME

    actual val versionCode: Int
        get() = Drop2048BuildConfig.VERSION_CODE

    actual val releaseChannel: String
        get() = Drop2048BuildConfig.RELEASE_CHANNEL

    actual val buildNumber: Int
        get() = Drop2048BuildConfig.BUILD_NUMBER

    actual val commitSha: String
        get() = Drop2048BuildConfig.COMMIT_SHA

    actual val commitBranch: String
        get() = Drop2048BuildConfig.COMMIT_BRANCH
}