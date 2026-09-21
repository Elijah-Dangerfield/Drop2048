package com.dangerfield.drop2048.libraries.leaderboards.impl

import platform.GameKit.GKAchievement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the one thing about Game Center achievements that no JVM test can see.
 *
 * Both assertions exist because the KDoc on `completedAchievement` used to say
 * GameKit defaults `showsCompletionBanner` to `false`. It defaults to `true`,
 * so every badge on iOS drew Game Center's banner on top of the app's own
 * unlock toast. The comment was the only thing holding the claim, and a comment
 * cannot fail.
 */
class GKAchievementIosTest {

    /**
     * The premise. If Apple ever changes the default, this fails and whoever
     * reads it can decide whether the explicit set below is still needed,
     * rather than discovering the answer from a screenshot in a bug report.
     */
    @Test
    fun gameKitDefaultsToShowingItsOwnBanner() {
        assertTrue(
            GKAchievement(identifier = "com.dangerfield.drop2048.achievement.Probe").showsCompletionBanner,
            "GKAchievement no longer defaults showsCompletionBanner to true. " +
                "Re-read completedAchievement's KDoc before changing it.",
        )
    }

    /** What we actually build, and the reason this file exists. */
    @Test
    fun ourAchievementSuppressesTheGameCenterBanner() {
        val achievement = completedAchievement("com.dangerfield.drop2048.achievement.FirstMerge")

        assertFalse(
            achievement.showsCompletionBanner,
            "Game Center would draw its own banner over the app's unlock toast.",
        )
        assertEquals(100.0, achievement.percentComplete)
        assertEquals("com.dangerfield.drop2048.achievement.FirstMerge", achievement.identifier)
    }
}
