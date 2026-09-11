package com.dangerfield.drop2048.features.debug.impl

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

    const val MessageOverridesCleared = "Overrides cleared."
    const val MessageDailyCleared = "Daily ledger cleared."
    const val MessageTutorialReset = "Tutorial reset."
    const val MessageLocalDataReset = "Local data reset."

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
