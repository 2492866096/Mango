package org.telegram.skin.dock

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.telegram.skin.SkinHostView

/**
 * The dock as a view for Telegram's tab screen. Lay it over the whole screen;
 * it lets touches through everywhere except on the dock and, while something
 * is open, the backdrop.
 */
class DockView(context: Context, strings: DockStrings) : SkinHostView(context) {
    val state = DockState(strings)
    private var actionsState by mutableStateOf<DockActions?>(null)

    var actions: DockActions?
        get() = actionsState
        set(value) {
            actionsState = value
        }

    @Composable
    override fun Content() {
        val actions = actionsState ?: return
        DockOverlay(state, actions)
    }
}
