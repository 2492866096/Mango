package org.telegram.skin.dock

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.telegram.skin.ui.Glyph
import org.telegram.skin.ui.Peer

/** One key on the dock. The 我 key shows [avatar] instead of [glyph]. */
@Immutable
data class DockTab(
    val id: Int,
    val glyph: Glyph,
    val label: String,
    /** What a screen reader says, e.g. "对话，102 条未读". */
    val description: String,
    /** A small figure beside the label, such as the unread total. */
    val readout: String? = null,
    val avatar: Peer? = null,
    /** Long-pressing opens the tab arranger instead of a menu of its own. */
    val arrangeable: Boolean = false,
)

@Immutable
data class DockAccount(
    val account: Int,
    val peer: Peer,
    val name: String,
    val handle: String,
    /** "5 条未读", "当前" and the like. */
    val meta: String,
    val current: Boolean,
)

@Immutable
data class DockContact(val peer: Peer, val name: String, val online: Boolean, val subtitle: String? = null)

/** A key in the 起点 panel: 新对话, 新群组 and so on. */
@Immutable
data class DockKey(val id: Int, val glyph: Glyph, val label: String)

/** A tab the person may put in the middle of the dock. */
@Immutable
data class DockChoice(val id: Int, val glyph: Glyph, val label: String)

@Immutable
data class DockStrings(
    val create: String,
    val close: String,
    val searchHint: String,
    val recent: String,
    val noRecent: String,
    val searching: String,
    val noResults: String,
    val releaseToSwitch: String,
    val releaseToAdd: String,
    val releaseToMore: String,
    val slideToPick: String,
    val tapToPick: String,
    val addAccount: String,
    val moreAccounts: String,
    val arrangeTitle: String,
    val arrangeHint: String,
    val arrangeFull: String,
    val switchAccountAction: String,
    val arrangeAction: String,
)

enum class DockMode {
    /** Just the bar. */
    Rail,
    /** Grown into the 起点 panel by the knob. */
    Create,
    /** The account cards fanned out above the 我 key. */
    Accounts,
    /** Grown into the panel that picks the middle tabs. */
    Arrange,
}

/** What the dock asks of its host. Positions are tab indices in [DockState.tabs]. */
interface DockActions {
    fun onTab(index: Int)
    /** Long press on a tab that is not arrangeable and not 我. [x], [y] is its centre in the dock view. */
    fun onTabMenu(index: Int, x: Float, y: Float)
    fun onModeChanged(mode: DockMode)
    fun onKey(id: Int)
    fun onQuery(query: String)
    fun onContact(contact: DockContact)
    fun onAccount(account: Int)
    fun onAddAccount()
    fun onMoreAccounts()
    /** A sideways swipe over the 我 key: move to the next or previous account. */
    fun onCycleAccount(forward: Boolean)
    /** The middle tabs, in order, as picked in the arranger. */
    fun onArrange(choices: List<Int>)
}

/**
 * Everything the dock shows. The host writes it; the dock reads it and
 * changes only [mode], [collapsed] and [query] on its own, telling the host
 * through [DockActions].
 */
@Stable
class DockState(strings: DockStrings) {
    var strings by mutableStateOf(strings)
    var tabs by mutableStateOf(emptyList<DockTab>())

    /** The current tab, fractional while the pages are being swiped between two. */
    var position by mutableFloatStateOf(0f)

    /** Folded to a small capsule while a list scrolls down. */
    var collapsed by mutableStateOf(false)

    /** Off screen while the current page needs the space, e.g. while searching. */
    var shown by mutableStateOf(true)

    var mode by mutableStateOf(DockMode.Rail)

    var keys by mutableStateOf(emptyList<DockKey>())
    var query by mutableStateOf("")
    var recents by mutableStateOf(emptyList<DockContact>())

    /** Search results, or null while there is no query. */
    var results by mutableStateOf<List<DockContact>?>(null)
    var searching by mutableStateOf(false)

    var accounts by mutableStateOf(emptyList<DockAccount>())
    var canAddAccount by mutableStateOf(true)

    var choices by mutableStateOf(emptyList<DockChoice>())
    var chosen by mutableStateOf(emptyList<Int>())
    var maxChosen by mutableIntStateOf(3)

    // Insets in pixels. [bottomInset] covers the navigation bar and anything
    // the host keeps above it; [imeInset] is the keyboard.
    var topInset by mutableIntStateOf(0)
    var bottomInset by mutableIntStateOf(0)
    var startInset by mutableIntStateOf(0)
    var imeInset by mutableIntStateOf(0)

    val isOpen: Boolean get() = mode != DockMode.Rail
}
