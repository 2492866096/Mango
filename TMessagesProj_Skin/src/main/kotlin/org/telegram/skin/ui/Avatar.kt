package org.telegram.skin.ui

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import org.telegram.skin.theme.Skin
import java.util.Locale
import kotlin.math.abs

/**
 * Someone with a face: a user or chat as seen from one account. [version]
 * changes when the picture does, so a cached avatar gets rebuilt.
 */
@Immutable
data class Peer(val account: Int, val id: Long, val name: String, val version: Int = 0)

/** Builds a view that loads and draws a peer's picture. The host supplies it. */
fun interface AvatarViews {
    fun create(context: Context, account: Int, peerId: Long): View
}

/** What the host plugs in before showing any skin screen. */
object SkinHost {
    /** Real avatars. Left unset (previews, tests) the skin draws letters. */
    @JvmStatic
    var avatarViews: AvatarViews? = null
}

/**
 * A peer's picture, cut to [shape]. Until the picture loads, and in previews,
 * the peer shows as an initial on one of the muted grounds, picked by id so a
 * person keeps their colour everywhere.
 */
@Composable
fun Avatar(peer: Peer, size: Dp, modifier: Modifier = Modifier, shape: Shape = Skin.shapes.avatar) {
    val c = Skin.colors
    val host = SkinHost.avatarViews
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(c.avatars[(abs(peer.id) % c.avatars.size).toInt()]),
        contentAlignment = Alignment.Center,
    ) {
        if (host != null && !LocalInspectionMode.current) {
            key(peer.account, peer.id, peer.version) {
                AndroidView(
                    factory = { host.create(it, peer.account, peer.id) },
                    modifier = Modifier.matchParentSize(),
                )
            }
        } else {
            // The letter keeps its size inside the box whatever the font scale.
            val fontSize = with(LocalDensity.current) { (size * 0.4f).toSp() }
            BasicText(initialOf(peer.name), style = Skin.type.name.copy(color = c.avatarText, fontSize = fontSize))
        }
    }
}

internal fun initialOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, 1)).uppercase(Locale.ROOT)
}
