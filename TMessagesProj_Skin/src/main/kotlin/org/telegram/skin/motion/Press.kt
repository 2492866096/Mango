package org.telegram.skin.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import org.telegram.skin.theme.Skin

/**
 * How far a key under [source] is pressed, from 0 to 1. It drops onto its stop
 * at once and comes back on its spring. A tap too quick to see (a list reports
 * press and release together) still goes all the way down before it comes
 * back, and a new press catches the key wherever it is.
 */
@Composable
fun pressDepth(source: InteractionSource): State<Float> {
    val depth = remember { Animatable(0f) }
    val motion = Skin.motion
    LaunchedEffect(source, motion) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> launch { depth.animateTo(1f, motion.pressDown()) }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                    if (!motion.reduced) depth.animateTo(1f, motion.pressDown())
                    depth.animateTo(0f, motion.pressRelease())
                }
            }
        }
    }
    return depth.asState()
}
