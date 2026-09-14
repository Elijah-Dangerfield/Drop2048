package com.dangerfield.drop2048.features.debug.impl

import com.dangerfield.drop2048.libraries.ads.AdGateSnapshot
import com.dangerfield.drop2048.libraries.ads.AdPlacement
import com.dangerfield.drop2048.libraries.ads.AdShowResult
import com.dangerfield.drop2048.features.debug.PresetBoard
import com.dangerfield.drop2048.libraries.cascade.Block
import com.dangerfield.drop2048.libraries.cascade.numberValue
import com.dangerfield.drop2048.libraries.cascade.specialKind

/**
 * Every word on the debug menu, as plain constants.
 *
 * **Deliberately not in `composeResources`.** This screen is unreachable without
 * seven taps and, on a release build, a passphrase; nobody who does not work on
 * the game will ever see it, so there is nothing here for a translator to
 * translate and every string added would be a real string in every locale file
 * forever. The repo rule to check against is the string *baseline*, which is at
 * nine and is not grown by any of this: `VerifyStrings` fails on a literal passed
 * directly to a `Text(...)`, and these are named values.
 */
object DebugCopy {
    const val Title = "Debug menu"

    const val SessionBannerTitle = "Debug session"
    const val SessionBannerBody =
        "Nothing this launch will be recorded: no run history, no Daily result, " +
            "no leaderboard score. Relaunch the app to record again."

    const val PassphraseTitle = "Release build"
    const val PassphraseBody = "The debug menu needs a passphrase on a release build."
    const val PassphraseLabel = "Passphrase"
    const val PassphraseSubmit = "Unlock"
    const val PassphraseWrong = "Not that one."

    const val SectionBoard = "Board"
    const val SectionSimulation = "Simulation"
    const val SectionEconomy = "Economy and entitlements"
    const val SectionRng = "RNG"
    const val SectionDiagnostics = "Diagnostics overlay"
    const val SectionState = "State"
    const val SectionTranscript = "Last resolution"

    const val Preset = "Preset board"
    const val PresetNone = "None"
    const val StartLevel = "Start level"
    const val TickInterval = "Tick interval"
    const val TickIntervalHint = "Milliseconds per row, replacing the speed curve for this run"
    const val TickIntervalDefault = "Curve"
    const val ForcedBlocks = "Forced blocks"
    const val ForcedBlocksHint = "Handed out in order, one per spawn"
    const val ClearQueue = "Clear the queue"
    const val QueueEmpty = "Empty"

    const val Invincible = "Invincible"
    const val InvincibleHint = "A stacked-out board keeps playing"
    const val FreezeTimer = "Freeze the drop timer"
    const val FreezeTimerHint = "Gravity stops; the block waits for an input"

    const val Soak = "Autoplay soak"
    const val SoakHint = "tools/balance's own policies, so the numbers match the harness"
    const val SoakRunning = "Playing..."
    const val SoakCheats = "reads a block the player cannot see"

    const val StartRun = "Start run"
    const val ClearOverrides = "Clear all"
    const val NoOverrides = "Nothing forced. A run started now is a normal run."

    const val Seed = "Seed"
    const val SeedHint = "The next run starts here. Empty draws a fresh one."
    const val CopySeed = "Copy the seed"
    const val RandomSeed = "Roll a new seed"

    const val ProGranted = "Pro granted"
    const val ProGrantedHint = "Every Pro perk behaves as it will for a buyer, until you relaunch"
    const val ResetPurchases = "Reset IAP state"
    const val ClearDaily = "Clear the Daily ledger"
    const val ClearDailyHint = "Every day's result, and the streak with it"
    const val DailyStreak = "Daily streak"

    const val ShowFrameRate = "Frame rate"
    const val ShowTick = "Actual vs intended tick"
    const val ShowCoordinates = "Cell coordinates"
    const val ShowMergeArrows = "Merge priority arrows"
    const val ShowTranscript = "Resolution log"

    const val ResetTutorial = "Reset the tutorial"
    const val ResetTutorialHint = "The next launch runs it again"
    const val ResetLocalData = "Reset all local data"
    const val ResetLocalDataHint = "Every table, and both in-flight runs"
    const val DumpState = "Dump app state as JSON"
    const val CopyDump = "Copy the dump"
    const val ForceCrash = "Force a test crash"
    const val ForceCrashHint = "Throws immediately. The app will die."

    const val TranscriptEmpty = "Nothing has resolved yet this launch."

    const val SectionAds = "Advertising"
    const val HouseAds = "House ads"
    const val HouseAdsHint = "A placeholder that always fills, drawn by the app. Debug builds only."
    const val HouseAdsUnavailable = "Not in this build. Ads go to the platform network."
    const val ForcedOutcome = "Force the next answer"
    const val ForcedOutcomeHint = "Skips the placeholder and answers this instead"
    const val ForcedOutcomeNone = "Draw it"
    const val ReportsReady = "Report an interstitial as preloaded"
    const val ReportsReadyHint = "Off is SPEC 12's \"skipped silently if not ready\""
    const val NoteRunFinished = "Count a finished run"
    const val NoteRunFinishedHint = "Feeds the fourth-run rule without playing four runs"
    const val ShowInterstitial = "Force show: interstitial"
    const val ShowInterstitialHint = "Through every gate in SPEC 12.3, so a refusal is the point"
    const val ShowRewardedContinue = "Force show: rewarded (continue)"
    const val ShowRewardedDailyRetry = "Force show: rewarded (Daily retry)"
    const val AdShowing = "Showing..."
    const val AdShown = "shown"
    const val AdNotShown = "not shown"
    const val LastAdResult = "Last result"
    const val AdGateInputs = "Gate inputs"
    const val AdWouldShow = "Nothing is blocking it."
    const val ConfigOverrides = "Config overrides"
    const val ConfigOverridesHint = "Every remote key, editable here. Survives a relaunch."

    const val ConfigTitle = "Config overrides"
    const val ConfigBody =
        "Local overrides shadow remote config and the compiled default, and they survive a " +
            "relaunch. Clear them before trusting anything this device tells you."
    const val ConfigClearAll = "Clear all overrides"
    const val ConfigSet = "Set"
    const val ConfigReset = "Remove the override"
    const val ConfigOverridden = "Overridden locally"

    const val MessageConfigCleared = "Config overrides cleared."
    const val MessageConfigNotParseable = "That is not a value this key can hold."

    const val MessageOverridesCleared = "Overrides cleared."
    const val MessageDailyCleared = "Daily ledger cleared."
    const val MessageTutorialReset = "Tutorial reset."
    const val MessageLocalDataReset = "Local data reset."

    fun configDefault(value: String): String = "Default: $value"

    fun overriddenCount(count: Int): String = when (count) {
        0 -> "No local overrides. This device resolves what everyone else does."
        1 -> "1 local override in force."
        else -> "$count local overrides in force."
    }

    fun outcomeLabel(result: AdShowResult?): String = result?.name ?: ForcedOutcomeNone

    fun placementLabel(placement: AdPlacement): String = when (placement) {
        AdPlacement.ContinueRun -> ShowRewardedContinue
        AdPlacement.DailyRetry -> ShowRewardedDailyRetry
    }

    /**
     * SPEC 19's two readouts and the six inputs beside them, on one line each.
     *
     * The cooldown is printed as *remaining* rather than as elapsed, because
     * "142s since the last one" needs the tester to remember the configured
     * window and "38s remaining" does not.
     */
    fun gateInputs(snapshot: AdGateSnapshot): List<String> = listOf(
        "network: ${snapshot.networkName}",
        "ads.enabled: ${snapshot.adsEnabled}",
        "pro: ${snapshot.isPro}",
        "run alive: ${snapshot.runAlive}",
        "runs this session: ${snapshot.runsThisSession} (need ${snapshot.minSessionRuns})",
        "days since install: ${snapshot.daysSinceInstall} (suppressed below ${snapshot.suppressDaysSinceInstall})",
        "cooldown remaining: ${snapshot.cooldownRemainingSeconds}s of ${snapshot.cooldownSeconds}s",
        "since rewarded: ${snapshot.secondsSinceRewarded ?: "never"} (gap ${snapshot.rewardedGapSeconds}s, in flight ${snapshot.rewardedInFlight})",
        "interstitial preloaded: ${snapshot.interstitialPreloaded}",
    )

    fun presetLabel(preset: PresetBoard): String = when (preset) {
        PresetBoard.Empty -> "Empty"
        PresetBoard.NearlyFull -> "Nearly full"
        PresetBoard.OneAwayFrom2048 -> "One away from 2048"
        PresetBoard.CascadeChain -> "Cascade chain test"
        PresetBoard.StoneHeavy -> "Stone heavy"
        PresetBoard.Danger -> "Danger state"
    }

    /** A block's face, the way the board draws it: the number, or the special's initial. */
    fun blockLabel(block: Block): String =
        block.numberValue?.points?.toString()
            ?: block.specialKind?.name?.take(1)
            ?: "?"
}
