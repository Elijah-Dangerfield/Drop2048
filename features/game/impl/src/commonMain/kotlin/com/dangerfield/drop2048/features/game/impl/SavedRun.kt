package com.dangerfield.drop2048.features.game.impl

import com.dangerfield.drop2048.libraries.cascade.Board
import com.dangerfield.drop2048.libraries.cascade.GameState
import com.dangerfield.drop2048.libraries.achievements.RunFacts
import com.dangerfield.drop2048.libraries.cascade.Transcript
import com.dangerfield.drop2048.libraries.progress.GameMode
import kotlinx.serialization.Serializable

/**
 * Everything about a live run that the engine does not hold.
 *
 * The engine counts drops and carries the level (SPEC 4.1) and deliberately has
 * no clock, so duration, the highest tier *reached* (decision D7) and the
 * cascade tallies SPEC 11 records are accumulated out here as the run happens.
 * They have to survive a resume for the same reason the board does: a run
 * resumed from disk that reports zero merges would write a false `run_record`.
 *
 * [playedMs] is time the run was actually being played, closed off at every
 * pause. See [RunRecord.durationMs][com.dangerfield.drop2048.libraries.progress.RunRecord.durationMs].
 */
@Serializable
data class RunTally(
    val merges: Int = 0,
    val bursts: Int = 0,
    val longestCascade: Int = 0,
    /**
     * SPEC 17's cascades-by-depth, indexed by depth: `cascadesByDepth[2]` is
     * how many locks resolved in exactly two steps. Index 0 is the locks that
     * merged nothing, which is the denominator the rest only mean anything
     * against.
     *
     * Persisted alongside [longestCascade] rather than derived from it,
     * because a histogram is not recoverable from its maximum — and a run
     * resumed from disk that reported only the cascades since the resume would
     * make every long session look like a short one on the dashboard.
     */
    val cascadesByDepth: List<Int> = emptyList(),
    /** Points value of the highest tier reached, 0 before the first merge. */
    val highestTier: Int = 0,
    val playedMs: Long = 0,
    /**
     * SPEC 15's four achievement facts that `run_record` has no column for.
     *
     * It rides here rather than in a second accumulator so a resumed run cannot
     * report a board clear on the stats page and lose the badge for it: one
     * tally, saved at the same moments, restored from the same blob.
     */
    val facts: RunFacts = RunFacts.Empty,
)

/**
 * A resolution caught in the act (SPEC 18.9).
 *
 * The transcript is what the engine returned; [beforeBoard] and [scoreBefore]
 * are what the screen was showing when it started. Those three re-derive every
 * frame through `framesFor`, which is why nothing here stores the frames
 * themselves — they are a pure function of the transcript and already tested as
 * one.
 *
 * [frameIndex] is how far playback had got. It is the same field the ViewModel
 * uses to resume from a pause, so backgrounding mid-cascade and pausing
 * mid-cascade are one code path rather than two that can disagree.
 */
@Serializable
data class SavedResolution(
    val beforeBoard: Board,
    val scoreBefore: Long,
    val transcript: Transcript,
    val frameIndex: Int,
)

/**
 * The in-progress run as it goes to disk (SPEC 11).
 *
 * [seed] and [mode] are carried rather than recovered because they cannot be
 * recovered: the RNG state inside [state] has already advanced past the seed,
 * and `run_record` wants the seed the run started from so the run can be
 * replayed byte for byte.
 *
 * ### [version] is what makes an engine change safe to ship
 *
 * The blob embeds a whole [GameState], so any change to the engine's serial
 * shape can invalidate it. Decision D11 is the first one that actually did:
 * `preview`, `hold` and `holdUsedThisDrop` are gone.
 *
 * The tempting answer is to rely on `ignoreUnknownKeys`, which would have
 * decoded a D11-era blob without complaint — and that is exactly the failure
 * mode worth avoiding. It only works while every change is subtractive, it
 * decides silently, and the first change it cannot absorb (a field that changes
 * *meaning* rather than disappearing) would restore a corrupt run rather than
 * refuse a stale one. A run restored into the wrong rules is worse than a run
 * lost, because nothing on screen says so.
 *
 * So the version is explicit and has **no default**: a blob written before this
 * field existed fails to decode outright, and a blob from a future or past
 * format is refused by [SavedRunStore]. Either way the player loses the run in
 * flight and nothing else — the install id, the onboarding flag and the whole
 * run history live outside this string.
 *
 * **Bump [SAVE_FORMAT_VERSION] whenever `GameState`, `RunTally` or
 * `SavedResolution` change shape.**
 */
@Serializable
data class SavedRun(
    val version: Int,
    val state: GameState,
    val tally: RunTally,
    val seed: Long,
    val mode: GameMode,
    val resolution: SavedResolution? = null,
    /**
     * The UTC day a [GameMode.DAILY] run belongs to, ISO-formatted, null in
     * Endless.
     *
     * Carried rather than recomputed on resume for the reason
     * `DailyRepository.recordAttempt` takes it as a parameter: an attempt started
     * at 23:58 UTC and finished after midnight belongs to the board it began on,
     * and a run restored from disk has no other way to know which that was. A
     * string rather than a `LocalDate` so the blob stays serializer-free, and the
     * same shape the `daily_result` primary key uses.
     */
    val dailyDate: String? = null,
)

/**
 * 6 — C8 added `RunTally.cascadesByDepth`, SPEC 17's cascade-depth histogram.
 *
 * A nullable-free field with a default, so a version 5 blob would decode
 * cleanly and report an empty histogram. It is bumped anyway, for the reason
 * version 3 was: "bump on every shape change" is a rule anyone can check, and
 * "bump only when the change is not backwards compatible" is a judgement call
 * made under deadline by whoever is least likely to be thinking about it. The
 * cost is one in-flight run on the release that lands it.
 *
 * 5 — decision D21 changed `EngineConfig`, which travels inside `GameState`.
 *
 * `nudgeRows` and `speed.softDropMsPerRow` are gone and `scoring.hardDropPerRow`
 * has arrived. A version 4 blob is not garbage — with `ignoreUnknownKeys` it
 * would decode perfectly, drop the two retired keys and take the compiled-in
 * default for the new one — and that is exactly the failure worth refusing: the
 * run would come back under a scoring table it was not played under, with
 * nothing on screen to say so. `aBlobFromTheNudgeBuildIsRefusedRatherThanResumed`
 * carries the real bytes and its own positive control (L35).
 *
 * 4 — C3c split the one save slot in two, one per [GameMode].
 *
 * [SavedRun] itself did not change shape, and this is bumped anyway because the
 * *slot* did. A version 3 blob in `AppData.savedRun` may be a Daily run — that
 * was exactly the bug the split fixes — and reading it back as the Endless save
 * would resume a Daily board under Endless rules on a seed the player did not
 * choose. `SavedRunStore` also checks [SavedRun.mode] against the slot it came
 * out of, so the two guards are independent: one catches the old world, the other
 * catches a future write to the wrong slot.
 *
 * 3 — C6 added [SavedRun.dailyDate].
 *
 * The field is nullable with a default, so a version 2 blob would in fact decode
 * cleanly. It is rejected anyway, and that is the rule rather than an oversight:
 * "bump on every shape change" is checkable, and "bump only when the change is
 * not backwards compatible" is a judgement call made under deadline by whoever
 * is least likely to be wondering about it. The cost of being strict is one
 * in-flight run on the release that lands it.
 *
 * 2 — decision D11 removed `preview`, `hold` and `holdUsedThisDrop` from
 * `GameState`. Version 1 is the shape C4 shipped, which had no version field at
 * all and is therefore rejected by failing to decode.
 */
const val SAVE_FORMAT_VERSION = 6
