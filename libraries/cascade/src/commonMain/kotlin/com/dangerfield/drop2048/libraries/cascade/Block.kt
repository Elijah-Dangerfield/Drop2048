package com.dangerfield.drop2048.libraries.cascade

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The eleven value tiers, 2 through 2048 (SPEC 5.1).
 *
 * [V2048] is terminal: it cannot exist at rest, because creating it bursts its
 * row. 4096 is unreachable by design, which is why [doubled] is nullable rather
 * than total.
 */
@Serializable
enum class BlockValue(val points: Int) {
    V2(2),
    V4(4),
    V8(8),
    V16(16),
    V32(32),
    V64(64),
    V128(128),
    V256(256),
    V512(512),
    V1024(1024),
    V2048(2048);

    val isTerminal: Boolean get() = this == V2048

    val doubled: BlockValue? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun ofPoints(points: Int): BlockValue? = entries.firstOrNull { it.points == points }

        /** The highest tier whose [points] do not exceed [ceiling], or null if none do. */
        fun atMost(ceiling: Int): BlockValue? = entries.lastOrNull { it.points <= ceiling }
    }
}

/** The three special blocks from SPEC 5.2. They arrive as the falling block only. */
@Serializable
enum class Special {
    WILDCARD,
    BOMB,
    STONE,
}

/** Anything that can occupy a cell. */
@Serializable
sealed interface Block

@Serializable
@SerialName("number")
data class NumberBlock(val value: BlockValue) : Block

@Serializable
@SerialName("special")
data class SpecialBlock(val special: Special) : Block

val Block.numberValue: BlockValue?
    get() = (this as? NumberBlock)?.value

val Block.specialKind: Special?
    get() = (this as? SpecialBlock)?.special

fun blockOf(value: BlockValue): Block = NumberBlock(value)

fun blockOf(special: Special): Block = SpecialBlock(special)
