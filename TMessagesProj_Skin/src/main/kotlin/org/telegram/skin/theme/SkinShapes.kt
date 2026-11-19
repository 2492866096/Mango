package org.telegram.skin.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * Corners grow with the size of the thing they round. A slab you can hold
 * (the dock, a panel) is softer than a chip, and letter avatars are rounded
 * squares rather than circles so they line up with the tiles around them.
 */
@Immutable
data class SkinShapes(
    val dock: RoundedCornerShape = RoundedCornerShape(26.dp),
    val dockCollapsed: RoundedCornerShape = RoundedCornerShape(22.dp),
    /** The dock grown into a panel. */
    val panel: RoundedCornerShape = RoundedCornerShape(30.dp),
    val inlay: RoundedCornerShape = RoundedCornerShape(17.dp),
    val inlayCollapsed: RoundedCornerShape = RoundedCornerShape(14.dp),
    val card: RoundedCornerShape = RoundedCornerShape(18.dp),
    val accountCard: RoundedCornerShape = RoundedCornerShape(24.dp),
    val tile: RoundedCornerShape = RoundedCornerShape(18.dp),
    val bubble: RoundedCornerShape = RoundedCornerShape(20.dp),
    val field: RoundedCornerShape = RoundedCornerShape(14.dp),
    val avatar: RoundedCornerShape = RoundedCornerShape(percent = 34),
    val chip: RoundedCornerShape = RoundedCornerShape(12.dp),
)

/** Spacing on a 4 dp grid. Lists keep a wider 20 dp gutter so text has air. */
@Immutable
data class SkinSpace(
    val hair: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val gutter: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** Gap between the floating dock and the screen edges. */
    val dockInset: Dp = 14.dp,
)

/** One layer of a shadow. Physical objects cast several, so specs are lists. */
@Immutable
data class ShadowLayer(
    val radius: Dp,
    val offset: DpOffset = DpOffset.Zero,
    val spread: Dp = 0.dp,
    val color: Color,
)

/**
 * Shadows tuned per theme. By day they are soft and cool-tinted; at night
 * they are deeper and neutral, because a tinted shadow on graphite reads as
 * a stain.
 */
@Immutable
data class SkinShadows(
    /** Things that float over content: the dock, panels, fanned cards. */
    val floating: List<ShadowLayer>,
    /** Things resting on the page: account card, tiles. */
    val resting: List<ShadowLayer>,
    /** Bubbles and chips. */
    val low: List<ShadowLayer>,
)

private val ink = Color(0xFF1C2226)

val FrostLightShadows = SkinShadows(
    floating = listOf(
        ShadowLayer(radius = 2.dp, offset = DpOffset(0.dp, 1.dp), color = ink.copy(alpha = 0.10f)),
        ShadowLayer(radius = 34.dp, offset = DpOffset(0.dp, 14.dp), color = ink.copy(alpha = 0.16f)),
    ),
    resting = listOf(
        ShadowLayer(radius = 2.dp, offset = DpOffset(0.dp, 1.dp), color = ink.copy(alpha = 0.08f)),
        ShadowLayer(radius = 40.dp, offset = DpOffset(0.dp, 18.dp), color = ink.copy(alpha = 0.14f)),
    ),
    low = listOf(
        ShadowLayer(radius = 2.dp, offset = DpOffset(0.dp, 1.dp), color = ink.copy(alpha = 0.06f)),
        ShadowLayer(radius = 18.dp, offset = DpOffset(0.dp, 6.dp), color = ink.copy(alpha = 0.07f)),
    ),
)

val FrostDarkShadows = SkinShadows(
    floating = listOf(
        ShadowLayer(radius = 34.dp, offset = DpOffset(0.dp, 14.dp), color = Color.Black.copy(alpha = 0.60f)),
    ),
    resting = listOf(
        ShadowLayer(radius = 40.dp, offset = DpOffset(0.dp, 18.dp), color = Color.Black.copy(alpha = 0.55f)),
    ),
    low = listOf(
        ShadowLayer(radius = 20.dp, offset = DpOffset(0.dp, 8.dp), color = Color.Black.copy(alpha = 0.40f)),
    ),
)
