package com.dangerfield.drop2048.libraries.progress.impl

import com.dangerfield.drop2048.libraries.progress.GameMode
import com.dangerfield.drop2048.libraries.progress.ProgressRepository
import com.dangerfield.drop2048.libraries.progress.RunRecord
import com.dangerfield.drop2048.libraries.progress.RunStats
import com.dangerfield.drop2048.libraries.progress.db.RunRecordDao
import com.dangerfield.drop2048.libraries.progress.db.RunRecordEntity
import com.dangerfield.drop2048.libraries.progress.statsFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * `run_record` in, SPEC 15 out.
 *
 * The fold runs in Kotlin over the whole table rather than as a page of SQL
 * aggregates. Two reasons, in order: the fold is one function that a test can
 * hand a list of runs and hand-count (which is how SPEC 15 is actually checked),
 * and a stat added later is a line in that function rather than a new query and
 * a new column in a projection nobody remembers to widen. The cost is one full
 * table read per emission — a few thousand rows of scalars, no joins, on a
 * screen the player opens between runs.
 *
 * Revisit if a lifetime ever gets to five figures of runs; the seam is here and
 * nothing above this class knows how the numbers are produced.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class ProgressRepositoryImpl(
    private val dao: RunRecordDao,
) : ProgressRepository {

    override suspend fun record(run: RunRecord) {
        dao.insert(run.toEntity())
    }

    override fun observeStats(): Flow<RunStats> =
        dao.observeAll().map { rows -> statsFrom(rows.map { it.toDomain() }) }

    override suspend fun bestScore(): Long = dao.bestScore() ?: 0
}

private fun RunRecord.toEntity() = RunRecordEntity(
    endedAt = endedAt,
    score = score,
    level = level,
    blocksPlaced = blocksPlaced,
    durationMs = durationMs,
    highestTier = highestTier,
    cause = cause,
    longestCascade = longestCascade,
    bursts = bursts,
    merges = merges,
    mode = mode.name,
    seed = seed,
)

/**
 * A row whose [RunRecordEntity.mode] no longer names a [GameMode] reads as
 * [GameMode.ENDLESS] rather than throwing. The stats page is a lifetime summary;
 * losing every number on it because one old row names a mode this build dropped
 * is a worse outcome than one row being counted in the wrong bucket.
 */
private fun RunRecordEntity.toDomain() = RunRecord(
    id = id,
    endedAt = endedAt,
    score = score,
    level = level,
    blocksPlaced = blocksPlaced,
    durationMs = durationMs,
    highestTier = highestTier,
    cause = cause,
    longestCascade = longestCascade,
    bursts = bursts,
    merges = merges,
    mode = GameMode.entries.firstOrNull { it.name == mode } ?: GameMode.ENDLESS,
    seed = seed,
)
