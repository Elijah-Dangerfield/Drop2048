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
 * [cause] is stored by **name**, not ordinal. An ordinal is a position in a
 * source file, so reordering an enum silently rewrites history.
 *
 * Schema version 9 dropped the `mode` column with the Daily Challenge (D27), and
 * dropped the rows that carried `'DAILY'` with it: a Daily score was set on a
 * pinned config and a shared seed, so folding one into a lifetime total or a
 * best score would be counting a different quantity.
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

    /**
     * The headline best: `MAX(score)` over every row, null before the first run.
     *
     * It was filtered to `mode = 'ENDLESS'` while the Daily existed (D19),
     * because a score set on a seed everybody else also played is not the same
     * quantity. D27 removed the mode and the rows it excluded, so the filter has
     * nothing left to exclude.
     */
    @Query("SELECT MAX(score) FROM run_record")
    suspend fun bestScore(): Long?

    @Query("DELETE FROM run_record")
    override suspend fun deleteAll()
}
