package com.dangerfield.drop2048.libraries.drop2048.storage.db

/**
 * Every `@Dao` holding wipeable data implements this and is multibound into an
 * `AppScope` set — adding a new DAO is a compile-time wire-up, not a list edit.
 *
 * The template consumed the set from an auth-driven cleaner, which went with
 * the identity stack. Nothing consumes it yet; Settings' "reset progress"
 * (C11) is the intended consumer, and it gets every table for free by
 * injecting `Set<ClearableDao>`.
 */
interface ClearableDao {
    suspend fun deleteAll()
}
