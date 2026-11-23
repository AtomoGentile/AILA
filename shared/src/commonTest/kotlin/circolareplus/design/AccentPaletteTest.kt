package circolareplus.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccentPaletteTest {
    @Test
    fun materialContainersUseTheSelectedAccentForEachRole() {
        val style = AppTheme.uiStyle
        val dark = AppTheme.isDarkMode
        val primary = AppTheme.accent
        val secondary = AppTheme.secondaryAccent
        val tertiary = AppTheme.tertiaryAccent
        try {
            for (uiStyle in listOf(UiStyle.EXPRESSIVE, UiStyle.GLASS)) {
                AppTheme.uiStyle = uiStyle
                for (isDark in listOf(false, true)) {
                    AppTheme.isDarkMode = isDark
                    AppTheme.accent = AilaAccent.PINK
                    AppTheme.secondaryAccent = AilaAccent.RUBY
                    AppTheme.tertiaryAccent = AilaAccent.AMBER
                    val scheme = ailaColorScheme()
                    assertEquals(Color(if (isDark) AilaAccent.PINK.containerDark else AilaAccent.PINK.containerLight), scheme.primaryContainer)
                    assertEquals(Color(if (isDark) AilaAccent.RUBY.containerDark else AilaAccent.RUBY.containerLight), scheme.secondaryContainer)
                    assertEquals(Color(if (isDark) AilaAccent.AMBER.containerDark else AilaAccent.AMBER.containerLight), scheme.tertiaryContainer)
                    assertEquals(Color(if (isDark) AilaAccent.AMBER.onContainerDark else AilaAccent.AMBER.onContainerLight), scheme.onTertiaryContainer)

                    AppTheme.secondaryAccent = AilaAccent.INDIGO
                    val updated = ailaColorScheme()
                    assertEquals(scheme.primaryContainer, updated.primaryContainer)
                    assertEquals(Color(if (isDark) AilaAccent.INDIGO.containerDark else AilaAccent.INDIGO.containerLight), updated.secondaryContainer)
                    assertEquals(scheme.tertiaryContainer, updated.tertiaryContainer)
                }
            }
        } finally {
            AppTheme.uiStyle = style
            AppTheme.isDarkMode = dark
            AppTheme.accent = primary
            AppTheme.secondaryAccent = secondary
            AppTheme.tertiaryAccent = tertiary
        }
    }

    @Test
    fun glyphsOnSolidAccentsMeetTextContrast() {
        for (accent in AilaAccent.entries) {
            for (value in listOf(accent.primaryLight, accent.primaryDark)) {
                val background = Color(value)
                val foreground = contrastingAccentInk(background)
                val lighter = maxOf(background.luminance(), foreground.luminance())
                val darker = minOf(background.luminance(), foreground.luminance())
                assertTrue((lighter + 0.05f) / (darker + 0.05f) >= 4.5f, accent.label)
            }
        }
    }
}
