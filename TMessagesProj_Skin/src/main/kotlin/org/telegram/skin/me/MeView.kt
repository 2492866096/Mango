package org.telegram.skin.me

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.telegram.skin.SkinHostView

/** The 我 page as a view, for the fragment that hosts it. */
class MeView(context: Context, strings: MeStrings) : SkinHostView(context) {
    val state = MeState(strings)
    private var actionsState by mutableStateOf<MeActions?>(null)

    var actions: MeActions?
        get() = actionsState
        set(value) {
            actionsState = value
        }

    @Composable
    override fun Content() {
        val actions = actionsState ?: return
        MePage(state, actions)
    }
}
