package com.dangerfield.drop2048.features.achievements

import com.dangerfield.drop2048.libraries.achievements.AchievementGroup
import com.dangerfield.drop2048.libraries.achievements.AchievementId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That every badge has its *own* words.
 *
 * The exhaustive `when`s in [AchievementCopy] already make a *missing* entry a
 * compile error, which is the failure worth preventing and the reason the file
 * is shaped that way. What they cannot catch is a copy-paste: two badges pointed
 * at the same resource read as a working screen with a duplicated tile, and it
 * is the mistake a twenty-four-branch `when` invites.
 *
 * The resources are compared by identity rather than resolved. Resolving one
 * needs a composition, and what is being asserted is which key was picked, not
 * what it says.
 */
class AchievementCopyTest {

    @Test
    fun noTwoBadgesShareAName() {
        val names = AchievementId.entries.map { AchievementCopy.name(it) }

        assertEquals(AchievementId.entries.size, names.toSet().size, "two badges share a name")
    }

    @Test
    fun noTwoBadgesShareADescriptionUnlessTheirLadderRungIsTheOnlyDifference() {
        val bodies = AchievementId.entries.map { AchievementCopy.description(it) }

        assertEquals(AchievementId.entries.size, bodies.toSet().size, "two badges share a body")
    }

    @Test
    fun everyGroupHasAHeading() {
        val headings = AchievementGroup.entries.map { AchievementCopy.groupName(it) }

        assertEquals(AchievementGroup.entries.size, headings.toSet().size)
    }

    @Test
    fun noTwoBadgesShareAGlyph() {
        val glyphs = AchievementId.entries.map { AchievementCopy.glyph(it) }

        assertEquals(AchievementId.entries.size, glyphs.toSet().size, "two badges share a glyph")
    }
}
