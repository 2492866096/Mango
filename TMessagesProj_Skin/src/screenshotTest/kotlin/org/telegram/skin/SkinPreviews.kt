package org.telegram.skin

import android.content.res.Configuration
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import org.telegram.skin.theme.SkinTheme

/**
 * The five ways every redesigned page gets rendered for review: both themes,
 * the largest system font, a phone on its side, and a right-to-left locale.
 * Wrap the content in [PreviewSkin] so it picks the variant up.
 */
@Preview(name = "light", widthDp = 390, heightDp = 844)
@Preview(name = "dark", widthDp = 390, heightDp = 844, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "font2x", widthDp = 390, heightDp = 844, fontScale = 2f)
@Preview(name = "landscape", widthDp = 844, heightDp = 390, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "rtl", widthDp = 390, heightDp = 844, locale = "ar")
annotation class SkinPreviews

/** [SkinTheme] driven by the preview's configuration instead of Telegram's settings. */
@Composable
fun PreviewSkin(content: @Composable () -> Unit) = SkinTheme(
    dark = isSystemInDarkTheme(),
    rtl = LocalConfiguration.current.layoutDirection == View.LAYOUT_DIRECTION_RTL,
    content = content,
)
