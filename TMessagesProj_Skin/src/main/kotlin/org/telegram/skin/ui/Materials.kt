package org.telegram.skin.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import org.telegram.skin.theme.Metal
import org.telegram.skin.theme.Porcelain
import org.telegram.skin.theme.Skin

// The two materials of the skin. Machined aluminium carries everything you
// hold or press; celadon porcelain is set into it wherever something is
// "the current one". Both are drawn, not imaged, so they stay sharp at any
// size and follow the theme.

/** A brushed-aluminium slab: vertical sheen and a lit top edge. Clips to [shape]. */
fun Modifier.metal(metal: Metal, shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(0f to metal.sheenTop, 0.55f to metal.sheenMid, 1f to metal.sheenBottom))
    .drawBehind {
        val px = 1.dp.toPx()
        drawLine(metal.highlight, Offset(0f, px / 2), Offset(size.width, px / 2), px)
        drawLine(metal.grooveCut, Offset(0f, size.height - px / 2), Offset(size.width, size.height - px / 2), px)
    }

/** The same metal lit across the diagonal, for cards that lie flat on the page. */
fun Modifier.metalCard(metal: Metal, shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.linearGradient(0f to metal.sheenTop, 0.5f to metal.sheenMid, 1f to metal.sheenBottom))
    .drawBehind {
        val px = 1.dp.toPx()
        drawLine(metal.highlight, Offset(0f, px / 2), Offset(size.width, px / 2), px)
    }

/**
 * Porcelain glaze sunk into the metal: a recess shadow along the top, a lit
 * lower lip, and, if [crackle] is set, the faint crazing lines of celadon.
 */
fun Modifier.porcelain(porcelain: Porcelain, shape: Shape, crackle: Boolean = true): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(porcelain.glazeTop, porcelain.glazeBottom)))
    .innerShadow(shape, Shadow(radius = 3.dp, offset = DpOffset(0.dp, 2.dp), color = porcelain.recess))
    .drawBehind {
        val px = 1.dp.toPx()
        drawLine(porcelain.lip, Offset(0f, size.height - px / 2), Offset(size.width, size.height - px / 2), px)
        if (crackle) {
            // The crazing is drawn on a 74 x 52 sketch and stretched to fit,
            // while the line itself stays hairline at any size.
            val sx = size.width / 74f
            val sy = size.height / 52f
            val path = Path()
            for (run in CRACKS) {
                path.moveTo(run[0] * sx, run[1] * sy)
                for (i in 2 until run.size step 2) path.lineTo(run[i] * sx, run[i + 1] * sy)
            }
            drawPath(path, porcelain.crackle, style = Stroke(width = 0.8.dp.toPx()))
        }
    }

private val CRACKS = listOf(
    floatArrayOf(8f, 14f, 22f, 20f, 30f, 9f, 44f, 17f, 58f, 11f, 68f, 19f),
    floatArrayOf(22f, 20f, 20f, 34f, 34f, 40f, 44f, 17f),
    floatArrayOf(34f, 40f, 52f, 36f, 58f, 46f),
    floatArrayOf(52f, 36f, 58f, 11f),
)

/** A sunken well in the metal, for text fields. */
fun Modifier.well(color: Color, recess: Color, shape: Shape): Modifier = this
    .clip(shape)
    .background(color)
    .innerShadow(shape, Shadow(radius = 3.dp, offset = DpOffset(0.dp, 2.dp), color = recess))

/** An engraved line: a dark cut with a lit lip beside it. */
@Composable
fun Groove(vertical: Boolean, modifier: Modifier = Modifier) {
    val m = Skin.colors.metal
    Box(
        modifier
            .then(if (vertical) Modifier.width(2.dp).fillMaxHeight() else Modifier.height(2.dp).fillMaxWidth())
            .drawBehind {
                val px = 1.dp.toPx()
                if (vertical) {
                    drawLine(m.grooveCut, Offset(px / 2, 0f), Offset(px / 2, size.height), px)
                    drawLine(m.grooveLip, Offset(px * 1.5f, 0f), Offset(px * 1.5f, size.height), px)
                } else {
                    drawLine(m.grooveCut, Offset(0f, px / 2), Offset(size.width, px / 2), px)
                    drawLine(m.grooveLip, Offset(0f, px * 1.5f), Offset(size.width, px * 1.5f), px)
                }
            },
    )
}

/**
 * The knurled aluminium knob. Its cross turns a quarter-and-a-half to read
 * as a close mark when [turn] reaches 1; any value between is a valid frame,
 * so a caller can animate it and stop anywhere.
 */
@Composable
fun Knob(turn: Float, size: Dp, modifier: Modifier = Modifier) {
    val m = Skin.colors.metal
    // A knurled ring is a sweep of 72 alternating slivers.
    val knurl = Brush.sweepGradient(List(144) { i -> if ((i / 2) % 2 == 0) m.knurlA else m.knurlB })
    Box(modifier.size(size)) {
        Box(
            Modifier
                .matchParentSize()
                .dropShadow(CircleShape, Shadow(radius = 6.dp, offset = DpOffset(0.dp, 2.dp), color = m.castShadow))
                .clip(CircleShape)
                .background(knurl),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(size * 0.78f)
                    .drawBehind {
                        // Lit from the upper left.
                        drawCircle(
                            Brush.radialGradient(
                                listOf(m.capLit, m.capMid, m.capShade),
                                center = Offset(this.size.width * 0.35f, this.size.height * 0.3f),
                                radius = this.size.maxDimension,
                            ),
                        )
                        drawCircle(m.highlight, radius = this.size.minDimension / 2 - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
                    },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Plus, Skin.colors.text, Modifier.rotate(turn * 45f), size = size * 0.4f)
            }
        }
        // The LED sits on the rim, outside the ring's clip.
        Led(on = true, size = 5.dp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = size * 0.12f))
    }
}

/**
 * A status light. Lit, it glows celadon; unlit, it is an empty ring, so the
 * two states differ in shape as well as colour. Switched on it strikes, its
 * glow blooming past full before it settles; switched off it fades with an
 * afterglow while the ring comes back.
 */
@Composable
fun Led(on: Boolean, modifier: Modifier = Modifier, size: Dp = 7.dp) {
    val motion = Skin.motion
    val glow = animateFloatAsState(if (on) 1f else 0f, if (on) motion.ledOn() else motion.ledOff(), label = "led")
    LedFrame({ glow.value }, modifier, size)
}

/**
 * One frame of [Led]: [glow] at 0 is off and at 1 lit; above 1 is the bloom
 * of striking. Read only while drawing, so an animation redraws the light
 * without recomposing anything.
 */
@Composable
fun LedFrame(glow: () -> Float, modifier: Modifier = Modifier, size: Dp = 7.dp) {
    val c = Skin.colors
    Box(
        modifier
            .size(size)
            .drawBehind {
                val g = glow()
                val lit = g.coerceIn(0f, 1f)
                val bloom = ((g - 1f) / 0.2f).coerceIn(0f, 1f)
                val r = this.size.minDimension / 2
                if (lit < 1f) {
                    val w = 1.5.dp.toPx()
                    drawCircle(c.textTertiary.copy(alpha = c.textTertiary.alpha * (1f - lit)), radius = r - w / 2, style = Stroke(w))
                }
                if (lit > 0f) {
                    // A tight glow falling off fast, like the blur it stands for; it
                    // reaches further and brighter while the light blooms.
                    val halo = r * 3.2f * (1f + 0.35f * bloom)
                    val strength = lit * (1f + 0.5f * bloom)
                    drawCircle(
                        Brush.radialGradient(
                            0f to c.led.copy(alpha = (0.5f * strength).coerceAtMost(1f)),
                            r / halo to c.led.copy(alpha = (0.5f * strength).coerceAtMost(1f)),
                            1.35f * r / halo to c.led.copy(alpha = (0.2f * strength).coerceAtMost(1f)),
                            2f * r / halo to c.led.copy(alpha = (0.06f * strength).coerceAtMost(1f)),
                            1f to Color.Transparent,
                            center = center,
                            radius = halo,
                        ),
                        radius = halo,
                    )
                    drawCircle(c.led.copy(alpha = lit), radius = r)
                }
            },
    )
}
