package org.telegram.skin.me

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.telegram.skin.theme.Skin
import org.telegram.skin.theme.shadows
import org.telegram.skin.ui.Avatar
import org.telegram.skin.ui.FittedText
import org.telegram.skin.ui.Glyph
import org.telegram.skin.ui.GlyphIcon
import org.telegram.skin.ui.Groove
import org.telegram.skin.ui.Led
import org.telegram.skin.ui.SkinText
import org.telegram.skin.ui.metalCard

private val DockClearance = 64.dp + 8.dp + 16.dp

/**
 * 我: the account card on top, a deck of four switches, the person's own
 * things, Mango's toolbox, then the doors into settings. On a wide screen the
 * column keeps a reading width and centres.
 */
@Composable
fun MePage(state: MeState, actions: MeActions, modifier: Modifier = Modifier) {
    val c = Skin.colors
    val density = LocalDensity.current
    val motion = Skin.motion
    val scroll = rememberScrollState()
    LaunchedEffect(state.scrollToTop) {
        if (state.scrollToTop > 0) scroll.animateScrollTo(0, motion.settle())
    }
    val top = with(density) { state.topInset.toDp() }
    val bottom = with(density) { state.bottomInset.toDp() }
    // Hand the scrolling on to the views around, so the dock folds here too.
    Box(
        modifier
            .fillMaxSize()
            .background(c.page)
            .nestedScroll(rememberNestedScrollInteropConnection()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(start = Skin.space.gutter, end = Skin.space.gutter, top = top + Skin.space.gutter, bottom = bottom + DockClearance),
        ) {
            state.card?.let { card ->
                // Switching accounts, the next card comes forward off the stack
                // as the one that was on top drops back into it.
                AnimatedContent(
                    targetState = card,
                    contentKey = { it.peer.account },
                    transitionSpec = {
                        (slideInVertically(motion.enter()) { it / 8 } + scaleIn(motion.enter(), initialScale = 0.94f) + fadeIn(motion.state()))
                            .togetherWith(scaleOut(motion.exit(), targetScale = 0.94f) + fadeOut(motion.exit()))
                            .using(SizeTransform(clip = false))
                    },
                    label = "card",
                ) { shown -> CardStack(shown, state, actions) }
            }
            state.accountsHint?.let { hint ->
                SkinText(
                    hint,
                    Skin.type.caption,
                    c.textSecondary,
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = actions::onAccounts)
                        .semantics { role = Role.Button }
                        .heightIn(min = 32.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    align = TextAlign.Center,
                )
            }
            // A notice coming or going moves what is below it smoothly.
            Column(Modifier.animateContentSize(motion.settle())) {
                state.notices.forEach { notice -> Notice(notice) { actions.onNotice(notice.id) } }
            }
            if (state.toggles.isNotEmpty()) {
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.toggles.forEach { toggle -> ToggleTile(toggle, actions, Modifier.weight(1f)) }
                }
            }
            if (state.shortcuts.isNotEmpty()) {
                Group(Modifier.padding(top = 16.dp)) { RowList(state.shortcuts, actions) }
            }
            if (state.tools.isNotEmpty()) {
                Section(state.strings.tools)
                Toolbox(state.tools, actions)
            }
            if (state.settings.isNotEmpty()) {
                Section(state.strings.settings)
                Group { RowList(state.settings, actions) }
            }
        }
    }
}

/** The current account as a card of brushed metal, with the other accounts stacked behind it. */
@Composable
private fun CardStack(card: MeCard, state: MeState, actions: MeActions) {
    val c = Skin.colors
    val t = Skin.type
    val shape = Skin.shapes.accountCard
    Box(Modifier.fillMaxWidth().heightIn(min = 214.dp)) {
        if (state.otherAccounts > 0) {
            val stack = Modifier
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = actions::onAccounts)
                .semantics { contentDescription = state.strings.switchAccounts; role = Role.Button }
            if (state.otherAccounts > 1) {
                Box(
                    Modifier
                        .padding(horizontal = 30.dp)
                        .fillMaxWidth()
                        .height(60.dp)
                        .then(stack)
                        .background(c.metal.stacked.copy(alpha = 0.55f)),
                )
            }
            Box(
                Modifier
                    .padding(start = 15.dp, end = 15.dp, top = 8.dp)
                    .fillMaxWidth()
                    .height(60.dp)
                    .then(stack)
                    .background(c.metal.stacked.copy(alpha = 0.85f)),
            )
        }
        Column(
            Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .heightIn(min = 196.dp)
                .shadows(Skin.shadows.resting, shape)
                .metalCard(c.metal, shape)
                .clickable(onClick = actions::onCard)
                .semantics(mergeDescendants = true) {
                    contentDescription = card.name + "，" + state.strings.openProfile
                    role = Role.Button
                }
                .padding(start = 20.dp, end = 12.dp, top = 22.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(card.peer, 64.dp, shape = RoundedCornerShape(21.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SkinText(card.name, t.cardTitle, c.text, maxLines = 2)
                    if (card.handle.isNotEmpty()) SkinText(card.handle, t.figure.copy(fontSize = t.preview.fontSize), c.textSecondary, maxLines = 1)
                }
                Box(
                    Modifier
                        .align(Alignment.Top)
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = actions::onQr)
                        .semantics { contentDescription = state.strings.qr; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { GlyphIcon(Glyph.Qr, c.text) }
            }
            Groove(vertical = false, Modifier.padding(top = 16.dp, bottom = 14.dp, end = 8.dp))
            // With a very large font the status drops under the number rather than squeeze it.
            FlowRow(
                Modifier.fillMaxWidth().padding(end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                SkinText(card.phone, t.figure.copy(fontSize = t.preview.fontSize, letterSpacing = t.label.letterSpacing / 2), c.textSecondary, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Led(on = card.online)
                    Spacer(Modifier.size(6.dp))
                    SkinText(card.status, t.caption, if (card.online) c.ink else c.textSecondary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun Notice(notice: MeNotice, onClick: () -> Unit) {
    val c = Skin.colors
    val tint = if (notice.danger) c.danger else c.ink
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .shadows(Skin.shadows.low, Skin.shapes.card)
            .clip(Skin.shapes.card)
            .background(c.surface)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphIcon(Glyph.Warning, tint)
        SkinText(notice.text, Skin.type.bodySmall, c.text, Modifier.weight(1f))
        GlyphIcon(Glyph.Chevron, c.textTertiary, size = 18.dp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToggleTile(toggle: MeToggle, actions: MeActions, modifier: Modifier) {
    val c = Skin.colors
    val shape = Skin.shapes.tile
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier
            .heightIn(min = 88.dp)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .shadows(Skin.shadows.low, shape)
            .clip(shape)
            .background(c.surface)
            .combinedClickable(
                onClick = { actions.onToggle(toggle.id, bounds.center.x, bounds.center.y) },
                onLongClick = { actions.onToggleDetails(toggle.id) },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = toggle.description
                role = Role.Switch
                toggleableState = ToggleableState(toggle.on)
            },
    ) {
        Led(on = toggle.on, modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 4.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlyphIcon(toggle.glyph, c.text, size = 24.dp)
            FittedText(toggle.label, Skin.type.caption, c.text)
        }
    }
}

@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = Skin.shapes.card
    Column(
        modifier
            .fillMaxWidth()
            .shadows(Skin.shadows.low, shape)
            .clip(shape)
            .background(Skin.colors.surface),
    ) { content() }
}

@Composable
private fun RowList(rows: List<MeRow>, actions: MeActions) {
    val c = Skin.colors
    rows.forEachIndexed { i, row ->
        if (i > 0) Box(Modifier.padding(start = 52.dp).fillMaxWidth().height(1.dp).background(c.hairline))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable { actions.onRow(row.id) }
                .semantics(mergeDescendants = true) { role = Role.Button }
                .padding(start = 16.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlyphIcon(row.glyph, c.textSecondary)
            SkinText(row.label, Skin.type.bodySmall, c.text, Modifier.weight(1f))
            if (row.value != null) {
                SkinText(row.value, Skin.type.figure.copy(fontSize = Skin.type.preview.fontSize, fontWeight = FontWeight.Light), c.textSecondary, maxLines = 1)
            }
            if (row.action != null) {
                Row(
                    Modifier
                        .clip(Skin.shapes.chip)
                        .background(c.field)
                        .clickable { actions.onRowAction(row.id) }
                        .semantics(mergeDescendants = true) { role = Role.Button }
                        .heightIn(min = 36.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GlyphIcon(row.action.glyph, c.ink, size = 18.dp)
                    SkinText(row.action.label, Skin.type.caption, c.ink, maxLines = 1)
                }
            }
            GlyphIcon(Glyph.Chevron, c.textTertiary, size = 18.dp)
        }
    }
}

@Composable
private fun Section(title: String) {
    SkinText(
        title,
        Skin.type.label,
        Skin.colors.textSecondary,
        Modifier
            .padding(start = 4.dp, top = 22.dp, bottom = 10.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Toolbox(tools: List<MeTool>, actions: MeActions) {
    val c = Skin.colors
    // Four to a row, or three when that leaves fewer empty cells in the last row;
    // three as well once the system font is so large that four-character names would break.
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    fun gaps(columns: Int) = (columns - tools.size % columns) % columns
    val columns = if (largeFont || gaps(3) < gaps(4)) 3 else 4
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        tools.chunked(columns).forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { tool ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { actions.onTool(tool.id) }
                            .semantics(mergeDescendants = true) { role = Role.Button }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(c.field),
                            contentAlignment = Alignment.Center,
                        ) { GlyphIcon(tool.glyph, c.text) }
                        SkinText(tool.label, Skin.type.caption, c.text, maxLines = 2, align = TextAlign.Center)
                    }
                }
                repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
