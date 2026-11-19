package org.telegram.skin.dock

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.telegram.skin.motion.SkinHaptics
import org.telegram.skin.motion.SkinMotion
import org.telegram.skin.motion.pressDepth
import org.telegram.skin.theme.Skin
import org.telegram.skin.theme.shadows
import org.telegram.skin.ui.Avatar
import org.telegram.skin.ui.FittedText
import org.telegram.skin.ui.Glyph
import org.telegram.skin.ui.GlyphIcon
import org.telegram.skin.ui.Led
import org.telegram.skin.ui.RollingFigure
import org.telegram.skin.ui.SkinText
import org.telegram.skin.ui.metal
import org.telegram.skin.ui.porcelain
import org.telegram.skin.ui.well

/**
 * 起点: what the knob opens. A search well, the four ways to start something,
 * and the people last talked to. When the room runs short (a phone on its
 * side with the keyboard up) the panel keeps its height and scrolls inside.
 */
@Composable
internal fun CreatePanel(state: DockState, actions: DockActions, room: Dp) {
    val c = Skin.colors
    val t = Skin.type
    Column(
        Modifier
            .heightIn(max = room.coerceAtLeast(120.dp))
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SearchWell(state, actions)
        val results = state.results
        if (state.query.isBlank() || results == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.keys.forEachIndexed { i, key -> PanelKey(key, i, Modifier.weight(1f)) { actions.onKey(key.id) } }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkinText(state.strings.recent, t.label, c.textSecondary, Modifier.semantics { heading() }, maxLines = 1)
                if (state.recents.isEmpty()) {
                    SkinText(state.strings.noRecent, t.caption, c.textTertiary)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(state.recents, key = { it.peer.account.toLong() shl 48 xor it.peer.id }) { contact ->
                            RecentContact(contact) { actions.onContact(contact) }
                        }
                    }
                }
            }
        } else if (results.isEmpty()) {
            SkinText(
                if (state.searching) state.strings.searching else state.strings.noResults,
                t.caption,
                c.textTertiary,
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                align = TextAlign.Center,
            )
        } else {
            Column {
                results.forEach { contact -> ResultRow(contact) { actions.onContact(contact) } }
            }
        }
    }
}

@Composable
private fun SearchWell(state: DockState, actions: DockActions) {
    val c = Skin.colors
    val focus = LocalFocusManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .well(c.field, c.metal.grooveCut, Skin.shapes.field)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphIcon(Glyph.Search, c.textSecondary, size = 18.dp)
        Box(Modifier.weight(1f).padding(vertical = 10.dp)) {
            if (state.query.isEmpty()) {
                SkinText(state.strings.searchHint, Skin.type.bodySmall, c.textTertiary, maxLines = 1)
            }
            BasicTextField(
                value = state.query,
                onValueChange = {
                    state.query = it
                    actions.onQuery(it)
                },
                singleLine = true,
                textStyle = Skin.type.bodySmall.copy(color = c.text),
                cursorBrush = SolidColor(c.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = state.strings.searchHint },
            )
        }
        if (state.query.isNotEmpty()) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable {
                        state.query = ""
                        actions.onQuery("")
                    }
                    .semantics { contentDescription = state.strings.close; role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Plus, c.textSecondary, Modifier.rotate(45f), size = 16.dp)
            }
        }
    }
}

/**
 * One of the four start keys: a small aluminium block with its name under the
 * mark. As the panel opens the keys rise out of the dock one after another;
 * pressed, a key sinks onto its stop.
 */
@Composable
private fun PanelKey(key: DockKey, index: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = Skin.colors
    val shape = Skin.shapes.tile
    val motion = Skin.motion
    val density = LocalDensity.current
    // A still preview shows the keys at rest.
    val still = motion.reduced || LocalInspectionMode.current
    val rise = remember { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(Unit) {
        delay(motion.stagger(index).toLong())
        rise.animateTo(1f, motion.enter())
    }
    val source = remember { MutableInteractionSource() }
    val depth = pressDepth(source)
    Column(
        modifier
            .heightIn(min = 82.dp)
            .graphicsLayer {
                val r = rise.value
                translationY = with(density) { (1f - r) * 14.dp.toPx() + depth.value * SkinMotion.PRESS_SINK_DP.dp.toPx() }
                alpha = r.coerceIn(0f, 1f)
            }
            .shadows(Skin.shadows.low, shape)
            .metal(c.metal, shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        GlyphIcon(key.glyph, c.text, size = 24.dp)
        FittedText(key.label, Skin.type.caption, c.text)
    }
}

@Composable
private fun RecentContact(contact: DockContact, onClick: () -> Unit) {
    val c = Skin.colors
    Column(
        Modifier
            .width(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Avatar(contact.peer, 50.dp, shape = RoundedCornerShape(17.dp))
            if (contact.online) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(13.dp)
                        .clip(CircleShape)
                        .metal(c.metal, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Led(on = true, size = 8.dp) }
            }
        }
        SkinText(contact.name, Skin.type.caption, c.text, maxLines = 1, align = TextAlign.Center)
    }
}

@Composable
private fun ResultRow(contact: DockContact, onClick: () -> Unit) {
    val c = Skin.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(Skin.shapes.card)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box {
            Avatar(contact.peer, 40.dp)
            if (contact.online) Led(on = true, modifier = Modifier.align(Alignment.BottomEnd), size = 8.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SkinText(contact.name, Skin.type.name, c.text, maxLines = 1)
            if (contact.subtitle != null) SkinText(contact.subtitle, Skin.type.caption, c.textSecondary, maxLines = 1)
        }
    }
}

private class ShownOrder {
    var value = 0
}

/** dp per second: about four dp of travel before the tile settles. */
private const val KNOCK_SPEED = 220f

/**
 * Picks the middle of the dock. Each choice is a tile; picked ones turn to
 * porcelain and carry their place in the bar as a numeral, so the order is
 * set simply by the order of the taps. The bar below follows live.
 */
@Composable
internal fun ArrangePanel(state: DockState, actions: DockActions, room: Dp) {
    val c = Skin.colors
    val t = Skin.type
    val motion = Skin.motion
    var full by remember { mutableStateOf(false) }
    LaunchedEffect(full) {
        if (full) {
            delay(2400)
            full = false
        }
    }
    Column(
        Modifier
            .heightIn(max = room.coerceAtLeast(120.dp))
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SkinText(state.strings.arrangeTitle, t.panelTitle, c.text, Modifier.semantics { heading() })
            Crossfade(full, animationSpec = motion.state(), label = "hint") { refused ->
                SkinText(if (refused) state.strings.arrangeFull else state.strings.arrangeHint, t.caption, if (refused) c.ink else c.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            state.choices.forEach { choice ->
                val order = state.chosen.indexOf(choice.id)
                ChoiceTile(choice, order, Modifier.weight(1f)) {
                    val next = when {
                        order >= 0 -> state.chosen - choice.id
                        state.chosen.size < state.maxChosen -> state.chosen + choice.id
                        else -> {
                            full = true
                            null
                        }
                    }
                    if (next != null) {
                        state.chosen = next
                        actions.onArrange(next)
                    }
                    next != null
                }
            }
        }
    }
}

/**
 * Picked, a tile fills with glaze from the bottom up, as porcelain is dipped;
 * dropped, the glaze drains away and the metal shows again. A tile that cannot
 * be picked (the bar is full) knocks against its stop.
 */
@Composable
private fun ChoiceTile(choice: DockChoice, order: Int, modifier: Modifier, onToggle: () -> Boolean) {
    val c = Skin.colors
    val shape = Skin.shapes.tile
    val motion = Skin.motion
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val on = order >= 0
    val glaze = animateFloatAsState(if (on) 1f else 0f, motion.glaze(), label = "glaze")
    val knock = remember { Animatable(0f) }
    // The numeral stays while the glaze drains, then goes with it.
    val shownOrder = remember { ShownOrder() }
    if (on) shownOrder.value = order
    val ink = lerp(c.text, c.porcelain.text, glaze.value)
    Box(
        modifier
            .heightIn(min = 82.dp)
            .graphicsLayer { translationX = with(density) { knock.value.dp.toPx() } }
            .shadows(if (on) emptyList() else Skin.shadows.low, shape)
            .metal(c.metal, shape)
            .clickable {
                if (onToggle()) {
                    SkinHaptics.tap(view)
                } else {
                    SkinHaptics.reject(view)
                    scope.launch { knock.animateTo(0f, motion.pressRelease(), initialVelocity = KNOCK_SPEED) }
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                selected = on
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .drawWithContent {
                    val g = glaze.value
                    if (g > 0f) {
                        clipRect(top = size.height * (1f - g)) { this@drawWithContent.drawContent() }
                    }
                }
                .porcelain(c.porcelain, shape),
        )
        if (glaze.value > 0f) {
            RollingFigure(
                "${shownOrder.value + 1}",
                Skin.type.figure.copy(fontWeight = FontWeight.SemiBold),
                ink.copy(alpha = glaze.value.coerceIn(0f, 1f)),
                Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 8.dp),
            )
        }
        Led(on = on, modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 4.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlyphIcon(choice.glyph, ink, size = 24.dp)
            FittedText(choice.label, Skin.type.caption, ink)
        }
    }
}
