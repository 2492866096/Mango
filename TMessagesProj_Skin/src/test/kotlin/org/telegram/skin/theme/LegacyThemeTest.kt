package org.telegram.skin.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegacyThemeTest {

    // Gradle runs unit tests from the module directory.
    private val module = File(System.getProperty("user.dir"))
    private val repo = module.parentFile
    private val legacy = LegacyTheme(repo)

    @Test
    fun overridesUseRealKeys() {
        for (colors in listOf(FrostLight, FrostDark)) {
            for (out in listOf(colors.bubbleGraphite, colors.bubbleCeladon)) {
                val unknown = designOverrides(colors, out).keys - legacy.validNames
                assertTrue("Not Telegram theme keys: $unknown", unknown.isEmpty())
            }
        }
    }

    @Test
    fun themeFilesMatchTokens() {
        // Each theme with its own outgoing bubble, and with the other one for the 气泡风格 setting.
        check("skin_frost.attheme", "day.attheme", DAY_BLUE, FrostLight, FrostLight.bubbleGraphite)
        check("skin_frost_celadon.attheme", "day.attheme", DAY_BLUE, FrostLight, FrostLight.bubbleCeladon)
        check("skin_frost_night.attheme", "night.attheme", NIGHT_BLUE, FrostDark, FrostDark.bubbleCeladon)
        check("skin_frost_night_graphite.attheme", "night.attheme", NIGHT_BLUE, FrostDark, FrostDark.bubbleGraphite)
    }

    private fun check(target: String, base: String, baseAccent: Int, colors: SkinColors, out: Bubble) {
        val text = legacy.generate(
            base = File(repo, "TMessagesProj/src/main/assets/$base"),
            baseAccent = baseAccent,
            accent = colors.led,
            isDark = colors.isDark,
            overrides = designOverrides(colors, out),
        )
        val file = File(module, "src/main/assets/$target")
        if (System.getProperty("skin.regen") == "true") {
            file.parentFile.mkdirs()
            file.writeText(text)
            return
        }
        assertTrue("$target is out of date with the tokens; rerun with -Pskin.regen=true",
            file.isFile && file.readText() == text)
    }

    private companion object {
        // Accent 0 of the stock Day and Night themes in Theme.java: the blue
        // their files are drawn in, which the recolor pass starts from.
        val DAY_BLUE = 0xFF4C91DF.toInt()
        val NIGHT_BLUE = 0xFF3E88F7.toInt()
    }
}
