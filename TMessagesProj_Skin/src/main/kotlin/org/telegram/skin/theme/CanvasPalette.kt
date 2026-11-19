package org.telegram.skin.theme

import androidx.compose.ui.graphics.toArgb

/**
 * The skin's materials as plain ARGB ints, for Telegram screens that keep
 * their own Canvas drawing and are only restyled. They read the same tokens
 * as the Compose pages, so a restyled screen and a rebuilt one match.
 */
class CanvasMaterials internal constructor(c: SkinColors, shadows: SkinShadows) {
    @JvmField val dark = c.isDark

    @JvmField val sheenTop = c.metal.sheenTop.toArgb()
    @JvmField val sheenMid = c.metal.sheenMid.toArgb()
    @JvmField val sheenBottom = c.metal.sheenBottom.toArgb()
    @JvmField val highlight = c.metal.highlight.toArgb()
    @JvmField val castShadow = c.metal.castShadow.toArgb()
    @JvmField val grooveCut = c.metal.grooveCut.toArgb()
    @JvmField val grooveLip = c.metal.grooveLip.toArgb()
    @JvmField val knurlA = c.metal.knurlA.toArgb()
    @JvmField val knurlB = c.metal.knurlB.toArgb()
    @JvmField val capLit = c.metal.capLit.toArgb()
    @JvmField val capMid = c.metal.capMid.toArgb()
    @JvmField val capShade = c.metal.capShade.toArgb()

    @JvmField val page = c.page.toArgb()
    @JvmField val field = c.field.toArgb()
    @JvmField val text = c.text.toArgb()
    @JvmField val textSecondary = c.textSecondary.toArgb()
    @JvmField val textTertiary = c.textTertiary.toArgb()
    @JvmField val led = c.led.toArgb()
    @JvmField val ink = c.ink.toArgb()
    @JvmField val danger = c.danger.toArgb()

    @JvmField val glazeTop = c.porcelain.glazeTop.toArgb()
    @JvmField val glazeBottom = c.porcelain.glazeBottom.toArgb()
    @JvmField val porcelainText = c.porcelain.text.toArgb()
    @JvmField val crackle = c.porcelain.crackle.toArgb()
    @JvmField val porcelainRecess = c.porcelain.recess.toArgb()
    @JvmField val porcelainLip = c.porcelain.lip.toArgb()

    /** The shadow under floating things, nearest layer first: blur and drop in dp, then colour. */
    @JvmField val floatingBlur = FloatArray(shadows.floating.size) { shadows.floating[it].radius.value }
    @JvmField val floatingDrop = FloatArray(shadows.floating.size) { shadows.floating[it].offset.y.value }
    @JvmField val floatingColor = IntArray(shadows.floating.size) { shadows.floating[it].color.toArgb() }
}

object CanvasPalette {
    private val light by lazy { CanvasMaterials(FrostLight, FrostLightShadows) }
    private val dark by lazy { CanvasMaterials(FrostDark, FrostDarkShadows) }

    @JvmStatic
    fun of(dark: Boolean): CanvasMaterials = if (dark) this.dark else light
}
