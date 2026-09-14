package com.dangerfield.drop2048.libraries.config

import kotlinx.coroutines.flow.Flow

/**
 * Repository responsible for storing and streaming QA/debug config overrides.
 */
interface ConfigOverrideRepository {
    /** Returns the currently persisted overrides, typically from disk-backed storage. */
    fun getOverrides(): List<ConfigOverride<Any>>

    /** Emits override changes so config can be re-merged without restarting the app. */
    fun getOverridesFlow(): Flow<List<ConfigOverride<Any>>>

    /** Persists or updates a single override value for the given path. */
    suspend fun addOverride(override: ConfigOverride<Any>)

    /**
     * Drops the override for [path], handing the key back to remote config and,
     * failing that, to its compiled default.
     *
     * Distinct from writing the default value back. An override *equal to* the
     * default still shadows whatever the console says, so a tester who "put it
     * back" would silently pin the key for the life of the install — which is
     * the same class of bug as a QA build that never sees a config change.
     */
    suspend fun removeOverride(path: String)

    /** Wipes every override. Useful from the QA menu or a debug recovery affordance. */
    suspend fun clearAll()
}
