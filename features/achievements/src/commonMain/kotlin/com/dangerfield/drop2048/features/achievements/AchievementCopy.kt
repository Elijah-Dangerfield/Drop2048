package com.dangerfield.drop2048.features.achievements

import com.dangerfield.drop2048.libraries.achievements.AchievementGroup
import com.dangerfield.drop2048.libraries.achievements.AchievementId
import drop2048.libraries.resources.generated.resources.Res
import drop2048.libraries.resources.generated.resources.achievement_big_run_body
import drop2048.libraries.resources.generated.resources.achievement_big_run_name
import drop2048.libraries.resources.generated.resources.achievement_chain_of_five_body
import drop2048.libraries.resources.generated.resources.achievement_chain_of_five_name
import drop2048.libraries.resources.generated.resources.achievement_chain_of_ten_body
import drop2048.libraries.resources.generated.resources.achievement_chain_of_ten_name
import drop2048.libraries.resources.generated.resources.achievement_clean_sweep_body
import drop2048.libraries.resources.generated.resources.achievement_clean_sweep_name
import drop2048.libraries.resources.generated.resources.achievement_double_burst_body
import drop2048.libraries.resources.generated.resources.achievement_double_burst_name
import drop2048.libraries.resources.generated.resources.achievement_fifty_hours_body
import drop2048.libraries.resources.generated.resources.achievement_fifty_hours_name
import drop2048.libraries.resources.generated.resources.achievement_first_burst_body
import drop2048.libraries.resources.generated.resources.achievement_first_burst_name
import drop2048.libraries.resources.generated.resources.achievement_first_figures_body
import drop2048.libraries.resources.generated.resources.achievement_first_figures_name
import drop2048.libraries.resources.generated.resources.achievement_first_merge_body
import drop2048.libraries.resources.generated.resources.achievement_first_merge_name
import drop2048.libraries.resources.generated.resources.achievement_five_hours_body
import drop2048.libraries.resources.generated.resources.achievement_five_hours_name
import drop2048.libraries.resources.generated.resources.achievement_five_hundred_blocks_body
import drop2048.libraries.resources.generated.resources.achievement_five_hundred_blocks_name
import drop2048.libraries.resources.generated.resources.achievement_level_twenty_body
import drop2048.libraries.resources.generated.resources.achievement_level_twenty_name
import drop2048.libraries.resources.generated.resources.achievement_monster_run_body
import drop2048.libraries.resources.generated.resources.achievement_monster_run_name
import drop2048.libraries.resources.generated.resources.achievement_on_the_brink_body
import drop2048.libraries.resources.generated.resources.achievement_on_the_brink_name
import drop2048.libraries.resources.generated.resources.achievement_one_hour_body
import drop2048.libraries.resources.generated.resources.achievement_one_hour_name
import drop2048.libraries.resources.generated.resources.achievement_seven_days_body
import drop2048.libraries.resources.generated.resources.achievement_seven_days_name
import drop2048.libraries.resources.generated.resources.achievement_sharp_run_body
import drop2048.libraries.resources.generated.resources.achievement_sharp_run_name
import drop2048.libraries.resources.generated.resources.achievement_sixty_four_body
import drop2048.libraries.resources.generated.resources.achievement_sixty_four_name
import drop2048.libraries.resources.generated.resources.achievement_solid_run_body
import drop2048.libraries.resources.generated.resources.achievement_solid_run_name
import drop2048.libraries.resources.generated.resources.achievement_stone_cold_body
import drop2048.libraries.resources.generated.resources.achievement_stone_cold_name
import drop2048.libraries.resources.generated.resources.achievement_ten_hours_body
import drop2048.libraries.resources.generated.resources.achievement_ten_hours_name
import drop2048.libraries.resources.generated.resources.achievement_thirty_days_body
import drop2048.libraries.resources.generated.resources.achievement_thirty_days_name
import drop2048.libraries.resources.generated.resources.achievement_twenty_five_hours_body
import drop2048.libraries.resources.generated.resources.achievement_twenty_five_hours_name
import drop2048.libraries.resources.generated.resources.achievement_wild_finish_body
import drop2048.libraries.resources.generated.resources.achievement_wild_finish_name
import drop2048.libraries.resources.generated.resources.achievements_group_chains
import drop2048.libraries.resources.generated.resources.achievements_group_daily
import drop2048.libraries.resources.generated.resources.achievements_group_dedication
import drop2048.libraries.resources.generated.resources.achievements_group_endurance
import drop2048.libraries.resources.generated.resources.achievements_group_firsts
import drop2048.libraries.resources.generated.resources.achievements_group_rare
import drop2048.libraries.resources.generated.resources.achievements_group_score
import org.jetbrains.compose.resources.StringResource

/**
 * The words and the glyph for every badge.
 *
 * This file is the other half of the deal `AchievementId` makes: the catalog
 * carries stable ids and no display copy, and adding an entry to it fails
 * *here*, at compile time, until somebody writes the words. The `when`s below
 * have no `else` for exactly that reason — an `else` would turn a missing
 * translation back into a runtime problem, which is the failure mode the split
 * exists to prevent.
 *
 * It lives in the feature's **api** module rather than its impl because
 * `:features:game:impl` needs a badge's name for the unlock toast, and a feature
 * impl may only depend on another feature's api.
 *
 * Every description that quotes a number takes the achievement's own target as
 * its first format argument, and the screen passes `Achievement.target` in. That
 * is not tidiness: five of the twenty-four targets are derived from SPEC 7's
 * scoring coefficients rather than typed (see `ScoreLadder`), so copy that wrote
 * the number out would go stale the first time a coefficient moved, and would go
 * stale silently.
 */
object AchievementCopy {

    fun name(id: AchievementId): StringResource = when (id) {
        AchievementId.FirstMerge -> Res.string.achievement_first_merge_name
        AchievementId.SixtyFour -> Res.string.achievement_sixty_four_name
        AchievementId.FirstBurst -> Res.string.achievement_first_burst_name
        AchievementId.ChainOfFive -> Res.string.achievement_chain_of_five_name
        AchievementId.ChainOfTen -> Res.string.achievement_chain_of_ten_name
        AchievementId.DoubleBurst -> Res.string.achievement_double_burst_name
        AchievementId.LevelTwenty -> Res.string.achievement_level_twenty_name
        AchievementId.FiveHundredBlocks -> Res.string.achievement_five_hundred_blocks_name
        AchievementId.OnTheBrink -> Res.string.achievement_on_the_brink_name
        AchievementId.CleanSweep -> Res.string.achievement_clean_sweep_name
        AchievementId.StoneCold -> Res.string.achievement_stone_cold_name
        AchievementId.WildFinish -> Res.string.achievement_wild_finish_name
        AchievementId.FirstFigures -> Res.string.achievement_first_figures_name
        AchievementId.SolidRun -> Res.string.achievement_solid_run_name
        AchievementId.SharpRun -> Res.string.achievement_sharp_run_name
        AchievementId.BigRun -> Res.string.achievement_big_run_name
        AchievementId.MonsterRun -> Res.string.achievement_monster_run_name
        AchievementId.SevenDays -> Res.string.achievement_seven_days_name
        AchievementId.ThirtyDays -> Res.string.achievement_thirty_days_name
        AchievementId.OneHour -> Res.string.achievement_one_hour_name
        AchievementId.FiveHours -> Res.string.achievement_five_hours_name
        AchievementId.TenHours -> Res.string.achievement_ten_hours_name
        AchievementId.TwentyFiveHours -> Res.string.achievement_twenty_five_hours_name
        AchievementId.FiftyHours -> Res.string.achievement_fifty_hours_name
    }

    fun description(id: AchievementId): StringResource = when (id) {
        AchievementId.FirstMerge -> Res.string.achievement_first_merge_body
        AchievementId.SixtyFour -> Res.string.achievement_sixty_four_body
        AchievementId.FirstBurst -> Res.string.achievement_first_burst_body
        AchievementId.ChainOfFive -> Res.string.achievement_chain_of_five_body
        AchievementId.ChainOfTen -> Res.string.achievement_chain_of_ten_body
        AchievementId.DoubleBurst -> Res.string.achievement_double_burst_body
        AchievementId.LevelTwenty -> Res.string.achievement_level_twenty_body
        AchievementId.FiveHundredBlocks -> Res.string.achievement_five_hundred_blocks_body
        AchievementId.OnTheBrink -> Res.string.achievement_on_the_brink_body
        AchievementId.CleanSweep -> Res.string.achievement_clean_sweep_body
        AchievementId.StoneCold -> Res.string.achievement_stone_cold_body
        AchievementId.WildFinish -> Res.string.achievement_wild_finish_body
        AchievementId.FirstFigures -> Res.string.achievement_first_figures_body
        AchievementId.SolidRun -> Res.string.achievement_solid_run_body
        AchievementId.SharpRun -> Res.string.achievement_sharp_run_body
        AchievementId.BigRun -> Res.string.achievement_big_run_body
        AchievementId.MonsterRun -> Res.string.achievement_monster_run_body
        AchievementId.SevenDays -> Res.string.achievement_seven_days_body
        AchievementId.ThirtyDays -> Res.string.achievement_thirty_days_body
        AchievementId.OneHour -> Res.string.achievement_one_hour_body
        AchievementId.FiveHours -> Res.string.achievement_five_hours_body
        AchievementId.TenHours -> Res.string.achievement_ten_hours_body
        AchievementId.TwentyFiveHours -> Res.string.achievement_twenty_five_hours_body
        AchievementId.FiftyHours -> Res.string.achievement_fifty_hours_body
    }

    /** The heading over one shelf of the grid. */
    fun groupName(group: AchievementGroup): StringResource = when (group) {
        AchievementGroup.Firsts -> Res.string.achievements_group_firsts
        AchievementGroup.Chains -> Res.string.achievements_group_chains
        AchievementGroup.Endurance -> Res.string.achievements_group_endurance
        AchievementGroup.Rare -> Res.string.achievements_group_rare
        AchievementGroup.Score -> Res.string.achievements_group_score
        AchievementGroup.Daily -> Res.string.achievements_group_daily
        AchievementGroup.Dedication -> Res.string.achievements_group_dedication
    }

    /**
     * The badge's face. A glyph rather than a string resource, because there is
     * nothing here to translate and twenty-four emoji in `strings.xml` would be
     * twenty-four rows of noise for a translator to skip past. Placeholder art
     * until real badge illustrations land.
     */
    @Suppress("CyclomaticComplexMethod")
    fun glyph(id: AchievementId): String = when (id) {
        AchievementId.FirstMerge -> "🧩"
        AchievementId.SixtyFour -> "🧱"
        AchievementId.FirstBurst -> "💥"
        AchievementId.ChainOfFive -> "⛓"
        AchievementId.ChainOfTen -> "🌀"
        AchievementId.DoubleBurst -> "💣"
        AchievementId.LevelTwenty -> "⬆️"
        AchievementId.FiveHundredBlocks -> "📦"
        AchievementId.OnTheBrink -> "🩸"
        AchievementId.CleanSweep -> "🧹"
        AchievementId.StoneCold -> "🪨"
        AchievementId.WildFinish -> "🃏"
        AchievementId.FirstFigures -> "🔢"
        AchievementId.SolidRun -> "🥉"
        AchievementId.SharpRun -> "🥈"
        AchievementId.BigRun -> "🥇"
        AchievementId.MonsterRun -> "👑"
        AchievementId.SevenDays -> "📅"
        AchievementId.ThirtyDays -> "🗓️"
        AchievementId.OneHour -> "⏱️"
        AchievementId.FiveHours -> "🕔"
        AchievementId.TenHours -> "🕙"
        AchievementId.TwentyFiveHours -> "🌙"
        AchievementId.FiftyHours -> "🏔️"
    }
}
