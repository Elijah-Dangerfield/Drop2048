@file:Suppress("MagicNumber")

package com.dangerfield.drop2048.system

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
class Radius private constructor(val shape: RoundedCornerShape) {
    internal constructor(cornerSize: CornerSize) : this(RoundedCornerShape(cornerSize))

    val cornerSize: CornerSize
        get() = shape.topStart.takeUnless { it == SquareCornerSize }
            ?: shape.topEnd.takeUnless { it == SquareCornerSize }
            ?: shape.bottomEnd.takeUnless { it == SquareCornerSize }
            ?: shape.bottomStart

    override fun equals(other: Any?): Boolean = this === other || other is Radius && shape == other.shape
    override fun hashCode(): Int = shape.hashCode()
    override fun toString(): String = "Radius(cornerSize=$cornerSize)"
}

fun Radius.cornerRadius(density: Density, size: Size): Float {
    return when (val corner = cornerSize) {
        is CornerSize -> {
            // CornerSize can be absolute (Dp) or percentage
            // You need density and size to resolve it
            corner.toPx(size, density)
        }
    }
}

object Radii {
    val Round = Radius(CornerSize(percent = 50))
    val R300 = Radius(CornerSize(DimensionResource.D300.dp))
    val R400 = Radius(CornerSize(DimensionResource.D400.dp))
    val R600 = Radius(CornerSize(DimensionResource.D600.dp))
    val None = Radius(SquareCornerSize)

    val Default get() = None
    val Button get() = Radius(CornerSize(percent = 25))
    val IconButton get() = Round
    val Banner get() = R400
    val Header get() = None
    val Card get() = R400

    /**
     * The handoff's panel radius, between its 18 and 24. Every chunky plate that
     * is not on the board: stat panels, the perk block, the sheet's groups.
     */
    val Panel = Radius(CornerSize(20.dp))

    /**
     * The tile's `border-radius: 22%`, proportional rather than a fixed dp.
     *
     * The one detail that makes a tile read as a sweet rather than as a cell in a
     * table, which is why anything that wants to *look* like a block — a perk
     * chip, a swatch — takes this rather than a dp that happens to match at one
     * size.
     */
    val Tile = Radius(CornerSize(percent = 22))
}


fun Modifier.clip(radius: Radius): Modifier = clip(radius.shape)

private val SquareCornerSize = CornerSize(0.dp)


