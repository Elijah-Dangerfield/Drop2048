package com.dangerfield.drop2048.libraries.achievements.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One row per finished run, append-only (SPEC 11).
 *
 * The facts are stored rather than the counters they add up to. It costs a
 * hundred bytes a run and it buys two things worth far more: a badge shipped in
 * a later release back-fills from history instead of starting everyone at zero,
 * and there is no second copy of the truth to drift from the fold.
 *
 * It overlaps `run_record` on purpose and is not a join onto it. The two tables
 * answer different questions, and a foreign key would have made a badge's
 * evidence disappear the moment a row it never owned did. "Reset progress" (C11)
 * clears both, through the `ClearableDao` the provider in `:libraries:storage:impl`
 * binds — this interface stays a plain Room DAO so the wipe rule lives with the
 * rest of the wipe set rather than as a default method Room has to compile.
 *
 * [key] is the run's identity
 * ([com.dangerfield.drop2048.libraries.achievements.RunOutcome.key]) and carries
 * the unique index, so an insert that arrives twice is ignored rather than
 * counted twice. The surrogate [id] exists because ordering is load-bearing —
 * the fold is a replay — and `ORDER BY id` is that order even when two runs
 * share a timestamp.
 */
@Entity(
    tableName = "achievement_fact",
    indices = [Index(value = ["key"], unique = true)],
)
data class AchievementFactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    /** [com.dangerfield.drop2048.libraries.progress.GameMode] name, never its ordinal. */
    val mode: String,
    val score: Long,
    val level: Int,
    val blocksPlaced: Int,
    val durationMs: Long,
    val highestTier: Int,
    val longestCascade: Int,
    val bursts: Int,
    val merges: Int,
    val boardsCleared: Int,
    val stoneBursts: Int,
    val wildcardBursts: Int,
    val longestDangerRun: Int,
    val dailyStreakDays: Int,
    val endedAt: Long,
)

/**
 * What the player has been told they earned, and when.
 *
 * Derivable from the facts, and stored anyway: this is the record of what has
 * already been *announced*, so a catalog change cannot re-toast a badge somebody
 * earned two months ago. The timestamp is the historical one, taken from the run
 * that crossed the threshold.
 */
@Entity(tableName = "achievement_unlock")
data class AchievementUnlockEntity(
    /** [com.dangerfield.drop2048.libraries.achievements.AchievementId] name. */
    @PrimaryKey val achievementId: String,
    val unlockedAt: Long,
)

@Dao
interface AchievementDao {

    /** Ignores a duplicate [AchievementFactEntity.key] rather than replacing it. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFact(row: AchievementFactEntity)

    @Query("SELECT * FROM achievement_fact ORDER BY id")
    suspend fun facts(): List<AchievementFactEntity>

    @Query("SELECT * FROM achievement_fact ORDER BY id")
    fun observeFacts(): Flow<List<AchievementFactEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUnlocks(rows: List<AchievementUnlockEntity>)

    @Query("SELECT * FROM achievement_unlock")
    suspend fun unlocks(): List<AchievementUnlockEntity>

    @Query("SELECT * FROM achievement_unlock")
    fun observeUnlocks(): Flow<List<AchievementUnlockEntity>>

    @Query("DELETE FROM achievement_fact")
    suspend fun deleteAllFacts()

    @Query("DELETE FROM achievement_unlock")
    suspend fun deleteAllUnlocks()
}
