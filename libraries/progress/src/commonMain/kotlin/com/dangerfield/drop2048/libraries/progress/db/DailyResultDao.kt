package com.dangerfield.drop2048.libraries.progress.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ClearableDao
import kotlinx.coroutines.flow.Flow

/**
 * One row per UTC day the player started the Daily Challenge (SPEC 11). A day
 * with no row was never opened.
 *
 * In the api module for the same reason as `run_record`: the shared
 * `AppDatabase` in `:libraries:storage:impl` has to list the entity, and one
 * impl module may not depend on another.
 *
 * [date] is the **UTC** ISO date the board belonged to (`2026-09-09`), not an
 * epoch stamp. Text rather than a number because ISO dates sort
 * lexicographically, so `ORDER BY date` is chronological for free, and a support
 * dump of this table is readable without a converter. It is the primary key,
 * which is what makes "one row per day" a property of the database rather than
 * of every call site.
 *
 * Unlike `run_record` this table **is** updated in place, and the difference is
 * deliberate. A `run_record` row is a finished fact; a `daily_result` row is the
 * running state of one day, and it changes twice per attempt — once when the
 * attempt is spent and once when it finishes. Two rows per attempt would make
 * "attempts used" a `COUNT` and "the day's score" a `MAX` over a table that also
 * has to answer "has today been completed", and every one of those questions
 * would then have its own way of being wrong.
 */
@Entity(tableName = "daily_result")
data class DailyResultEntity(
    @PrimaryKey val date: String,
    val seed: Long,
    val score: Long,
    val attemptsUsed: Int,
    val completed: Boolean,
    val retriesUsed: Int,
)

@Dao
interface DailyResultDao : ClearableDao {

    /**
     * Opens a day, and does nothing at all if it is already open.
     *
     * `IGNORE` rather than `REPLACE` is where one-attempt-per-day actually
     * lives: a second opener for the same date cannot reset the counters, even
     * if two writers race.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: DailyResultEntity): Long

    @Query("SELECT * FROM daily_result WHERE date = :date")
    suspend fun forDate(date: String): DailyResultEntity?

    @Query("UPDATE daily_result SET attemptsUsed = attemptsUsed + 1 WHERE date = :date")
    suspend fun spendAttempt(date: String)

    /**
     * Banks a finished attempt: the day is completed, and its score is the better
     * of what is already there and what just happened.
     *
     * `MAX` in SQL rather than a read-modify-write in Kotlin so that two
     * finishes cannot interleave and let the worse one win.
     */
    @Query("UPDATE daily_result SET completed = 1, score = MAX(score, :score) WHERE date = :date")
    suspend fun bankScore(date: String, score: Long)

    @Query("UPDATE daily_result SET retriesUsed = retriesUsed + 1 WHERE date = :date")
    suspend fun spendRetry(date: String)

    @Query("SELECT * FROM daily_result ORDER BY date")
    fun observeAll(): Flow<List<DailyResultEntity>>

    @Query("SELECT * FROM daily_result ORDER BY date")
    suspend fun all(): List<DailyResultEntity>

    @Query("DELETE FROM daily_result")
    override suspend fun deleteAll()
}
