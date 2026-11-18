package circolareplus.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NothingExperienceTest {
    @Test fun nothingTextAndSelectionsHaveReadableContrastInBothThemes() {
        val previousStyle=AppTheme.uiStyle;val previousDark=AppTheme.isDarkMode
        try {
            AppTheme.uiStyle=UiStyle.NOTHING
            for(dark in listOf(false,true)) {
                AppTheme.isDarkMode=dark
                for((ink,paper) in listOf(AppTheme.TextDark to AppTheme.BackgroundLight,
                    AppTheme.TextMuted to AppTheme.CardSurface,AppTheme.TextFaint to AppTheme.CardSurface,AppTheme.OnSelection to AppTheme.SelectionContainer,
                    AppTheme.BackgroundLight to AppTheme.TextDark)) {
                    assertTrue(contrast(ink,paper)>=4.5,"Contrast ${contrast(ink,paper)} dark=$dark")
                }
            }
        } finally { AppTheme.uiStyle=previousStyle;AppTheme.isDarkMode=previousDark }
    }
    @Test fun oldPreferencesKeepVisibilityAndNotificationChannelsButRequireOptInForConflictLights() {
        val previous = """{"preferences":{"showFocusWidget":false,"dotDisplay":false,"rearLights":true,"systemNotifications":true},"focus":{"deadlineMillis":123}}"""
        var saved = ""
        NothingExperience.restore(previous) { saved = it }
        try {
            assertFalse(NothingExperience.preferences.showCalendarGlyph)
            assertFalse(NothingExperience.preferences.dotDisplay)
            assertFalse(NothingExperience.preferences.conflictLights)
            assertTrue(NothingExperience.preferences.systemNotifications)
            NothingExperience.configure(NothingExperience.preferences.copy(conflictLights = true))
            assertTrue(NothingExperience.readPreferences(saved).conflictLights)
            assertFalse(NothingExperience.readPreferences(saved).showCalendarGlyph)
        } finally { NothingExperience.restore("{}") {} }
    }

    @Test fun notificationGroupsRouteRankingAndUnknownTypes() {
        assertEquals(NothingNotificationGroup.POLLS, NothingNotificationGroup.fromCategory("ranking_polls"))
        assertEquals(NothingNotificationGroup.CLASS, NothingNotificationGroup.fromCategory("seatmap"))
        assertFalse(NothingExperience.readPreferences("broken").systemNotifications)
    }
    private fun contrast(a:Color,b:Color):Float {
        val x=a.luminance();val y=b.luminance()
        return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
    }
}
