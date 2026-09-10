package com.dangerfield.drop2048.system.color

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import com.dangerfield.drop2048.libraries.ui.system.LocalContentColor
import com.dangerfield.drop2048.libraries.ui.system.color.ColorResource

@Immutable
@Suppress("LongParameterList")
interface Colors {

    val accentPrimary: ColorResource
    val onAccentPrimary: ColorResource
    val accentSecondary: ColorResource
    val onAccentSecondary: ColorResource

    /* Backgrounds */
    val shadow: ColorResource
    val background: ColorResource
    val backgroundOverlay: ColorResource
    val onBackground: ColorResource
    val border: ColorResource

    val borderSecondary: ColorResource
    val borderDisabled: ColorResource

    /* Texts */
    val text: ColorResource
    val textSecondary: ColorResource
    val textDisabled: ColorResource
    val danger: ColorResource

    val status: StatusColor

    /* Surfaces */
    val surfacePrimary: ColorResource
    val onSurfacePrimary: ColorResource
    val surfaceSecondary: ColorResource
    val onSurfaceSecondary: ColorResource
    val surfaceTertiary: ColorResource
    val onSurfaceTertiary: ColorResource

    val surfaceDisabled: ColorResource
    val onSurfaceDisabled: ColorResource

}

interface StatusColor {
    val okay: ColorResource
    val warning: ColorResource
    val bad: ColorResource
}

val defaultColors = object : Colors {
    // Blue as primary accent - like a clear sky
    override val accentPrimary = ColorResource.Blue600
    override val onAccentPrimary = ColorResource.White
    // Purple as secondary - adds a touch of creativity and calm
    override val accentSecondary = ColorResource.Purple600
    override val onAccentSecondary = ColorResource.White

    override val shadow = ColorResource.Black_A30
    override val textDisabled = ColorResource.Gray400
    override val danger = ColorResource.Red600
    // White surfaces for a clean, modern look
    override val surfacePrimary = ColorResource.White
    override val surfaceDisabled = ColorResource.Gray200
    override val onSurfacePrimary = ColorResource.Gray900
    override val surfaceSecondary = ColorResource.Gray100
    override val onSurfaceSecondary = ColorResource.Gray800
    override val surfaceTertiary = ColorResource.Gray200
    override val onSurfaceTertiary = ColorResource.Gray700
    override val onSurfaceDisabled = ColorResource.Gray400
    // Light gray background for a soft, neutral canvas
    override val background = ColorResource.Gray50
    override val onBackground = ColorResource.Gray900
    override val border = ColorResource.Gray300
    override val borderSecondary = ColorResource.Gray400
    override val borderDisabled = ColorResource.Gray200
    // Dark gray text on light backgrounds for high readability
    override val text = ColorResource.Gray900
    override val backgroundOverlay = ColorResource.Black_A70
    override val textSecondary = ColorResource.Gray600

    override val status = object : StatusColor {
        override val okay = ColorResource.Green600
        override val warning = ColorResource.Amber600
        override val bad = ColorResource.Red600
    }
}


@Composable
fun ProvideContentColor(color: ColorResource, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalContentColor provides color,
        androidx.compose.material3.LocalContentColor provides color.color,
        content = content
    )
}