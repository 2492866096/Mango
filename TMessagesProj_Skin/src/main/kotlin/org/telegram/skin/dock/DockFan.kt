package org.telegram.skin.dock

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.telegram.skin.motion.SkinHaptics
import org.telegram.skin.theme.Skin
import org.telegram.skin.theme.shadows
import org.telegram.skin.ui.Avatar
import org.telegram.skin.ui.Glyph
import org.telegram.skin.ui.GlyphIcon
import org.telegram.skin.ui.Led
import org.telegram.skin.ui.SkinText
import org.telegram.skin.ui.metalCard
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

// The account fan. Press the 我 key and slide out of the dock: account cards
// open in a shallow arc beside the key, the one under the finger lifts, and
// letting go switches to it. Letting go anywhere else leaves the fan open to
// be tapped, which is also how a screen reader reaches it. Beside a rail (a
// wide screen) the cards run out sideways from the rail instead.

private val CardWidth = 108.dp
private val CardHeight = 140.dp
private val FanRadius = 500.dp

/** Cards widen with a large system font, up to a point, so names still fit. */
private fun Density.cardScale() = fontScale.coerceIn(1f, 1.4f)

internal enum class FanKind { Account, Add, More }

internal class FanCard(val kind: FanKind, val account: DockAccount?, val center: Offset, val rotation: Float, val hidden: Int = 0)

/** Where the finger is and what it is over, shared by the 我 key and the fan. */
@Stable
internal class FanTracker {
    var dock by mutableStateOf(Rect.Zero)
    var meKey by mutableStateOf(Rect.Zero)
    var finger by mutableStateOf<Offset?>(null)
    var armed by mutableStateOf(false)
    var picked by mutableIntStateOf(-1)

    /** The cards as last laid out, for hit tests from the gesture. */
    var cards: List<FanCard> = emptyList()
    var reach = 0f
    var clearance = 0f

    fun reset() {
        finger = null
        armed = false
        picked = -1
    }

    /** The card nearest the finger, once the finger has left the dock. */
    fun hit(point: Offset, vertical: Boolean, rtl: Boolean): Int {
        val outside = when {
            !vertical -> point.y < dock.top - clearance
            rtl -> point.x < dock.left - clearance
            else -> point.x > dock.right + clearance
        }
        if (!outside) return -1
        var best = -1
        var bestDistance = reach
        cards.forEachIndexed { i, card ->
            val d = hypot(point.x - card.center.x, point.y - card.center.y)
            if (d < bestDistance) {
                best = i
                bestDistance = d
            }
        }
        return best
    }
}

private enum class Press { None, Tap, Fan, Next, Previous }

/**
 * Gestures of the 我 key: a tap opens the 我 page, a press or a slide out of
 * the dock opens the fan, and a sideways swipe steps to the next account.
 */
@Composable
internal fun Modifier.meKeyGestures(
    state: DockState,
    actions: DockActions,
    fan: FanTracker,
    index: Int,
    open: (DockMode) -> Unit,
): Modifier {
    val view = LocalView.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return this
        .onGloballyPositioned { fan.meKey = it.boundsInRoot() }
        .pointerInput(state, actions) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val vertical = fan.dock.height > fan.dock.width
                val slop = viewConfiguration.touchSlop
                var total = Offset.Zero
                val press = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var result = Press.None
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            result = Press.Tap
                            break
                        }
                        total += change.positionChange()
                        val out = if (vertical) (if (rtl) -total.x else total.x) else -total.y
                        val side = if (vertical) total.y else total.x
                        if (out > slop && out > abs(side)) {
                            result = Press.Fan
                            break
                        }
                        if (!vertical && abs(side) > slop * 2 && abs(side) > abs(out)) {
                            // Swiping toward the start reads as "next", as when flicking through cards.
                            result = if ((side < 0) != rtl) Press.Next else Press.Previous
                            break
                        }
                    }
                    result
                } ?: Press.Fan

                when (press) {
                    Press.Tap -> {
                        if (state.isOpen) open(DockMode.Rail)
                        actions.onTab(index)
                    }
                    Press.Next, Press.Previous -> {
                        SkinHaptics.confirm(view)
                        actions.onCycleAccount(press == Press.Next)
                        do {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                        } while (change.pressed)
                    }
                    Press.Fan -> {
                        SkinHaptics.confirm(view)
                        open(DockMode.Accounts)
                        fan.armed = true
                        fan.finger = fan.meKey.topLeft + down.position + total
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val point = fan.meKey.topLeft + change.position
                            fan.finger = point
                            val picked = fan.hit(point, vertical, rtl)
                            if (picked != fan.picked) {
                                fan.picked = picked
                                if (picked >= 0) SkinHaptics.tap(view)
                            }
                        }
                        val picked = fan.cards.getOrNull(fan.picked)
                        fan.armed = false
                        fan.finger = null
                        fan.picked = -1
                        if (picked != null) {
                            // Felt only when letting go does something; the current account just closes the fan.
                            if (picked.kind != FanKind.Account || picked.account?.current == false) SkinHaptics.confirm(view)
                            choose(picked, actions, open)
                        }
                    }
                    Press.None -> Unit
                }
            }
        }
}

private fun choose(card: FanCard, actions: DockActions, open: (DockMode) -> Unit) {
    open(DockMode.Rail)
    when (card.kind) {
        FanKind.Account -> card.account?.let { if (!it.current) actions.onAccount(it.account) }
        FanKind.Add -> actions.onAddAccount()
        FanKind.More -> actions.onMoreAccounts()
    }
}

@Composable
internal fun AccountFan(
    state: DockState,
    actions: DockActions,
    fan: FanTracker,
    vertical: Boolean,
    top: Dp,
    open: (DockMode) -> Unit,
    deal: () -> Float = { 1f },
) {
    val c = Skin.colors
    val motion = Skin.motion
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val cards = remember(state.accounts, state.canAddAccount, fan.dock, fan.meKey, width, height, vertical, rtl, top) {
            with(density) { layOutFan(state, fan.dock, fan.meKey, width, height, top.toPx(), vertical, rtl) }
        }
        fan.cards = cards
        fan.reach = with(density) { 96.dp.toPx() }
        fan.clearance = with(density) { 6.dp.toPx() }

        // The readout names what letting go will do.
        val picked = cards.getOrNull(fan.picked)
        val s = state.strings
        val (line, name) = when {
            picked == null -> (if (fan.armed) s.slideToPick else s.tapToPick) to null
            picked.kind == FanKind.Account -> s.releaseToSwitch to picked.account?.name
            picked.kind == FanKind.Add -> s.releaseToAdd to null
            else -> s.releaseToMore to null
        }
        // Beside a rail the readout starts past it; padding is mirrored in right-to-left, so measure from that side.
        val readoutStart = if (vertical) with(density) { (if (rtl) width - fan.dock.left else fan.dock.right).toDp() } + 24.dp else 22.dp
        Crossfade(
            targetState = line to name,
            modifier = Modifier.padding(start = readoutStart, top = top + 28.dp, end = 22.dp),
            animationSpec = motion.state(),
            label = "readout",
        ) { (shownLine, shownName) ->
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SkinText(shownLine, Skin.type.label.copy(letterSpacing = Skin.type.caption.letterSpacing), c.textSecondary)
                if (shownName != null) SkinText(shownName, Skin.type.hero, c.text, maxLines = 1)
            }
        }

        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                cards.forEachIndexed { i, card ->
                    val lifted = i == fan.picked
                    // A card rises to meet the finger and seats there, and settles back when the finger moves off.
                    val lift by animateFloatAsState(if (lifted) 1f else 0f, if (lifted) motion.seat() else motion.settle(), label = "lift")
                    val liftPx = with(density) { 18.dp.toPx() }
                    FanCardView(
                        card = card,
                        picked = lifted,
                        state = state,
                        width = CardWidth * density.cardScale(),
                        modifier = Modifier
                            // Nearer cards lie on top, so each one leaves the start of
                            // the next, where its name is, in view.
                            .zIndex(if (lifted) cards.size.toFloat() else (cards.size - 1 - i).toFloat())
                            .graphicsLayer {
                                // Dealt from the 我 key: each card leaves it a moment after
                                // the one before, turning and growing into its place.
                                val p = dealt(deal(), i, cards.size)
                                val home = fan.meKey.center - card.center
                                translationX = home.x * (1f - p)
                                translationY = home.y * (1f - p) - lift * liftPx
                                rotationZ = card.rotation * p
                                val scale = 0.55f + 0.45f * p
                                scaleX = scale
                                scaleY = scale
                                alpha = (p * 2.5f).coerceAtMost(1f)
                            },
                        onClick = { choose(card, actions, open) },
                    )
                }
            },
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(Constraints()) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { i, p ->
                    val center = cards[i].center
                    // Absolute placement: the fan geometry is already mirrored for right-to-left.
                    p.place((center.x - p.width / 2f).roundToInt(), (center.y - p.height / 2f).roundToInt())
                }
            }
        }
    }
}

/** How far card [index] of [count] is along, when the deal as a whole is at [progress]. */
private fun dealt(progress: Float, index: Int, count: Int): Float {
    if (count <= 1) return progress.coerceIn(0f, 1f)
    val spread = minOf(0.12f, 0.5f / (count - 1))
    return ((progress - index * spread) / (1f - (count - 1) * spread)).coerceIn(0f, 1f)
}

/**
 * Lays the cards on a shallow arc, nearest the 我 key first: the current
 * account, the others, then 添加账号. Cards overlap like a hand when there are
 * many; past what still leaves a finger's width per card, the rest fold into a
 * 更多账号 card.
 */
private fun Density.layOutFan(
    state: DockState,
    dock: Rect,
    key: Rect,
    width: Float,
    height: Float,
    top: Float,
    vertical: Boolean,
    rtl: Boolean,
): List<FanCard> {
    if (dock == Rect.Zero || key == Rect.Zero) return emptyList()
    val cardW = CardWidth.toPx() * cardScale()
    val cardH = CardHeight.toPx() * cardScale()
    // Beside a rail the row is long and its end cards tilt, so they need more edge room.
    val margin = (if (vertical) 28.dp else 16.dp).toPx()

    // Cards run along x either way. Under a bar they start at the 我 key and
    // spread toward the start; beside a rail they start at the rail and
    // spread toward the end.
    val lowLimit = margin + cardW / 2
    val highLimit = width - margin - cardW / 2
    val near: Float
    val direction: Float
    if (vertical) {
        direction = if (rtl) -1f else 1f
        near = (if (rtl) dock.left - 24.dp.toPx() - cardW / 2 else dock.right + 24.dp.toPx() + cardW / 2).coerceIn(lowLimit, highLimit)
    } else {
        direction = if (rtl) 1f else -1f
        near = key.center.x.coerceIn(lowLimit, highLimit)
    }
    val far = if (direction < 0) lowLimit else highLimit
    val span = abs(near - far)

    // Each card keeps at least a finger's width of itself in view; beside a
    // rail there is room for most of the name as well.
    val minStep = (if (vertical) 96.dp else 48.dp).toPx()
    val maxStep = if (vertical) cardW + 16.dp.toPx() else 78.dp.toPx()
    val room = 1 + floor(span / minStep).toInt()

    val current = state.accounts.filter { it.current }
    val others = state.accounts.filter { !it.current }
    val add = if (state.canAddAccount) 1 else 0
    val items = ArrayList<Pair<FanKind, DockAccount?>>()
    current.forEach { items += FanKind.Account to it }
    val hidden: Int
    if (current.size + others.size + add <= room) {
        others.forEach { items += FanKind.Account to it }
        hidden = 0
    } else {
        val shown = (room - current.size - add - 1).coerceAtLeast(0)
        others.take(shown).forEach { items += FanKind.Account to it }
        hidden = others.size - shown
        items += FanKind.More to null
    }
    if (add == 1) items += FanKind.Add to null

    val count = items.size
    val step = if (count > 1) minOf(maxStep, span / (count - 1)) else 0f
    // A long row beside a rail bends gently; a short hand under the bar curls more.
    val radius = maxOf(FanRadius.toPx(), step * (count - 1) * 2.4f)
    // Under the bar the arc turns about a point just past the key, as a hand
    // of cards held there would; beside the rail it is centred on the row.
    val pivot = if (vertical) near + direction * step * (count - 1) / 2 else near + direction * 0.78f * step
    val baseline = if (vertical) {
        (key.center.y - 30.dp.toPx()).coerceIn(top + margin + cardH / 2 + 72.dp.toPx(), height - margin - cardH / 2)
    } else {
        dock.top - 142.dp.toPx() * cardScale()
    }

    return items.mapIndexed { k, (kind, account) ->
        val along = near + direction * k * step
        val theta = asin(((along - pivot) / radius).coerceIn(-0.9f, 0.9f))
        val sag = radius * (1 - cos(theta))
        FanCard(
            kind = kind,
            account = account,
            center = Offset(along, baseline + sag),
            rotation = Math.toDegrees(theta.toDouble()).toFloat(),
            hidden = if (kind == FanKind.More) hidden else 0,
        )
    }
}

@Composable
private fun FanCardView(card: FanCard, picked: Boolean, state: DockState, width: Dp, modifier: Modifier, onClick: () -> Unit) {
    val c = Skin.colors
    val shape = Skin.shapes.card
    val strings = state.strings
    val motion = Skin.motion
    // The edge of the card under the finger strikes like an LED and fades when the finger moves on.
    val glow = animateFloatAsState(if (picked) 1f else 0f, if (picked) motion.ledOn() else motion.ledOff(), label = "edge")
    val base = modifier
        .width(width)
        .heightIn(min = CardHeight)
    when (card.kind) {
        FanKind.Account -> {
            val account = card.account ?: return
            Column(
                base
                    .shadows(Skin.shadows.floating, shape)
                    .metalCard(c.metal, shape)
                    .drawWithContent {
                        drawContent()
                        val g = glow.value
                        if (g > 0f) {
                            val w = 1.5.dp.toPx() * (1f + 0.6f * ((g - 1f) / 0.2f).coerceIn(0f, 1f))
                            inset(w / 2) {
                                drawOutline(shape.createOutline(size, layoutDirection, this), c.led.copy(alpha = g.coerceIn(0f, 1f)), style = Stroke(w))
                            }
                        }
                    }
                    .clickable(onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        contentDescription = account.name + "，" + account.meta
                        role = Role.Button
                    }
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Avatar(account.peer, 40.dp, shape = RoundedCornerShape(13.dp))
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    SkinText(account.name, Skin.type.cardName, c.text, maxLines = 1)
                    if (account.handle.isNotEmpty()) SkinText(account.handle, Skin.type.figureSmall, c.textSecondary, maxLines = 1)
                    Row(
                        Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (account.current) Led(on = true)
                        if (account.meta.isNotEmpty()) {
                            SkinText(account.meta, Skin.type.micro, if (account.current) c.ink else c.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }
        FanKind.Add, FanKind.More -> {
            val label = if (card.kind == FanKind.Add) strings.addAccount else strings.moreAccounts.replace("%d", card.hidden.toString())
            val dash = c.textTertiary
            Column(
                base
                    .background(c.page.copy(alpha = 0.92f), shape)
                    .drawBehind {
                        val w = 1.5.dp.toPx()
                        drawRoundRect(
                            color = lerp(dash, c.led, glow.value.coerceIn(0f, 1f)),
                            topLeft = Offset(w / 2, w / 2),
                            size = Size(size.width - w, size.height - w),
                            cornerRadius = CornerRadius(18.dp.toPx() - w / 2),
                            style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                        )
                    }
                    .clickable(onClick = onClick)
                    .semantics(mergeDescendants = true) {
                        contentDescription = label
                        role = Role.Button
                    }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
            ) {
                GlyphIcon(if (card.kind == FanKind.Add) Glyph.Plus else Glyph.Grid, c.textSecondary, size = 22.dp)
                SkinText(label, Skin.type.caption, c.textSecondary, maxLines = 2)
            }
        }
    }
}
