package org.telegram.skin.me

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.telegram.skin.ui.Glyph
import org.telegram.skin.ui.Peer

/** The account card at the top of 我. */
@Immutable
data class MeCard(
    val peer: Peer,
    val name: String,
    val handle: String,
    /** Already masked by the host, e.g. "+86 138 **** 2046". */
    val phone: String,
    /** Connection state in words: 在线, 连接中…, 等待网络… */
    val status: String,
    val online: Boolean,
)

/** One of the four switches on the control deck. */
@Immutable
data class MeToggle(val id: Int, val glyph: Glyph, val label: String, val on: Boolean, val description: String)

/** Something that needs the person's attention, such as a frozen account. */
@Immutable
data class MeNotice(val id: Int, val text: String, val danger: Boolean)

/** A small button at the end of a row, such as 发动态 on 我的动态. */
@Immutable
data class MeRowAction(val glyph: Glyph, val label: String)

@Immutable
data class MeRow(val id: Int, val glyph: Glyph, val label: String, val value: String? = null, val action: MeRowAction? = null)

@Immutable
data class MeTool(val id: Int, val glyph: Glyph, val label: String)

@Immutable
data class MeStrings(
    val qr: String,
    val tools: String,
    val settings: String,
    val openProfile: String,
    val switchAccounts: String,
)

interface MeActions {
    fun onCard()
    fun onQr()
    /** The stacked cards or the hint under them: show the other accounts. */
    fun onAccounts()
    fun onNotice(id: Int)
    /** [x], [y] is the tile's centre in the view, for effects that start there. */
    fun onToggle(id: Int, x: Float, y: Float)
    /** Long press on a tile: open its full settings. */
    fun onToggleDetails(id: Int)
    fun onRow(id: Int)
    fun onRowAction(id: Int)
    fun onTool(id: Int)
}

@Stable
class MeState(strings: MeStrings) {
    var strings by mutableStateOf(strings)
    var card by mutableStateOf<MeCard?>(null)

    /** How many other accounts there are; up to two show as slabs behind the card. */
    var otherAccounts by mutableIntStateOf(0)
    var accountsHint by mutableStateOf<String?>(null)
    var notices by mutableStateOf(emptyList<MeNotice>())
    var toggles by mutableStateOf(emptyList<MeToggle>())
    var shortcuts by mutableStateOf(emptyList<MeRow>())
    var tools by mutableStateOf(emptyList<MeTool>())
    var settings by mutableStateOf(emptyList<MeRow>())

    var topInset by mutableIntStateOf(0)
    var bottomInset by mutableIntStateOf(0)

    /** Bump to scroll back to the top. */
    var scrollToTop by mutableIntStateOf(0)
}
