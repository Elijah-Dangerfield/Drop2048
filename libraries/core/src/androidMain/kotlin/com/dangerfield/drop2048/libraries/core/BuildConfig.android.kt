package com.dangerfield.drop2048.libraries.core

import com.dangerfield.drop2048.buildinfo.Drop2048BuildConfig
import com.dangerfield.drop2048.libraries.core.BuildConfig as AndroidBuildConfig

actual object BuildInfo {
    actual val isDebug: Boolean
        get() = AndroidBuildConfig.DEBUG

    actual val platform: Platform
        get() = Platform.Android

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

    /**
     * Android has no TestFlight. Play's internal testing track serves the same
     * artifact as production through the same store, and the install source is
     * not something the app can read reliably, so there is nothing honest to
     * answer here but false.
     */
    actual val isTestFlight: Boolean = false
}