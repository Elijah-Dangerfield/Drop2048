package com.dangerfield.drop2048.libraries.achievements.impl

import com.dangerfield.drop2048.libraries.achievements.Achievement
import com.dangerfield.drop2048.libraries.achievements.AchievementEngine
import com.dangerfield.drop2048.libraries.achievements.AchievementId
import com.dangerfield.drop2048.libraries.achievements.AchievementState
import com.dangerfield.drop2048.libraries.achievements.Achievements
import com.dangerfield.drop2048.libraries.achievements.AchievementsRepository
import com.dangerfield.drop2048.libraries.achievements.RunFacts
import com.dangerfield.drop2048.libraries.achievements.RunOutcome
import com.dangerfield.drop2048.libraries.achievements.db.AchievementDao
import com.dangerfield.drop2048.libraries.achievements.db.AchievementFactEntity
import com.dangerfield.drop2048.libraries.achievements.db.AchievementUnlockEntity
import com.dangerfield.drop2048.libraries.progress.GameMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Room-backed [AchievementsRepository].
 *
 * There is no in-memory tally. Every read folds the stored fact log from
 * scratch, which sounds wasteful and is not: a run takes minutes, so a heavy
 * player's log is a few thousand rows of scalars and the fold is arithmetic over
 * a list. What it buys is that there is exactly one representation of a player's
 * progress — the facts — so no cached counter can disagree with the history it
 * came from, and a badge added in a later release is simply picked up by the
 * next fold. If the log ever gets long enough to matter, materializing counters
 * behind this interface is a change nobody outside the file can see. This is the
 * same ruling `ProgressRepositoryImpl` makes about `run_record`, for the same
 * reason.
 *
 * The mutex serializes [record], which is a read-modify-write over two tables.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class AchievementsRepositoryImpl(
    private val dao: AchievementDao,
) : AchievementsRepository {

    private val writes = Mutex()

    override fun observe(): Flow<AchievementState> =
        combine(dao.observeFacts(), dao.observeUnlocks()) { facts, unlocks -> stateOf(facts, unlocks) }
            .distinctUntilChanged()

    override suspend fun state(): AchievementState = stateOf(dao.facts(), dao.unlocks())

    /**
     * The fact goes in *first*, then the whole log is re-folded. Doing it in that
     * order is what makes a duplicate record harmless: the unique index drops the
     * second copy, so the counters the fold sees are the counters the player
     * actually earned rather than the ones an at-least-once caller happened to
     * report.
     *
     * What gets announced is measured against the stored unlocks, not against the
     * state before this run. So a badge that a *previous* release did not have,
     * but that this player's history already earns, is granted and celebrated
     * here — with the date of the run that really earned it.
     */
    override suspend fun record(outcome: RunOutcome): List<Achievement> = writes.withLock {
        val announced = dao.unlocks().mapTo(mutableSetOf()) { it.achievementId }
        dao.insertFact(outcome.toEntity())

        val earned = AchievementEngine.replay(dao.facts().map { it.toOutcome() }).unlocked
        val newly = earned.filterKeys { it.name !in announced }
        if (newly.isNotEmpty()) {
            dao.insertUnlocks(newly.map { (id, at) -> AchievementUnlockEntity(id.name, at) })
        }
        Achievements.catalog.filter { it.id in newly }
    }

    override suspend fun reset() {
        writes.withLock {
            dao.deleteAllFacts()
            dao.deleteAllUnlocks()
        }
    }

    private fun stateOf(
        facts: List<AchievementFactEntity>,
        unlocks: List<AchievementUnlockEntity>,
    ): AchievementState = AchievementState(
        counters = AchievementEngine.replay(facts.map { it.toOutcome() }).counters,
        unlocked = unlocks.mapNotNull { row ->
            row.achievementId.toAchievementId()?.let { it to row.unlockedAt }
        }.toMap(),
    )
}

/**
 * An unreadable id means the row was written by a build whose catalog we no
 * longer have — a downgrade, or a badge that was removed. Dropping it is the
 * honest reading: nothing in the catalog can render it, and the fact log still
 * holds everything needed to grant it again if it comes back.
 */
private fun String.toAchievementId(): AchievementId? =
    AchievementId.entries.firstOrNull { it.name == this }

/**
 * An unreadable mode falls back to Endless, matching how `ProgressRepositoryImpl`
 * reads a strange `run_record` row. It is not the conservative choice here —
 * Endless is the mode whose scores climb the score ladder — but the two tables
 * disagreeing about what a row was would be worse than either answer.
 */
private fun String.toGameMode(): GameMode =
    GameMode.entries.firstOrNull { it.name == this } ?: GameMode.ENDLESS

private fun AchievementFactEntity.toOutcome(): RunOutcome = RunOutcome(
    mode = mode.toGameMode(),
    score = score,
    level = level,
    blocksPlaced = blocksPlaced,
    durationMs = durationMs,
    highestTier = highestTier,
    longestCascade = longestCascade,
    bursts = bursts,
    merges = merges,
    facts = RunFacts(
        boardsCleared = boardsCleared,
        stoneBursts = stoneBursts,
        wildcardBursts = wildcardBursts,
        longestDangerRun = longestDangerRun,
    ),
    dailyStreakDays = dailyStreakDays,
    endedAt = endedAt,
)

private fun RunOutcome.toEntity(): AchievementFactEntity = AchievementFactEntity(
    key = key,
    mode = mode.name,
    score = score,
    level = level,
    blocksPlaced = blocksPlaced,
    durationMs = durationMs,
    highestTier = highestTier,
    longestCascade = longestCascade,
    bursts = bursts,
    merges = merges,
    boardsCleared = facts.boardsCleared,
    stoneBursts = facts.stoneBursts,
    wildcardBursts = facts.wildcardBursts,
    longestDangerRun = facts.longestDangerRun,
    dailyStreakDays = dailyStreakDays,
    endedAt = endedAt,
)
