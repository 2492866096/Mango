package org.telegram.skin.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.telegram.skin.R

/**
 * Three voices, each with one job:
 * - a Song-style serif for titles and names on cards, which is where the
 *   editorial feel comes from;
 * - Outfit for numbers (counts, times, durations), set with tabular figures so
 *   columns of times line up;
 * - the system sans for everything people read at length.
 *
 * The serif ships as a subset of Noto Serif SC Bold covering the 8105
 * standard characters, GB2312 and Big5 level 1. Anything rarer falls back to
 * the system font, which is fine for the odd name and keeps the APK small.
 */
val SerifDisplay = FontFamily(Font(R.font.noto_serif_sc_bold, FontWeight.Bold))

val Numerals = FontFamily(
    Font(R.font.outfit_light, FontWeight.Light),
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
)

private const val TABULAR = "tnum"

// Every style takes its paragraph direction from the text itself, so Chinese
// or English stays left-to-right (punctuation included) inside a mirrored,
// right-to-left layout, and Arabic stays right-to-left inside a normal one.
// Figures have no letters to go by, so they fall back to left-to-right and a
// phone number keeps its order in an Arabic layout.
private fun style(
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontFeatureSettings: String? = null,
    textDirection: TextDirection = TextDirection.Content,
) = TextStyle(
    fontFamily = fontFamily,
    fontWeight = fontWeight,
    fontSize = fontSize,
    letterSpacing = letterSpacing,
    lineHeight = lineHeight,
    fontFeatureSettings = fontFeatureSettings,
    textDirection = textDirection,
)

@Immutable
data class SkinType(
    /** Page titles such as 对话. */
    val display: TextStyle,
    /** The light count that sits beside a page title. */
    val displayCount: TextStyle,
    /** A name shown large and alone, e.g. while switching accounts. */
    val hero: TextStyle,
    /** The name on the account card. */
    val cardTitle: TextStyle,
    /** Titles of panels and sections that carry a serif. */
    val panelTitle: TextStyle,
    /** Names on small cards. */
    val cardName: TextStyle,
    /** Glyphs inside seals and small badges. */
    val seal: TextStyle,

    /** Chat names in lists and the chat header. */
    val name: TextStyle,
    /** Message text. */
    val body: TextStyle,
    val bodySmall: TextStyle,
    /** Last-message previews. */
    val preview: TextStyle,
    /** Short quiet labels over groups. */
    val label: TextStyle,
    val caption: TextStyle,
    /** Tab labels and the smallest captions. */
    val micro: TextStyle,

    /** Times, counts and other figures that may stack in a column. */
    val figure: TextStyle,
    val figureSmall: TextStyle,
)

val DefaultSkinType = SkinType(
    display = style(fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 36.sp, letterSpacing = 3.sp, lineHeight = 1.2.em),
    displayCount = style(fontFamily = Numerals, fontWeight = FontWeight.Light, fontSize = 30.sp, fontFeatureSettings = TABULAR, textDirection = TextDirection.ContentOrLtr),
    hero = style(fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 3.sp, lineHeight = 1.25.em),
    cardTitle = style(fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 23.sp, letterSpacing = 2.sp, lineHeight = 1.25.em),
    panelTitle = style(fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 2.sp, lineHeight = 1.3.em),
    cardName = style(fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 15.sp, letterSpacing = 1.sp, lineHeight = 1.3.em),
    seal = style(fontFamily = SerifDisplay, fontWeight = FontWeight.Bold, fontSize = 12.sp),

    name = style(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 1.35.em),
    body = style(fontSize = 16.sp, lineHeight = 1.55.em),
    bodySmall = style(fontSize = 15.sp, lineHeight = 1.45.em),
    preview = style(fontSize = 14.sp, lineHeight = 1.4.em),
    label = style(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 1.sp, lineHeight = 1.4.em),
    caption = style(fontSize = 12.sp, lineHeight = 1.4.em),
    micro = style(fontSize = 11.sp, lineHeight = 1.3.em),

    figure = style(fontFamily = Numerals, fontSize = 12.sp, fontFeatureSettings = TABULAR, textDirection = TextDirection.ContentOrLtr),
    figureSmall = style(fontFamily = Numerals, fontSize = 11.sp, fontFeatureSettings = TABULAR, textDirection = TextDirection.ContentOrLtr),
)
