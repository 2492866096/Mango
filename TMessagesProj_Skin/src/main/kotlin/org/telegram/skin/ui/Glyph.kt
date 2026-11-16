package org.telegram.skin.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Every icon the skin draws. They share one hand: a 24-unit grid, 1.8-unit
 * strokes with round ends, no fills. The host names them by value, so the
 * same set serves the dock, the 我 page and anything later.
 */
enum class Glyph(internal val paths: List<String>, internal val mirrors: Boolean = false, internal val weight: Float = 1.8f) {
    Chats(listOf("M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H11l-4.5 4v-4A2.5 2.5 0 0 1 4 13.5z")),
    Contacts(listOf(circle(12f, 8.5f, 4f), "M4.5 20.5c1.2-3.6 4-5.5 7.5-5.5s6.3 1.9 7.5 5.5")),
    Calls(listOf("M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2")),
    Feed(listOf("M4 10v4h3l6 4V6L7 10z", "M16.5 9a4 4 0 0 1 0 6", "M19 6.5a7.5 7.5 0 0 1 0 11")),
    Checklist(listOf("M4 6.5l1.5 1.5L8.5 5", "M11.5 6.5H20", "M4 12.5l1.5 1.5 3-3", "M11.5 12.5H20", "M4 18.5l1.5 1.5 3-3", "M11.5 18.5H20")),
    Inboxes(listOf("M3.5 6.5A2.5 2.5 0 0 1 6 4h8a2.5 2.5 0 0 1 2.5 2.5v4.5a2.5 2.5 0 0 1-2.5 2.5H9.5L6 16v-2.5A2.5 2.5 0 0 1 3.5 11z", "M19.5 9.5a1.5 1.5 0 0 1 1 1.4v4.6a2.5 2.5 0 0 1-2.5 2.5v2.5L14.5 18h-3.5")),
    Plus(listOf("M12 5v14", "M5 12h14"), weight = 2.2f),
    Search(listOf(circle(11f, 11f, 7f), "M20 20l-3.5-3.5"), weight = 2f),
    NewChat(listOf("M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H11l-4.5 4v-4A2.5 2.5 0 0 1 4 13.5z", "M12 6.5v6", "M9 9.5h6")),
    NewGroup(listOf(circle(9f, 7.5f, 3.5f), "M2.5 20c.9-3.2 3.3-5 6.5-5s5.6 1.8 6.5 5", "M16 4.5a3.5 3.5 0 0 1 0 6.5", "M18 15.3c1.7.8 2.9 2.4 3.5 4.7")),
    Secret(listOf("M6.5 10.5h11v9.5h-11z", "M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5", "M12 14.5v2")),
    Moon(listOf("M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")),
    Globe(listOf(circle(12f, 12f, 9f), "M3 12h18", "M12 3c2.6 2.7 2.6 15.3 0 18", "M12 3c-2.6 2.7-2.6 15.3 0 18")),
    DataSaver(listOf("M12 4v11", "M7.5 10.5L12 15l4.5-4.5", "M5 20h14")),
    Quiet(listOf("M9 5.5A6 6 0 0 1 18 10c0 5 2 7 2 7H8", "M6.2 6.6A6 6 0 0 0 6 10c0 5-2 7-2 7", "M10 21h4", "M3 3l18 18")),
    Bookmark(listOf("M6.5 3.5h11v17l-5.5-3.8-5.5 3.8z")),
    Stories(dashedRing() + circle(12f, 12f, 3.5f)),
    Camera(listOf("M4 8.5a2 2 0 0 1 2-2h2l1.5-2h5l1.5 2h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2z", circle(12f, 13f, 3.5f))),
    Qr(listOf(rect(3.5f, 3.5f, 6.5f, 6.5f, 1.5f), rect(14f, 3.5f, 6.5f, 6.5f, 1.5f), rect(3.5f, 14f, 6.5f, 6.5f, 1.5f), "M14 14h2.5v2.5H14z", "M18 18h2.5v2.5H18z", "M18 14h2.5", "M14 18v2.5")),
    Chevron(listOf("M9 5l7 7-7 7"), mirrors = true, weight = 2f),
    Broadcast(listOf("M4 10v4h3l6 4V6L7 10z", "M16.5 9a4 4 0 0 1 0 6")),
    Rules(listOf("M4 7h9", "M17 7h3", "M4 17h3", "M11 17h9", "M15 5v4", "M9 15v4")),
    ShieldCheck(listOf("M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z", "M8.5 12l2.5 2.5 4.5-4.5")),
    Gauge(listOf("M4.5 17a8 8 0 1 1 15 0", "M12 13.5l3.5-4")),
    History(listOf("M3.5 12a8.5 8.5 0 1 0 2.5-6", "M3.5 4v4h4", "M12 8v4.5l3 2")),
    Grid(listOf(rect(4f, 4f, 6f, 6f, 1.5f), rect(14f, 4f, 6f, 6f, 1.5f), rect(4f, 14f, 6f, 6f, 1.5f), rect(14f, 14f, 6f, 6f, 1.5f))),
    Person(listOf(circle(12f, 8f, 4f), "M4.5 20c1.2-3.4 4-5.2 7.5-5.2s6.3 1.8 7.5 5.2")),
    Palette(listOf("M12 3.5a8.5 8.5 0 1 0 0 17c1.2 0 1.8-.8 1.8-1.7 0-1.3-1.1-1.6-1.1-2.7 0-1 .8-1.6 1.8-1.6h2.2a3.8 3.8 0 0 0 3.8-3.8c0-4-3.8-7.2-8.5-7.2z", circle(7.8f, 11f, 1f), circle(10.5f, 7.5f, 1f), circle(14.8f, 7.8f, 1f))),
    Lock(listOf(rect(5.5f, 10.5f, 13f, 9.5f, 2f), "M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5")),
    Bell(listOf("M6 10a6 6 0 0 1 12 0c0 5 2 7 2 7H4s2-2 2-7", "M10 21h4")),
    Database(listOf("M5 6c0-1.4 3.1-2.5 7-2.5s7 1.1 7 2.5-3.1 2.5-7 2.5S5 7.4 5 6z", "M5 6v12c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5V6", "M5 12c0 1.4 3.1 2.5 7 2.5s7-1.1 7-2.5")),
    Folder(listOf("M3.5 7A2 2 0 0 1 5.5 5h4l2 2.5h7a2 2 0 0 1 2 2V17a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2z")),
    Devices(listOf(rect(3f, 5f, 13f, 9f, 1.5f), "M6 17.5h7", rect(17.5f, 9f, 4f, 10f, 1.2f))),
    Leaf(listOf("M5 19c0-8 5-13.5 14-14 .5 9-5 14-13 14", "M5 19c3-4 6-6.5 9-8")),
    Language(listOf("M4 6h8", "M8 4v2", "M10.5 6c-.8 3.5-3 6.2-6 7.8", "M6.5 9.5c1 1.6 2.6 2.9 4.5 3.8", "M12.5 20l3.5-8.5 3.5 8.5", "M13.8 17h4.4")),
    Dock(listOf(rect(3f, 14f, 18f, 6f, 3f), "M7 17h2", "M11 17h2", "M15 17h2", "M12 4v6", "M9 7l3 3 3-3")),
    Gear(listOf(circle(12f, 12f, 3f), "M12 2.5v3", "M12 18.5v3", "M4.2 7l2.6 1.5", "M17.2 15.5l2.6 1.5", "M4.2 17l2.6-1.5", "M17.2 8.5l2.6-1.5")),
    Warning(listOf("M12 4l9 16H3z", "M12 10v4", "M12 17v.5"));
}

private val cache = HashMap<Glyph, ImageVector>()

internal fun Glyph.vector(): ImageVector = cache.getOrPut(this) {
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = mirrors,
    ).apply {
        for (d in paths) {
            addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = weight,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
}

/** Draws [glyph] in [tint]. Decorative by default: the control it sits in carries the label. */
@Composable
fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Image(
        painter = rememberVectorPainter(glyph.vector()),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r ${r}v${h - 2 * r}a$r $r 0 0 1 ${-r} ${r}h${-(w - 2 * r)}a$r $r 0 0 1 ${-r} ${-r}v${-(h - 2 * r)}a$r $r 0 0 1 $r ${-r}z"

// The stories ring is broken into nine dashes, like Telegram's own unread-story ring.
private fun dashedRing(): List<String> = List(9) { i ->
    val r = 8.5
    val start = Math.toRadians(i * 40.0 - 90)
    val end = start + Math.toRadians(26.0)
    fun x(a: Double) = (12 + r * cos(a)).toFloat()
    fun y(a: Double) = (12 + r * sin(a)).toFloat()
    "M${x(start)} ${y(start)}A$r $r 0 0 1 ${x(end)} ${y(end)}"
}
