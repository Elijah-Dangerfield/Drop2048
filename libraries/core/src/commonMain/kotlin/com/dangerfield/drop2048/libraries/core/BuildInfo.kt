package com.dangerfield.drop2048.libraries.core

expect object BuildInfo {
    val isDebug: Boolean
    val platform: Platform
    val applicationId: String
    val versionName: String
    val versionCode: Int
    val releaseChannel: String
    val buildNumber: Int

    /** Short git SHA the build was produced from — `GITHUB_SHA` in CI,
     *  `git rev-parse` locally, `"unknown"` when neither is available. */
    val commitSha: String

    /** Branch the build was produced from (`GITHUB_REF_NAME` in CI). */
    val commitBranch: String

    /**
     * Whether this install came from TestFlight rather than the App Store.
     *
     * A **runtime** answer rather than a build flag, because TestFlight and the
     * App Store ship the identical release binary and nothing baked in at
     * compile time can tell them apart. iOS reads the receipt; Android has no
     * equivalent channel and answers false.
     */
    val isTestFlight: Boolean
}

/**
 * Builds whose only audience is the people making the app: a local debug build,
 * and TestFlight.
 *
 * The gate on developer affordances that must never reach a player. Note what
 * it is *not*: `releaseChannel` is a string set from `versions.properties` and
 * an env override, so it describes which artifact this is, not who is holding
 * the phone. A channel named "internal" that someone promotes to production
 * would quietly hand every player a directive button.
 *
 * On Android this is only ever `isDebug`, and the floating button is held out of
 * a release APK by the build rather than by this flag — see `DevFeedback`.
 */
val BuildInfo.isTesterBuild: Boolean get() = BuildInfo.isDebug || BuildInfo.isTestFlight

fun BuildInfo.isiOS() = BuildInfo.platform == Platform.iOS
val BuildInfo.buildType: String get() = if (BuildInfo.isDebug) "debug" else "release"
val BuildInfo.versionTag: String get() = "${BuildInfo.versionName}-${BuildInfo.releaseChannel}"
fun BuildInfo.versionString(): String = "$versionName ($buildNumber)"


enum class Platform {
    Android,
    iOS
}