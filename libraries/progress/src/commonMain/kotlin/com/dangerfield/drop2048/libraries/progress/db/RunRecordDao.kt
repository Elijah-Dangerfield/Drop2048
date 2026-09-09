package com.dangerfield.drop2048.libraries.progress.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.dangerfield.drop2048.libraries.drop2048.storage.db.ClearableDao
import kotlinx.coroutines.flow.Flow

/**
 * One row per completed run (SPEC 11). Append-only: a finished run is a fact and
 * nothing later is allowed to edit it.
 *
 * In the api module rather than in `:libraries:storage:impl` because the shared
 * `AppDatabase` has to list the entity and one impl module may not depend on
 * another. `:libraries:storage:impl` provides the DAO; this is where it is
 * declared.
 *
 * [mode] and [cause] are stored by **name**, not ordinal. An ordinal is a
 * position in a source file, so reordering an enum silently rewrites history.
 */
@Entity(tableName = "run_record")
data class RunRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val endedAt: Long,
    val score: Long,
    val level: Int,
    val blocksPlaced: Int,
    val durationMs: Long,
    val highestTier: Int,
    val cause: String,
    val longestCascade: Int,
    val bursts: Int,
    val merges: Int,
    val mode: String,
    val seed: Long,
)

@Dao
interface RunRecordDao : ClearableDao {

    @Insert
    suspend fun insert(row: RunRecordEntity): Long

    @Query("SELECT * FROM run_record ORDER BY endedAt")
    fun observeAll(): Flow<List<RunRecordEntity>>

    @Query("SELECT * FROM run_record ORDER BY endedAt")
    suspend fun all(): List<RunRecordEntity>

    /** Null before the first run, which is why the return type is nullable. */
    @Query("SELECT MAX(score) FROM run_record")
    suspend fun bestScore(): Long?

    @Query("DELETE FROM run_record")
    override suspend fun deleteAll()
}
