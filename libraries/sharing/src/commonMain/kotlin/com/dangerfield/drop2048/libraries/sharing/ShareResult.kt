package com.dangerfield.drop2048.libraries.sharing

/**
 * What a finished run looks like from the outside: the three numbers worth
 * bragging about, and how long it took.
 *
 * There is no board here, no seed and no transcript, and that is the whole
 * design. A Daily run is one seed shared worldwide (SPEC 14), so a share that
 * carried any of those would be handing the reader a run they have not played
 * yet. The formatter cannot print what it was never given, which makes "a share
 * gives nothing away" a property of this type rather than of care at the call
 * site.
 */
data class ShareResult(
    val score: Long,

    /** Points value of the highest tier *reached* (decision D7), e.g. 1024. */
    val biggestTier: Int,

    /** The deepest single cascade. SPEC 21 says this is what the game is for. */
    val longestCascade: Int,

    val level: Int,

    /** Milliseconds actually spent playing (decision D12). */
    val durationMs: Long,
)

/**
 * The words in a share. Every one of them is resolved by the UI from
 * `:libraries:resources` and handed down, because this module has no business
 * holding English and no way to pluralise a streak for a locale it cannot see.
 *
 * The formatter owns the layout, the emoji and the numbers; the caller owns the
 * language.
 */
data class ShareLabels(
    /** e.g. "Drop 2048 Daily · Sep 10" or "Drop 2048 · Endless". */
    val title: String,

    /** e.g. "🔥 12 day streak". Omitted entirely when null. */
    val streak: String? = null,

    /** The footer line, normally the app's domain. Omitted when null. */
    val footer: String? = null,
)
