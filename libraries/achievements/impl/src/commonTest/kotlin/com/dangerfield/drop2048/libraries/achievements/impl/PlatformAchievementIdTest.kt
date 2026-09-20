package com.dangerfield.drop2048.libraries.achievements.impl

import com.dangerfield.drop2048.libraries.achievements.AchievementId
import com.dangerfield.drop2048.libraries.achievements.Achievements
import com.dangerfield.drop2048.libraries.leaderboards.platformAchievementId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Game Center achievement ids, which nothing else can check.
 *
 * This is the only module that can see both catalogs — the badges live in
 * `:libraries:achievements` and the id format lives in `:libraries:leaderboards`,
 * and the dependency runs one way — so it is the only place the two can be held
 * against each other.
 *
 * The failure being guarded is the same one `LeaderboardTest` guards for boards,
 * and it is worse here because there are twenty-two of them: a duplicate id
 * files one badge under another's name, so a player unlocks a badge on their
 * profile that they did not earn and does not get the one they did. Nothing in
 * this app ever reads an achievement back from Game Center, so no other test,
 * screen or log line can ever notice.
 *
 * `theIdsAreStableAgainstTheFormat` is the one that costs something to change,
 * on purpose. These ids are typed into App Store Connect by hand; once a player
 * has earned a badge under one, changing the format orphans it on every profile
 * that has it. So a format change has to be a deliberate edit to a test that
 * spells out what it breaks, rather than a rename that quietly compiles.
 */
class PlatformAchievementIdTest {

    @Test
    fun everyBadgeHasAnId() {
        assertEquals(CatalogSize, AchievementId.entries.size)
        AchievementId.entries.forEach { id ->
            assertTrue(
                platformAchievementId(id.name).isNotBlank(),
                "$id has no platform achievement id",
            )
        }
    }

    @Test
    fun noTwoBadgesShareAnId() {
        assertEquals(CatalogSize, AchievementId.entries.size)
        assertEquals(
            AchievementId.entries.size,
            AchievementId.entries.map { platformAchievementId(it.name) }.toSet().size,
        )
    }

    /**
     * The grid is what the player sees and the enum is what gets reported, so a
     * badge in one and not the other is either an id nobody can earn or an
     * unlock with no tile. Both compile.
     */
    @Test
    fun theCatalogAndTheEnumAgree() {
        assertEquals(
            AchievementId.entries.toSet(),
            Achievements.catalog.map { it.id }.toSet(),
        )
    }

    /**
     * Changing this breaks every badge already earned on every profile. If that
     * is genuinely intended, the App Store Connect entries have to be recreated
     * under the new ids and `docs/OWNER-TODO.md` updated with them.
     */
    @Test
    fun theIdsAreStableAgainstTheFormat() {
        assertEquals(
            "com.dangerfield.drop2048.achievement.FirstMerge",
            platformAchievementId(AchievementId.FirstMerge.name),
        )
        assertEquals(
            "com.dangerfield.drop2048.achievement.FiftyHours",
            platformAchievementId(AchievementId.FiftyHours.name),
        )
    }

    private companion object {
        /** SPEC 15's twenty-two, since D27 cut the two Daily streak badges. */
        const val CatalogSize = 22
    }
}
