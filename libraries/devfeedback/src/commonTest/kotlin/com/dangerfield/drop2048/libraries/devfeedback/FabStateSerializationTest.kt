package com.dangerfield.drop2048.libraries.devfeedback

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That `@Serializable` on [DevFeedbackFabState] is actually wired, not just
 * written.
 *
 * This is here because it already happened. `drop2048.compose.multiplatform`
 * does not apply the kotlinx-serialization compiler plugin, while
 * `drop2048.kotlin.multiplatform` and `drop2048.feature` both do — so the
 * annotation compiled, the whole app built, `assembleDebug` was green, and the
 * first launch died on the main thread with `Serializer for class
 * 'DevFeedbackFabState' is not found` before a single frame. Nothing short of
 * installing it found that (L56 again).
 *
 * The lookup below is the part that matters: `serializer<T>()` is what
 * `versionedJsonSerializer` calls, and it is the call that throws. A test that
 * only built the data class and read its properties would pass in a module with
 * no plugin at all.
 *
 * The round trip is worth having too, for the reason the state is on disk in the
 * first place: the two floats survive a relaunch, and a field renamed without a
 * migration is a button back at the default with nothing to say why.
 */
class FabStateSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun theStateHasAGeneratedSerializerAndNotJustAnAnnotation() {
        val encoded = json.encodeToString(serializer<DevFeedbackFabState>(), DevFeedbackFabState())

        assertEquals(
            DevFeedbackFabState(),
            json.decodeFromString(serializer<DevFeedbackFabState>(), encoded),
        )
    }

    @Test
    fun aDraggedAndHiddenButtonSurvivesTheRoundTrip() {
        val stored = DevFeedbackFabState(hidden = true, x = 0.25f, y = 0.8f)

        val restored = json.decodeFromString(
            serializer<DevFeedbackFabState>(),
            json.encodeToString(serializer<DevFeedbackFabState>(), stored),
        )

        assertEquals(stored, restored)
        assertEquals(FabPlacement(x = 0.25f, y = 0.8f), restored.placement)
    }

    @Test
    fun anOlderRecordWithNoPositionReadsAsTheDefault() {
        // What a relaunch after a field is added looks like. The fields have
        // defaults so an absent one is not an error, and the default is a
        // placement the button can be tapped at.
        val restored = json.decodeFromString(serializer<DevFeedbackFabState>(), """{"hidden":true}""")

        assertEquals(true, restored.hidden)
        assertEquals(DefaultFabPlacement, restored.placement)
    }
}
