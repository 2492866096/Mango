package org.telegram.skin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import org.telegram.skin.theme.Skin

/**
 * A figure that rolls to its next value like the drums of a counter: only the
 * digits that change turn, up when the value grows and down when it shrinks.
 * When the figure gains or loses a digit the whole of it rolls over at once.
 * Screen readers get the figure as one piece of text.
 */
@Composable
fun RollingFigure(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val motion = Skin.motion
    val last = remember { Last(text) }
    val up = remember(text) { figureValue(text) >= figureValue(last.text) }
    SideEffect { last.text = text }
    val styled = style.copy(color = color)

    AnimatedContent(
        targetState = text,
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        transitionSpec = { roll(up, motion.roll(), motion.roll()) },
        contentKey = { it.length },
        label = "figure",
    ) { figure ->
        // Figures read left to right in every language, so the drums keep that
        // order in a right-to-left layout too.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row {
                figure.forEachIndexed { i, ch ->
                    key(i) {
                        AnimatedContent(
                            targetState = ch,
                            transitionSpec = { roll(up, motion.roll(), motion.roll()) },
                            label = "digit",
                        ) { digit -> BasicText(digit.toString(), style = styled) }
                    }
                }
            }
        }
    }
}

private class Last(var text: String)

private fun roll(up: Boolean, spec: FiniteAnimationSpec<IntOffset>, fade: FiniteAnimationSpec<Float>) = ContentTransform(
    targetContentEnter = slideInVertically(spec) { h -> if (up) h else -h } + fadeIn(fade),
    initialContentExit = slideOutVertically(spec) { h -> if (up) -h else h } + fadeOut(fade),
    sizeTransform = SizeTransform(clip = true),
)

/** The figure's value, reading Telegram's short forms: "1.2K" is 1200. */
internal fun figureValue(text: String): Double {
    var number = 0.0
    var scale = 1.0
    var fraction = 0.0
    var seen = false
    for (ch in text) {
        when {
            ch.isDigit() -> {
                seen = true
                if (fraction > 0) {
                    number += (ch - '0') * fraction
                    fraction /= 10
                } else {
                    number = number * 10 + (ch - '0')
                }
            }
            ch == '.' && fraction == 0.0 -> fraction = 0.1
            ch == 'K' || ch == 'k' -> scale *= 1000
            ch == 'M' -> scale *= 1_000_000
        }
    }
    return if (seen) number * scale else 0.0
}
