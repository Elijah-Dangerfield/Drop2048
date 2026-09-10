package com.dangerfield.drop2048.libraries.achievements

import com.dangerfield.drop2048.libraries.progress.GameMode

/**
 * A finished run that earns nothing at all.
 *
 * Every default is deliberately just outside a threshold — no merges, no bursts,
 * no cascade, level 1, a tier below 64, half a minute on the clock. A test that
 * wants a counter to move says so by overriding exactly the field that moves it,
 * which is what makes the assertions mean anything: if the fold ever started
 * counting the neutral outcome, half the file would fail at once.
 */
fun outcome(
    mode: GameMode = GameMode.ENDLESS,
    score: Long = 0,
    level: Int = 1,
    blocksPlaced: Int = 0,
    durationMs: Long = 30_000,
    highestTier: Int = 32,
    longestCascade: Int = 0,
    bursts: Int = 0,
    merges: Int = 0,
    facts: RunFacts = RunFacts.Empty,
    dailyStreakDays: Int = 0,
    endedAt: Long = 1,
): RunOutcome = RunOutcome(
    mode = mode,
    score = score,
    level = level,
    blocksPlaced = blocksPlaced,
    durationMs = durationMs,
    highestTier = highestTier,
    longestCascade = longestCascade,
    bursts = bursts,
    merges = merges,
    facts = facts,
    dailyStreakDays = dailyStreakDays,
    endedAt = endedAt,
)

fun foldAll(vararg outcomes: RunOutcome): AchievementCounters =
    outcomes.fold(AchievementCounters.Empty) { counters, next -> counters.fold(next) }
