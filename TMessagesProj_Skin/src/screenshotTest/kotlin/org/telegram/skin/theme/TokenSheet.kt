package org.telegram.skin.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.telegram.skin.PreviewSkin
import org.telegram.skin.SkinPreviews
import org.telegram.skin.ui.Knob

// A one-page look at every token, rendered in both themes. It is the first
// check that the fonts load, the palette holds together and the shadow and
// metal recipes work before any real screen is built on them.

@PreviewTest
@SkinPreviews
@Composable
fun TokenSheetPreview() = PreviewSkin { TokenSheet() }

@Composable
private fun TokenSheet() {
    val c = Skin.colors
    val t = Skin.type
    Column(
        Modifier
            .fillMaxSize()
            .background(c.page)
            .padding(horizontal = Skin.space.gutter, vertical = Skin.space.xl),
        verticalArrangement = Arrangement.spacedBy(Skin.space.l),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Label("对话", t.display, c.text)
            Spacer(Modifier.width(Skin.space.s))
            Label("8", t.displayCount, c.textSecondary)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(c.page, c.chat, c.surface, c.field, c.hairline, c.textSecondary, c.text, c.led, c.ink, c.danger)
                .forEach { Swatch(it) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Skin.space.xs)) {
            Label("林经理 林經理", t.hero, c.text)
            Label("林一舟", t.cardTitle, c.text)
            Label("切换账号", t.panelTitle, c.text)
            Label("家人群", t.name, c.text)
            Label("妈妈：周末回来吃饭吗？我包了饺子", t.body, c.text)
            Label("[照片] 昨天拍的晚霞", t.preview, c.textSecondary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Skin.space.m),
                verticalArrangement = Arrangement.spacedBy(Skin.space.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Label("最近联系", t.label, c.textSecondary)
                Label("十分钟前在线", t.caption, c.textSecondary)
                Label("联系人", t.micro, c.textSecondary)
                Label("21:40  12  102", t.figure, c.textSecondary)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Skin.space.s)) {
            c.avatars.forEachIndexed { i, ground ->
                Box(
                    Modifier.size(44.dp).clip(Skin.shapes.avatar).background(ground),
                    contentAlignment = Alignment.Center,
                ) { Label("家小前数王公"[i].toString(), t.name, c.avatarText) }
            }
        }

        MetalSlab()

        Column(verticalArrangement = Arrangement.spacedBy(Skin.space.s)) {
            Bubble(c.bubbleIn, "今晚几点到？", start = true)
            Bubble(c.bubbleGraphite, "八点左右，路上有点堵", start = false)
            Bubble(c.bubbleCeladon, "到了给你发消息", start = false)
        }
    }
}

/** A short dock: aluminium sheen, the lit top edge, one porcelain inlay and a knob. */
@Composable
private fun MetalSlab() {
    val m = Skin.colors.metal
    val p = Skin.colors.porcelain
    val shapes = Skin.shapes
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadows(Skin.shadows.floating, shapes.dock)
            .clip(shapes.dock)
            .background(Brush.verticalGradient(listOf(m.sheenTop, m.sheenMid, m.sheenBottom)))
            .drawBehind { drawLine(m.highlight, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx()) }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(52.dp)
                .clip(shapes.inlay)
                .background(Brush.verticalGradient(listOf(p.glazeTop, p.glazeBottom))),
            contentAlignment = Alignment.Center,
        ) { Label("对话", Skin.type.micro, p.text) }
        Spacer(Modifier.weight(2f))
        Box(
            Modifier
                .padding(horizontal = 6.dp)
                .width(1.dp)
                .height(34.dp)
                .background(m.grooveCut),
        )
        Knob(turn = 0f, size = 50.dp)
    }
}

@Composable
private fun Bubble(colors: Bubble, text: String, start: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (start) Alignment.CenterStart else Alignment.CenterEnd) {
        Row(
            Modifier
                .shadows(Skin.shadows.low, Skin.shapes.bubble)
                .clip(Skin.shapes.bubble)
                .background(colors.background)
                .padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 7.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Skin.space.s),
        ) {
            Label(text, Skin.type.body, colors.text)
            Label("21:40", Skin.type.figureSmall, colors.meta)
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        Modifier
            .size(28.dp)
            .clip(Skin.shapes.chip)
            .background(Skin.colors.hairline)
            .padding(1.dp)
            .clip(Skin.shapes.chip)
            .background(color),
    )
}

@Composable
private fun Label(text: String, style: TextStyle, color: Color) {
    BasicText(text, style = style.copy(color = color))
}
