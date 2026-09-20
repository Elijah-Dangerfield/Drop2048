package com.dangerfield.drop2048.libraries.ads.impl

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * That `@Serializable` on [AdState] is actually wired, not just written.
 *
 * The twin of `FabStateSerializationTest`, and here for the same reason: this
 * already happened twice. `drop2048.compose.multiplatform` did not apply the
 * kotlinx-serialization compiler plugin, so the annotation compiled, the module
 * compiled, `assembleDebug` was green, and a fresh iOS install died on the main
 * thread with `Serializer for class 'AdState' is not found` before a single
 * frame. `AdStateCacheImpl` is reached from `paywallCoordinator`, which is an
 * `AutoInit`, so it is constructed during app-component creation at boot.
 *
 * The plugin now comes from the convention plugin, so this test is the thing
 * that notices if that is ever undone.
 *
 * The lookup below is the part that matters: `serializer<T>()` is what
 * `versionedJsonSerializer` calls, and it is the call that throws. A test that
 * only built the data class and read its properties would pass in a module with
 * no plugin at all.
 */
class AdStateSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun theStateHasAGeneratedSerializerAndNotJustAnAnnotation() {
        val encoded = json.encodeToString(serializer<AdState>(), AdState())

        assertEquals(AdState(), json.decodeFromString(serializer<AdState>(), encoded))
    }

    @Test
    fun theFourTimestampsSurviveTheRoundTrip() {
        val stored = AdState(
            firstSeenAtMs = 1_700_000_000_000,
            lastInterstitialAtMs = 1_700_000_100_000,
            lastRewardedAtMs = 1_700_000_200_000,
            lastBannerAtMs = 1_700_000_300_000,
        )

        val restored = json.decodeFromString(
            serializer<AdState>(),
            json.encodeToString(serializer<AdState>(), stored),
        )

        assertEquals(stored, restored)
    }

    @Test
    fun aRecordWrittenBeforeTheBannerFieldExistedReadsAsNever() {
        // What a relaunch across the D28 banner change looks like. Every field
        // has a default, so an absent one is not an error, and 0 is the value
        // AdImpressions reads as "no banner has ever been on screen".
        val restored = json.decodeFromString(
            serializer<AdState>(),
            """{"firstSeenAtMs":1700000000000,"lastInterstitialAtMs":0,"lastRewardedAtMs":0}""",
        )

        assertEquals(1_700_000_000_000, restored.firstSeenAtMs)
        assertEquals(0L, restored.lastBannerAtMs)
    }
}
