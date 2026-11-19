package org.telegram.skin.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import org.telegram.skin.motion.SkinIndication
import org.telegram.skin.motion.SkinMotion
import org.telegram.skin.motion.SkinMotionSpecs

private val LocalSkinColors = staticCompositionLocalOf { FrostLight }
private val LocalSkinShadows = staticCompositionLocalOf { FrostLightShadows }
private val LocalSkinType = staticCompositionLocalOf { DefaultSkinType }
private val LocalSkinShapes = staticCompositionLocalOf { SkinShapes() }
private val LocalSkinSpace = staticCompositionLocalOf { SkinSpace() }
private val LocalSkinMotion = staticCompositionLocalOf { SkinMotionSpecs.Full }

/** Read the current tokens anywhere under [SkinTheme]. */
object Skin {
    val colors: SkinColors
        @Composable @ReadOnlyComposable get() = LocalSkinColors.current
    val shadows: SkinShadows
        @Composable @ReadOnlyComposable get() = LocalSkinShadows.current
    val type: SkinType
        @Composable @ReadOnlyComposable get() = LocalSkinType.current
    val shapes: SkinShapes
        @Composable @ReadOnlyComposable get() = LocalSkinShapes.current
    val space: SkinSpace
        @Composable @ReadOnlyComposable get() = LocalSkinSpace.current
    val motion: SkinMotionSpecs
        @Composable @ReadOnlyComposable get() = LocalSkinMotion.current
}

/**
 * Provides the tokens to [content]. Theme and direction are the caller's call:
 * inside the app both follow Telegram's own settings (current theme, chosen
 * language) rather than the system's, so the new screens never disagree with
 * the old ones next to them. Motion follows the system and Telegram's power
 * saving through [SkinMotion]; whatever is pressed without an indication of
 * its own gets the skin's ink wash.
 */
@Composable
fun SkinTheme(dark: Boolean, rtl: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) FrostDark else FrostLight
    val motion = if (SkinMotion.reducedForCompose) SkinMotionSpecs.Reduced else SkinMotionSpecs.Full
    val indication = remember(colors, motion) { SkinIndication(colors.ink, motion) }
    CompositionLocalProvider(
        LocalSkinColors provides colors,
        LocalSkinShadows provides if (dark) FrostDarkShadows else FrostLightShadows,
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalSkinMotion provides motion,
        LocalIndication provides indication,
        content = content,
    )
}

/** Paints each layer of a shadow spec under the content, first layer first. */
fun Modifier.shadows(layers: List<ShadowLayer>, shape: Shape): Modifier =
    layers.fold(this) { modifier, layer ->
        modifier.dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = layer.radius,
                color = layer.color,
                spread = layer.spread,
                offset = layer.offset,
            ),
        )
    }
