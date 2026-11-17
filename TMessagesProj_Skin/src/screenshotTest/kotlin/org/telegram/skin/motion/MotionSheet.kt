package org.telegram.skin.motion

import android.content.res.Configuration
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import org.telegram.skin.PreviewSkin
import org.telegram.skin.theme.Skin
import org.telegram.skin.ui.Knob
import org.telegram.skin.ui.LedFrame
import org.telegram.skin.ui.SkinText
import kotlin.math.abs

// The motion tokens drawn out: each curve as it runs over half a second, and
// the LED and the knob caught at moments of their motion. Every value is
// sampled from the same specs the screens animate with.

@Preview(name = "light", widthDp = 390, heightDp = 844)
@Preview(name = "dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
annotation class SheetPreviews

@PreviewTest
@SheetPreviews
@Composable
fun MotionSheetPreview() = PreviewSkin { MotionSheet() }

private class Track(val name: String, val spec: FiniteAnimationSpec<Float>, val from: Float, val to: Float)

private const val SPAN_MS = 500

private fun sample(spec: FiniteAnimationSpec<Float>, from: Float, to: Float, ms: Int): Float =
    TargetBasedAnimation(spec, Float.VectorConverter, from, to).getValueFromNanos(ms * 1_000_000L)

@Composable
private fun MotionSheet() {
    val c = Skin.colors
    val m = SkinMotionSpecs.Full
    val tracks = listOf(
        Track("按下", m.pressDown(), 0f, 1f),
        Track("松开回弹", m.pressRelease(), 1f, 0f),
        Track("状态切换", m.state(), 0f, 1f),
        Track("开关铝帽", m.toggle(), 0f, 1f),
        Track("釉色注入", m.glaze(), 0f, 1f),
        Track("进入", m.enter(), 0f, 1f),
        Track("离开", m.exit(), 0f, 1f),
        Track("瓷片落位", m.seat(), 0f, 1f),
        Track("灯亮起", m.ledOn(), 0f, 1f),
        Track("灯熄灭", m.ledOff(), 1f, 0f),
    )
    Column(
        Modifier
            .fillMaxSize()
            .background(c.page)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkinText("动效 · 0–500 ms", Skin.type.panelTitle, c.text)
        tracks.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { TrackCard(it, Modifier.weight(1f)) }
            }
        }
        Strip("灯亮起", listOf(0, 30, 60, 100, 160, 400)) { ms ->
            LedFrame({ sample(m.ledOn(), 0f, 1f, ms) }, size = 12.dp)
        }
        Strip("灯熄灭", listOf(0, 40, 90, 160, 260, 380)) { ms ->
            LedFrame({ sample(m.ledOff(), 1f, 0f, ms) }, size = 12.dp)
        }
        Strip("旋钮转到关闭", listOf(0, 40, 80, 130, 200, 400)) { ms ->
            Knob(turn = sample(m.seat(), 0f, 1f, ms), size = 36.dp)
        }
    }
}

@Composable
private fun TrackCard(track: Track, modifier: Modifier) {
    val c = Skin.colors
    val values = List(SPAN_MS / 5 + 1) { i -> sample(track.spec, track.from, track.to, i * 5) }
    val range = track.to - track.from
    // Past the target, as a share of the distance; and when it stays within 2% of it.
    val overshoot = values.maxOf { (it - track.to) * range / (range * range) }.coerceAtLeast(0f)
    val settled = (values.indexOfLast { abs(it - track.to) > abs(range) * 0.02f } + 1) * 5
    Column(
        modifier
            .background(c.surface, Skin.shapes.card)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SkinText(track.name, Skin.type.caption, c.text, maxLines = 1)
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            // Values from -0.15 to 1.25 of the way, so a rebound shows.
            fun y(v: Float) = size.height * (1f - ((v - track.from) / range + 0.15f) / 1.4f)
            val hair = 1.dp.toPx()
            drawLine(c.hairline, Offset(0f, y(track.from)), Offset(size.width, y(track.from)), hair)
            drawLine(
                c.textTertiary,
                Offset(0f, y(track.to)),
                Offset(size.width, y(track.to)),
                hair,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
            )
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = size.width * i / (values.size - 1)
                if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
            }
            drawPath(path, c.ink, style = Stroke(1.6.dp.toPx()))
        }
        val note = if (overshoot >= 0.005f) "$settled ms · 越过 ${(overshoot * 100).toInt()}%" else "$settled ms"
        SkinText(note, Skin.type.figureSmall, c.textSecondary, maxLines = 1)
    }
}

@Composable
private fun Strip(name: String, times: List<Int>, frame: @Composable (ms: Int) -> Unit) {
    val c = Skin.colors
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SkinText(name, Skin.type.caption, c.textSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            times.forEach { ms ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { frame(ms) }
                    SkinText("$ms", Skin.type.figureSmall, c.textTertiary)
                }
            }
        }
    }
}
