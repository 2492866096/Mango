package org.telegram.skin

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.telegram.skin.theme.SkinTheme

/**
 * Hosts one skin screen inside Telegram's view tree. It is a plain
 * FrameLayout so Java callers never see a Compose type.
 *
 * Telegram moves fragment views in and out of the window all the time, so
 * the composition is kept across detaches and only freed by [dispose], which
 * the owning screen calls when it is destroyed.
 */
abstract class SkinHostView(context: Context) : FrameLayout(context) {
    private var dark by mutableStateOf(false)
    private var rtl by mutableStateOf(false)
    private val compose = ComposeView(context)

    init {
        compose.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        compose.setContent { SkinTheme(dark = dark, rtl = rtl) { Content() } }
        addView(compose, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Follows Telegram's theme and chosen language rather than the system's. */
    fun setTheme(dark: Boolean, rtl: Boolean) {
        this.dark = dark
        this.rtl = rtl
    }

    fun dispose() = compose.disposeComposition()

    @Composable
    protected abstract fun Content()
}
