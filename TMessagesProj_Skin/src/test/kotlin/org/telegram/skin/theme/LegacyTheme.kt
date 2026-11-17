package org.telegram.skin.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Builds the two Frost theme files for Telegram's own (legacy) screens, so the
 * screens we haven't redrawn still wear the new palette.
 *
 * Telegram's theme files are flat "name=argb" lists. We start from the stock
 * Day and Night files and do two passes:
 *
 * 1. Recolor every leftover blue the way Telegram itself does when you pick an
 *    accent color (Theme.changeColorAccent), turning it celadon. This catches
 *    the hundreds of keys we would never list by hand: switches, progress
 *    rings, badges deep inside settings.
 * 2. Pin the keys that carry the design (surfaces, text, bubbles, the chat
 *    list) straight to the design tokens.
 *
 * The inputs are parsed from Telegram's sources in TMessagesProj, so a change
 * there shows up as a test failure here rather than as a silently stale file.
 */
internal class LegacyTheme(private val repo: File) {

    private val themeColors = File(repo, "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ThemeColors.java").readText()
    private val theme = File(repo, "TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java").readText()

    /** key_ identifier -> name used in theme files. */
    private val names: Map<String, String> =
        Regex("""colorKeysMap\.put\(key_(\w+),\s*"(\w+)"\)""").findAll(themeColors)
            .associate { it.groupValues[1] to it.groupValues[2] }

    val validNames: Set<String> = names.values.toSet()

    private val constants: Map<String, Int> =
        Regex("""static final int (\w+)\s*=\s*(0x[0-9a-fA-F]{8})\s*;""").findAll(themeColors + theme)
            .associate { it.groupValues[1] to it.groupValues[2].removePrefix("0x").toLong(16).toInt() }

    /** Built-in defaults, by name. Entries that are expressions rather than plain values are skipped. */
    private val defaults: Map<String, Int> = buildMap {
        Regex("""defaultColors\[key_(\w+)\]\s*=\s*([^;]+);""").findAll(themeColors).forEach { m ->
            val name = names[m.groupValues[1]] ?: return@forEach
            val expr = m.groupValues[2].trim()
            val value = when {
                expr.matches(Regex("0x[0-9a-fA-F]{1,8}")) -> expr.removePrefix("0x").toLong(16).toInt()
                expr in constants -> constants.getValue(expr)
                else -> return@forEach
            }
            put(name, value)
        }
    }

    /** Keys that read another key's color when a theme leaves them out. */
    private val fallbacks: Map<String, String> =
        Regex("""fallbackKeys\.put\(key_(\w+),\s*key_(\w+)\)""").findAll(theme)
            .mapNotNull { m -> names[m.groupValues[1]]?.let { k -> names[m.groupValues[2]]?.let { k to it } } }
            .toMap()

    /** Keys Telegram never recolors with the accent: avatars, chart lines, group call UI, code blocks. */
    private val accentExcluded: Set<String> = buildSet {
        Regex("""themeAccentExclusionKeys\.add\(key_(\w+)\)""").findAll(theme)
            .forEach { m -> names[m.groupValues[1]]?.let(::add) }
        val arrays = Regex("""(keys_\w+)\s*=\s*\{([^}]*)\}""").findAll(theme)
            .associate { it.groupValues[1] to it.groupValues[2] }
        Regex("""themeAccentExclusionKeys\.add\((keys_\w+)\[i\]\)""").findAll(theme).forEach { m ->
            arrays[m.groupValues[1]]?.split(',')?.map { it.trim().removePrefix("key_") }
                ?.forEach { id -> names[id]?.let(::add) }
        }
    }

    fun generate(base: File, baseAccent: Int, accent: Color, isDark: Boolean, overrides: Map<String, Color?>): String {
        val baseColors = parse(base)
        val baseHsv = colorToHsv(baseAccent)
        val accentHsv = colorToHsv(accent.toArgb())

        val out = sortedMapOf<String, Int>()
        out.putAll(baseColors)
        // Same walk as the accent pass in Theme.ThemeAccent: every known key, the
        // file's value if it has one, otherwise the built-in default.
        for (name in validNames) {
            if (name in accentExcluded) continue
            val color = baseColors[name] ?: run {
                val fallback = fallbacks[name]
                if (fallback != null && fallback in baseColors) return@run null
                defaults[name]
            } ?: continue
            val recolored = changeColorAccent(baseHsv, accentHsv, color, isDark)
            if (recolored != color || name in baseColors) out[name] = recolored
        }
        for ((name, color) in overrides) {
            if (color == null) out.remove(name) else out[name] = color.toArgb()
        }
        return out.entries.joinToString("\n", postfix = "\n") { "${it.key}=${it.value}" }
    }

    private fun parse(file: File): Map<String, Int> = buildMap {
        for (line in file.readLines()) {
            if (line.startsWith("WPS") || line.startsWith("WLS")) break
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val value = line.substring(eq + 1).trim()
            put(line.substring(0, eq), if (value.startsWith("#")) value.substring(1).toLong(16).toInt() else value.toInt())
        }
    }

    // A straight port of Theme.changeColorAccent, with android.graphics.Color
    // swapped for the Skia routines it calls, so the result matches the app.
    private fun changeColorAccent(baseHsv: FloatArray, accentHsv: FloatArray, color: Int, isDark: Boolean): Int {
        val hsv = colorToHsv(color)
        val diffH = min(abs(hsv[0] - baseHsv[0]), abs(hsv[0] - baseHsv[0] - 360f))
        if (diffH > 30f) return color

        val dist = min(1.5f * hsv[1] / baseHsv[1], 1f)
        hsv[0] = hsv[0] + accentHsv[0] - baseHsv[0]
        hsv[1] = hsv[1] * accentHsv[1] / baseHsv[1]
        hsv[2] = hsv[2] * (1f - dist + dist * accentHsv[2] / baseHsv[2])
        var result = hsvToColor(color ushr 24, hsv)

        val before = brightness(color)
        val after = brightness(result)
        // Keep colors lighter in dark themes and darker in light ones.
        if (if (isDark) before > after else before < after) {
            val amountOfNew = 0.6f
            result = scaleBrightness(result, (1f - amountOfNew) * before / after + amountOfNew)
        }
        return result
    }

    private fun brightness(c: Int) = (red(c) * 0.2126f + green(c) * 0.7152f + blue(c) * 0.0722f) / 255f

    private fun scaleBrightness(c: Int, amount: Float): Int {
        fun ch(v: Int) = (v * amount).toInt().coerceIn(0, 255)
        return argb(c ushr 24, ch(red(c)), ch(green(c)), ch(blue(c)))
    }

    private fun red(c: Int) = c shr 16 and 0xFF
    private fun green(c: Int) = c shr 8 and 0xFF
    private fun blue(c: Int) = c and 0xFF
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    /** SkRGBToHSV. */
    private fun colorToHsv(c: Int): FloatArray {
        val r = red(c); val g = green(c); val b = blue(c)
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val delta = mx - mn
        val v = mx / 255f
        if (delta == 0) return floatArrayOf(0f, 0f, v)
        val s = delta.toFloat() / mx
        var h = when (mx) {
            r -> (g - b).toFloat() / delta
            g -> 2f + (b - r).toFloat() / delta
            else -> 4f + (r - g).toFloat() / delta
        } * 60f
        if (h < 0) h += 360f
        return floatArrayOf(h, s, v)
    }

    /** SkHSVToColor, including its habit of treating an out-of-range hue as red. */
    private fun hsvToColor(alpha: Int, hsv: FloatArray): Int {
        val s = hsv[1].coerceIn(0f, 1f)
        val v = hsv[2].coerceIn(0f, 1f)
        fun round(x: Float) = floor(x + 0.5f).toInt()
        val vByte = round(v * 255)
        if (s <= 0f) return argb(alpha, vByte, vByte, vByte)
        val hx = if (hsv[0] < 0f || hsv[0] >= 360f) 0f else hsv[0] / 60f
        val w = floor(hx)
        val f = hx - w
        val p = round((1 - s) * v * 255)
        val q = round((1 - s * f) * v * 255)
        val t = round((1 - s * (1 - f)) * v * 255)
        return when (w.toInt()) {
            0 -> argb(alpha, vByte, t, p)
            1 -> argb(alpha, q, vByte, p)
            2 -> argb(alpha, p, vByte, t)
            3 -> argb(alpha, p, q, vByte)
            4 -> argb(alpha, t, p, vByte)
            else -> argb(alpha, vByte, p, q)
        }
    }
}

/**
 * The keys that carry the design, pinned to tokens. A null value drops the key
 * from the file, which is how the stock out-bubble gradients are removed:
 * bubbles here are flat. [out] is the outgoing bubble: the theme's own by
 * default, the other one for the 气泡风格 setting's fixed choices.
 */
internal fun designOverrides(c: SkinColors, out: Bubble = c.bubbleOut): Map<String, Color?> {
    val onInk = if (c.isDark) c.page else Color.White
    val inn = c.bubbleIn
    val selection = c.text.copy(alpha = 0.06f)
    val mutedCounter = if (c.isDark) Color(0xFF3A4247) else Color(0xFFB9C2C6)
    val switchOff = if (c.isDark) Color(0xFF3A4247) else Color(0xFFC9D2D6)
    val avatarKeys = listOf("Red", "Orange", "Violet", "Green", "Cyan", "Blue", "Pink")
    // Pink reuses the warm rose; there are seven Telegram slots and six grounds.
    val avatarGround = listOf(4, 1, 3, 2, 5, 0, 4).map { c.avatars[it] }

    return buildMap {
        // Surfaces.
        put("windowBackgroundWhite", c.surface)
        put("windowBackgroundGray", c.page)
        put("divider", c.hairline)
        put("listSelectorSDK21", selection)
        put("dialogBackground", c.surface)
        put("dialogBackgroundGray", c.page)

        // The header is the page itself, not a colored bar.
        put("actionBarDefault", c.page)
        put("actionBarDefaultIcon", c.text)
        put("actionBarDefaultTitle", c.text)
        // The chat list's title (the logo, or 对话 in the skin) has a colour of its own,
        // missing from the stock theme files and so falling back to Telegram blue.
        put("telegram_color_dialogsLogo", c.text)
        put("actionBarDefaultSubtitle", c.textSecondary)
        // The chat header's "online" and typing in celadon ink.
        put("chat_status", c.ink)
        put("actionBarDefaultSelector", selection)
        put("actionBarDefaultSearch", c.text)
        put("actionBarDefaultSearchPlaceholder", c.textSecondary)
        put("actionBarDefaultSubmenuBackground", c.surface)
        put("actionBarDefaultSubmenuItem", c.text)
        put("actionBarDefaultSubmenuItemIcon", c.textSecondary)
        put("actionBarActionModeDefault", c.surface)
        put("actionBarActionModeDefaultIcon", c.text)

        // Text.
        put("windowBackgroundWhiteBlackText", c.text)
        for (k in listOf("", "2", "3", "4", "8")) put("windowBackgroundWhiteGrayText$k", c.textSecondary)
        for (k in listOf("windowBackgroundWhiteBlueText", "windowBackgroundWhiteBlueText2", "windowBackgroundWhiteBlueText4",
            "windowBackgroundWhiteBlueHeader", "windowBackgroundWhiteLinkText", "windowBackgroundWhiteValueText",
            "windowBackgroundWhiteBlueButton", "windowBackgroundWhiteBlueIcon")) put(k, c.ink)
        put("windowBackgroundWhiteInputField", c.hairline)
        put("windowBackgroundWhiteInputFieldActivated", c.led)
        put("dialogTextBlack", c.text)
        put("dialogTextGray", c.textSecondary)
        put("dialogTextLink", c.ink)
        put("dialogTextBlue", c.ink)
        put("dialogButton", c.ink)
        put("dialogInputField", c.hairline)
        put("dialogInputFieldActivated", c.led)
        put("dialogRoundCheckBox", c.ink)
        for (k in listOf("text_RedRegular", "text_RedBold", "fill_RedNormal", "fill_RedDark")) put(k, c.danger)

        // Controls.
        put("switchTrack", switchOff)
        put("switchTrackChecked", c.led)
        put("checkbox", c.ink)
        put("checkboxCheck", c.surface)
        put("checkboxSquareBackground", c.ink)
        put("radioBackgroundChecked", c.ink)
        put("featuredStickers_addButton", c.ink)
        put("featuredStickers_buttonText", onInk)

        // Chat list.
        put("chats_name", c.text)
        put("chats_message", c.textSecondary)
        put("chats_date", c.textSecondary)
        put("chats_nameMessage", c.text)
        put("chats_attachMessage", c.ink)
        put("chats_actionMessage", c.ink)
        put("chats_draft", c.danger)
        put("chats_unreadCounter", c.ink)
        put("chats_unreadCounterMuted", mutedCounter)
        put("chats_unreadCounterText", onInk)
        put("chats_tabUnreadActiveBackground", c.ink)
        put("chats_onlineCircle", c.led)
        put("chats_sentCheck", c.ink)
        put("chats_sentReadCheck", c.ink)
        put("chats_pinnedIcon", c.textTertiary)
        put("chats_muteIcon", c.textTertiary)
        put("chats_verifiedBackground", c.ink)
        put("chats_verifiedCheck", c.surface)
        put("chats_secretName", c.ink)
        put("chats_secretIcon", c.ink)
        // The compose button is graphite metal, not an accent blob.
        put("chats_actionBackground", c.text)
        put("chats_actionPressedBackground", c.textSecondary)
        put("chats_actionIcon", c.page)

        // Letter avatars: muted grounds with dark initials, as in the new screens.
        avatarKeys.forEachIndexed { i, k ->
            put("avatar_background$k", avatarGround[i])
            put("avatar_background2$k", avatarGround[i])
        }
        put("avatar_text", c.avatarText)

        // Unseen stories wear a solid celadon ring. Telegram keeps these out of
        // the accent pass, so they would otherwise stay blue at night.
        for (k in listOf("stories_circle1", "stories_circle2", "stories_circle_dialog1", "stories_circle_dialog2")) put(k, c.led)

        // Chat.
        put("chat_wallpaper", c.chat)
        put("chat_inBubble", inn.background)
        put("chat_inBubbleSelected", inn.quote)
        put("chat_inBubbleShadow", Color.Transparent)
        put("chat_outBubble", out.background)
        put("chat_outBubbleSelected", out.quote)
        put("chat_outBubbleShadow", Color.Transparent)
        for (k in listOf("chat_outBubbleGradient", "chat_outBubbleGradient2", "chat_outBubbleGradient3", "chat_outBubbleGradientAnimated")) put(k, null)
        put("chat_messageTextIn", inn.text)
        put("chat_messageTextOut", out.text)
        put("chat_inTimeText", inn.meta)
        put("chat_outTimeText", out.meta)
        put("chat_inTimeSelectedText", inn.meta)
        put("chat_outTimeSelectedText", out.meta)
        put("chat_inViews", inn.meta)
        put("chat_outViews", out.meta)
        put("chat_outSentClock", out.meta)
        put("chat_outSentCheck", out.tick)
        put("chat_outSentCheckRead", out.tick)
        put("chat_outSentCheckSelected", out.tick)
        put("chat_outSentCheckReadSelected", out.tick)
        put("chat_messageLinkIn", c.ink)
        put("chat_messageLinkOut", out.tick)
        put("chat_inReplyLine", c.led)
        put("chat_outReplyLine", out.tick)
        put("chat_inReplyNameText", c.ink)
        put("chat_outReplyNameText", out.tick)
        put("chat_inReplyMessageText", inn.text)
        put("chat_outReplyMessageText", out.text)
        put("chat_inForwardedNameText", c.ink)
        put("chat_outForwardedNameText", out.tick)
        // Play and download keys in a bubble are a solid disc of its text colour, the glyph cut in the bubble's own.
        put("chat_inLoader", inn.text)
        put("chat_inLoaderSelected", inn.text)
        put("chat_inMediaIcon", inn.background)
        put("chat_inMediaIconSelected", inn.background)
        put("chat_outLoader", out.text)
        put("chat_outLoaderSelected", out.text)
        put("chat_outMediaIcon", out.background)
        put("chat_outMediaIconSelected", out.background)
        put("chat_inAudioProgress", inn.background)
        put("chat_outAudioProgress", out.background)
        put("chat_inAudioSeekbarFill", c.ink)
        put("chat_outAudioSeekbarFill", out.tick)
        put("chat_inInstant", c.ink)
        put("chat_outInstant", out.tick)
        put("chat_textSelectBackground", c.led.copy(alpha = 0.3f))
        put("chat_linkSelectBackground", c.led.copy(alpha = 0.2f))
        put("chat_outLinkSelectBackground", out.tick.copy(alpha = 0.25f))
        put("chat_serviceBackground", if (c.isDark) Color.White.copy(alpha = 0.06f) else c.text.copy(alpha = 0.06f))
        put("chat_serviceText", if (c.isDark) Color(0xFFA3ADB2) else Color(0xFF4D585E))
        put("chat_messagePanelBackground", if (c.isDark) Color(0xFF1A1F22) else c.surface)
        put("chat_messagePanelText", c.text)
        put("chat_messagePanelHint", c.textSecondary)
        put("chat_messagePanelIcons", c.textSecondary)
        put("chat_messagePanelSend", c.ink)
        put("chat_messagePanelVoiceBackground", c.ink)
        put("chat_recordedVoiceBackground", c.ink)
    }
}
