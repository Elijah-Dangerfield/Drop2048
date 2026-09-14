package com.dangerfield.drop2048.libraries.devfeedback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a player's build can contain of the directive channel, asserted from the
 * module every build does contain.
 *
 * ### What this test can and cannot see
 *
 * It cannot see a release build. A unit test runs on the debug variant by
 * construction, and `BuildInfo.isTesterBuild` reads an `expect object`; a test
 * that flipped it would be testing a mock of the thing whose honesty is the
 * point.
 *
 * The real guarantee is structural and is checked elsewhere, in three legs (the
 * shape L78 established):
 *
 * 1. `:apps:compose:verifyNoDevFeedbackInRelease` resolves the release runtime
 *    classpath at build time and fails if `:libraries:devfeedback:tester` is on
 *    it. That is the leg that catches `debugImplementation` being changed to
 *    `implementation`, which is a one-word edit in a file nobody reads twice.
 * 2. `:libraries:devfeedback:tester` is a `debugImplementation`, so anvil has no
 *    `DevFeedback` to bind in a release compilation but [NoDevFeedback], and the
 *    button, the panel, the graphics-layer recording and the JPEG encoder are
 *    not classes in the artifact.
 * 3. `BuildInfo.isTesterBuild` at the top of `DevFeedbackHost.Host`. **iOS only
 *    gets this one**, because Kotlin/Native has no build-type source sets, so
 *    the tester module sits in `iosMain` and links into every iOS binary. That
 *    is also what makes TestFlight work, so it is a deliberate gap rather than
 *    an unclosed one.
 *
 * What is left for a test is the **shape of the seam**, and it is worth pinning
 * because the seam is the part a release build really does run: one call,
 * wrapped around every frame of the app. The failure this catches is somebody
 * adding `val isShown: StateFlow<Boolean>` to `DevFeedback` so that some other
 * screen can ask — which would put the channel's state into a player's binary,
 * quietly, with nothing else going red.
 */
class DevFeedbackReleaseSafetyTest {

    @Test
    fun `the seam declares one member and it takes the app as its content`() {
        val declared = DevFeedback::class.java.declaredMethods.map { it.name }

        assertEquals(
            listOf("Host"),
            declared,
            "DevFeedback grew a member. Everything on this interface exists in a player's " +
                "binary and is answered there by NoDevFeedback, so a state flow added here is " +
                "the directive channel's state shipping to people who cannot see it.",
        )
    }

    @Test
    fun `the answer a player's build binds holds no state at all`() {
        // `$stable` is the Compose compiler's own stability marker, emitted on
        // any class in a module with the plugin applied. Dropping every `$` name
        // rather than that one by name, because the compiler is free to add
        // more and none of them is something a person wrote.
        val fields = NoDevFeedback::class.java.declaredFields
            .filterNot { it.isSynthetic || '$' in it.name }
            .map { it.name }

        assertTrue(
            fields.isEmpty(),
            "NoDevFeedback has fields: $fields. A reviewer asking whether this could ship " +
                "switched on should be able to read four lines and stop.",
        )
    }
}
