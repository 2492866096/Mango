package org.telegram.skin.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The palette is cool white by day and graphite by night, with one celadon
 * accent. Celadon is spent sparingly (the LED dots, the porcelain inlay, read
 * ticks) so it still draws the eye wherever it does show up.
 */
@Immutable
data class SkinColors(
    val isDark: Boolean,

    // Surfaces, from the back of the screen forward. The chat sits a shade off
    // the page so that moving between the two reads as moving somewhere.
    val page: Color,
    val chat: Color,
    val surface: Color,
    val field: Color,

    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val hairline: Color,

    /** Glowing marks: unread, online, "this one is current". */
    val led: Color,
    /** Accent text and icons, dark enough to read on the page. */
    val ink: Color,
    val danger: Color,
    /** Line work of the crackle wallpaper. */
    val crackle: Color,

    val avatarText: Color,
    /** Muted grounds for letter avatars, picked by a stable hash of the peer. */
    val avatars: List<Color>,

    val metal: Metal,
    val porcelain: Porcelain,
    val bubbleIn: Bubble,
    val bubbleGraphite: Bubble,
    val bubbleCeladon: Bubble,
) {
    /** Outgoing bubbles follow the theme by default: graphite by day, celadon at night. */
    val bubbleOut: Bubble get() = if (isDark) bubbleCeladon else bubbleGraphite
}

/** Brushed aluminium: the dock, the account cards, the knobs. */
@Immutable
data class Metal(
    // Vertical sheen across a slab, top to bottom.
    val sheenTop: Color,
    val sheenMid: Color,
    val sheenBottom: Color,
    /** The one-pixel lit edge along the top. */
    val highlight: Color,
    val castShadow: Color,
    // An engraved line is a dark cut with a lit lip under it.
    val grooveCut: Color,
    val grooveLip: Color,
    // The knurled ring of a knob alternates two tones.
    val knurlA: Color,
    val knurlB: Color,
    // The knob's cap is lit from the upper left.
    val capLit: Color,
    val capMid: Color,
    val capShade: Color,
    /** A slab sitting further back in a stack, such as the other accounts' cards. */
    val stacked: Color,
)

/** Celadon glaze set into the metal, used for the selected tab. */
@Immutable
data class Porcelain(
    val glazeTop: Color,
    val glazeBottom: Color,
    val text: Color,
    val crackle: Color,
    /** Inner shadow that makes the glaze sit below the metal surface. */
    val recess: Color,
    /** The lit lower lip of the recess. */
    val lip: Color,
)

@Immutable
data class Bubble(
    val background: Color,
    val text: Color,
    val meta: Color,
    val tick: Color,
    val quote: Color,
)

val FrostLight = SkinColors(
    isDark = false,
    page = Color(0xFFF3F5F7),
    chat = Color(0xFFEEF2F3),
    surface = Color(0xFFFFFFFF),
    field = Color(0xFFEEF1F3),
    text = Color(0xFF1C2226),
    textSecondary = Color(0xFF5B666C),
    textTertiary = Color(0xFF5F6A70),
    hairline = Color(0xFFE1E6E8),
    led = Color(0xFF4FA597),
    ink = Color(0xFF2C6A64),
    danger = Color(0xFFB5473A),
    crackle = Color(0x132C6A64),
    avatarText = Color(0xFF3A4449),
    avatars = listOf(
        Color(0xFFD8DEE2), Color(0xFFE5DFD6), Color(0xFFD5E1DC),
        Color(0xFFDCDAE4), Color(0xFFE7DBDA), Color(0xFFD8E2E8),
    ),
    metal = Metal(
        sheenTop = Color(0xFFFCFDFD),
        sheenMid = Color(0xFFF0F3F5),
        sheenBottom = Color(0xFFE5E9EC),
        highlight = Color(0xFFFFFFFF),
        castShadow = Color(0x291C2226),
        grooveCut = Color(0x1A1C2226),
        grooveLip = Color(0xF2FFFFFF),
        knurlA = Color(0xFFD3DADE),
        knurlB = Color(0xFFF4F6F7),
        capLit = Color(0xFFFFFFFF),
        capMid = Color(0xFFEEF1F3),
        capShade = Color(0xFFCFD6DA),
        stacked = Color(0xFFE3E8EB),
    ),
    porcelain = Porcelain(
        glazeTop = Color(0xFFD9ECE6),
        glazeBottom = Color(0xFFC3DED5),
        text = Color(0xFF1F4A45),
        crackle = Color(0x291F4A45),
        recess = Color(0x331F4A45),
        lip = Color(0xB3FFFFFF),
    ),
    bubbleIn = Bubble(
        background = Color(0xFFFFFFFF),
        text = Color(0xFF1C2226),
        meta = Color(0xFF5F6A70),
        tick = Color(0xFF2C6A64),
        quote = Color(0xFFF0F4F5),
    ),
    bubbleGraphite = Bubble(
        background = Color(0xFF20272B),
        text = Color(0xFFF2F5F6),
        meta = Color(0xFFB4BEC3),
        tick = Color(0xFF7CC4B6),
        quote = Color(0xFF2E363B),
    ),
    bubbleCeladon = Bubble(
        background = Color(0xFFCDE2DB),
        text = Color(0xFF16302B),
        meta = Color(0xFF3D5A54),
        tick = Color(0xFF2C6A64),
        quote = Color(0xFFB9D6CC),
    ),
)

val FrostDark = SkinColors(
    isDark = true,
    page = Color(0xFF15191C),
    chat = Color(0xFF121618),
    surface = Color(0xFF1C2124),
    field = Color(0xFF22282C),
    text = Color(0xFFE8EDEF),
    textSecondary = Color(0xFF9BA6AC),
    textTertiary = Color(0xFF8C979D),
    hairline = Color(0xFF252C30),
    led = Color(0xFF7CC4B6),
    ink = Color(0xFF8FD0C3),
    danger = Color(0xFFE8857A),
    crackle = Color(0x128FD0C3),
    avatarText = Color(0xFFC9D2D6),
    avatars = listOf(
        Color(0xFF2B3337), Color(0xFF33302B), Color(0xFF293533),
        Color(0xFF2F2E37), Color(0xFF38302F), Color(0xFF2A3439),
    ),
    metal = Metal(
        sheenTop = Color(0xFF2C3337),
        sheenMid = Color(0xFF232A2D),
        sheenBottom = Color(0xFF1E2427),
        highlight = Color(0x1AFFFFFF),
        castShadow = Color(0x99000000),
        grooveCut = Color(0x80000000),
        grooveLip = Color(0x0FFFFFFF),
        knurlA = Color(0xFF3B4348),
        knurlB = Color(0xFF2A3135),
        capLit = Color(0xFF4A5358),
        capMid = Color(0xFF31393D),
        capShade = Color(0xFF23292C),
        stacked = Color(0xFF262D31),
    ),
    porcelain = Porcelain(
        glazeTop = Color(0xFF33605A),
        glazeBottom = Color(0xFF284D48),
        text = Color(0xFFE6F2EF),
        crackle = Color(0x29E6F2EF),
        recess = Color(0x73000000),
        lip = Color(0x14FFFFFF),
    ),
    bubbleIn = Bubble(
        background = Color(0xFF1F2529),
        text = Color(0xFFE8EDEF),
        meta = Color(0xFF8C979D),
        tick = Color(0xFF8FD0C3),
        quote = Color(0xFF283035),
    ),
    bubbleGraphite = Bubble(
        background = Color(0xFFDCE3E6),
        text = Color(0xFF1C2226),
        meta = Color(0xFF4F5B61),
        tick = Color(0xFF2C6A64),
        quote = Color(0xFFC9D2D6),
    ),
    bubbleCeladon = Bubble(
        background = Color(0xFF2A4843),
        text = Color(0xFFE6F2EF),
        meta = Color(0xFFA6C8C0),
        tick = Color(0xFFBFE3DA),
        quote = Color(0xFF35605A),
    ),
)
