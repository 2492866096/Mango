package org.telegram.skin.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * What a row or a card shows under the finger, in place of the plain grey
 * overlay foundation draws: a faint wash of the skin's ink that comes up at
 * once and dies away like an LED's afterglow. Even the quickest tap (a list
 * reports press and release together) gets the whole flash. Keyboard focus
 * and a hovering pointer leave a lighter wash that stays.
 */
internal class SkinIndication(private val ink: Color, private val motion: SkinMotionSpecs) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode = Wash(interactionSource, ink, motion)

    override fun equals(other: Any?): Boolean = other is SkinIndication && other.ink == ink && other.motion === motion

    override fun hashCode(): Int = 31 * ink.hashCode() + motion.hashCode()

    private class Wash(
        private val source: InteractionSource,
        private val ink: Color,
        private val motion: SkinMotionSpecs,
    ) : Modifier.Node(), DrawModifierNode {
        private val press = Animatable(0f)
        private var focused = false
        private var hovered = false

        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> launch { press.animateTo(1f, motion.pressDown()) }
                        is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                            if (!motion.reduced) press.animateTo(1f, motion.pressDown())
                            press.animateTo(0f, motion.ledOff())
                        }
                        is FocusInteraction.Focus -> focused = true
                        is FocusInteraction.Unfocus -> focused = false
                        is HoverInteraction.Enter -> hovered = true
                        is HoverInteraction.Exit -> hovered = false
                    }
                    invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            drawContent()
            val rest = (if (focused) FOCUS else 0f) + (if (hovered) HOVER else 0f)
            val alpha = maxOf(press.value * PRESS, rest)
            if (alpha > 0f) drawRect(ink.copy(alpha = alpha))
        }
    }

    private companion object {
        const val PRESS = 0.12f
        const val FOCUS = 0.10f
        const val HOVER = 0.05f
    }
}
