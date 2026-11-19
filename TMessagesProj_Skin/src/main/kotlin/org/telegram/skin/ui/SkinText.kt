package org.telegram.skin.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Text in one of the skin's styles. Anything that does not fit ends in an ellipsis. */
@Composable
fun SkinText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (align != null) style.copy(color = color, textAlign = align) else style.copy(color = color),
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
    )
}

/**
 * A one-line label that shrinks, down to [min], rather than cut off. Used where
 * the room is fixed by the layout (dock keys, tiles) but the system font may
 * be set very large.
 */
@Composable
fun FittedText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, min: TextUnit = 8.sp) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        overflow = TextOverflow.Ellipsis,
        softWrap = false,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = min, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}
