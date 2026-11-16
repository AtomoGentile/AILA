package circolareplus.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NothingExperienceTest {
    @Test fun deadlineSurvivesPauseResumeAndTimeAwayFromTheApp() {
        val start = 100_000L
        val session = NothingFocusSession.fresh(25).start(start)
        assertEquals(1500,session.remaining(start))
        val paused = session.pause(start+61_000)
        assertEquals(1439,paused.remaining(start+600_000))
        val resumed = paused.start(start+600_000)
        assertEquals(1379,resumed.remaining(start+660_000))
        assertEquals(0,resumed.remaining(start+3_000_000))
    }
    @Test fun preferencesAndActiveDeadlineAreRestoredWithoutResettingTheTimer() {
        var saved = ""
        NothingExperience.restore("{}") { saved = it }
        NothingExperience.configure(NothingPreferences(face=GlyphFace.ORBIT,rhythm=GlyphRhythm.CALM,focusMinutes=15,signature="STUDIO"))
        NothingExperience.toggleFocus(10_000)
        val deadline = NothingExperience.focus.deadlineMillis
        NothingExperience.restore(saved) { saved = it }
        assertEquals(GlyphFace.ORBIT,NothingExperience.preferences.face)
        assertEquals("STUDIO",NothingExperience.preferences.signature)
        assertEquals(deadline,NothingExperience.focus.deadlineMillis)
        assertEquals(840,NothingExperience.focus.remaining(70_000))
        NothingExperience.restore("{}") {}
    }
    @Test fun completionIsCountedOnlyOnceEvenWhenSeveralScreensObserveTheDeadline() {
        NothingExperience.restore("{}") {}
        NothingExperience.configure(NothingPreferences(focusMinutes=5))
        NothingExperience.toggleFocus(1000)
        assertTrue(NothingExperience.completeIfDue(301_000))
        assertFalse(NothingExperience.completeIfDue(302_000))
        assertEquals(1,NothingExperience.completedSessions)
        assertTrue(NothingExperience.focus.completed)
        assertFalse(NothingExperience.focus.running)
        NothingExperience.restore("{}") {}
    }
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
    private fun contrast(a:Color,b:Color):Float {
        val x=a.luminance();val y=b.luminance()
        return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
    }
}
