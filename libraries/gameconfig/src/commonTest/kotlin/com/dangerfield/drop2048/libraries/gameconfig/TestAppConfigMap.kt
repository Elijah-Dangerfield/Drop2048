package com.dangerfield.drop2048.libraries.gameconfig

import com.dangerfield.drop2048.libraries.config.AppConfigMap

/**
 * A merged config snapshot built from dotted paths, the way the server's sparse
 * override tree arrives after `ConfigJsonConverter` has parsed it: nested maps,
 * plain Kotlin values, no JSON left.
 *
 * Taking dotted keys rather than hand-nested maps keeps a test's intent
 * (`"level.blocksPerLevel" to 0`) one line long, which matters because most of
 * these tests are a table of one bad key each.
 */
internal class TestAppConfigMap(overrides: Map<String, Any?>) : AppConfigMap() {

    override val map: Map<String, *> = overrides.entries.fold(emptyMap<String, Any?>()) { acc, entry ->
        acc.merge(entry.key.split('.'), entry.value)
    }

    private fun Map<String, Any?>.merge(path: List<String>, value: Any?): Map<String, Any?> {
        val head = path.first()
        if (path.size == 1) return this + (head to value)
        @Suppress("UNCHECKED_CAST")
        val child = (this[head] as? Map<String, Any?>) ?: emptyMap()
        return this + (head to child.merge(path.drop(1), value))
    }

    companion object {
        val Empty = TestAppConfigMap(emptyMap())
    }
}

internal fun remoteEngineConfig(map: AppConfigMap) = RemoteEngineConfig(
    boardRows = BoardRows(map),
    blocksPerLevel = BlocksPerLevel(map),
    spawnCapDivisor = SpawnCapDivisor(map),
    spawnTable = SpawnTableValue(map),
    speedCurve = SpeedCurveMsPerRow(map),
    speedFloorMs = SpeedFloorMs(map),
    speedTailStepMs = SpeedTailStepMs(map),
    wildcardPerMille = WildcardPerMille(map),
    wildcardFirstLevel = WildcardFirstLevel(map),
    bombPerMille = BombPerMille(map),
    bombFirstLevel = BombFirstLevel(map),
    stonePerMille = StonePerMille(map),
    stoneFirstLevel = StoneFirstLevel(map),
)
