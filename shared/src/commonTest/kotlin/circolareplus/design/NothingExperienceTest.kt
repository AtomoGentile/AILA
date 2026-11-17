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
    @Test fun radarDeduplicatesAndPrioritizesTodayAndRespectsDisabledChannels() {
        val later = GlyphRadarEntry("later", GlyphChannel.CALENDAR, "Domani", "", false) {}
        val today = GlyphRadarEntry("today", GlyphChannel.CALENDAR, "Oggi", "", true) {}
        val circular = GlyphRadarEntry("c1", GlyphChannel.CIRCULARS, "Circolare", "") {}
        val poll = GlyphRadarEntry("p1", GlyphChannel.CLASS, "Voto", "") {}
        assertEquals(listOf("today", "later", "c1", "p1"), radarEntries(listOf(later,circular,today,today,poll),NothingPreferences()).map { it.key })
        assertEquals(listOf(poll),radarEntries(listOf(later,circular,today,poll),NothingPreferences(radarEvents=false,radarCirculars=false)))
        assertTrue(radarEntries(listOf(later,circular,poll),NothingPreferences(radarEvents=false,radarCirculars=false,radarClass=false)).isEmpty())
    }
    @Test fun radarChoicesSurviveRestoreAndOldSettingsRetainDefaultChannels() {
        var saved=""
        NothingExperience.restore("{}") { saved=it }
        NothingExperience.configure(NothingPreferences(radarEvents=false,radarClass=false))
        NothingExperience.restore(saved) {}
        assertFalse(NothingExperience.preferences.radarEvents)
        assertTrue(NothingExperience.preferences.radarCirculars)
        assertFalse(NothingExperience.preferences.radarClass)
        NothingExperience.restore("{}") {}
        assertTrue(NothingExperience.preferences.radarEvents)
        assertTrue(NothingExperience.preferences.radarClass)
    }
    @Test fun phone4aProgressClearsSegmentsFromTheTopWithoutOutOfBoundsIndices() {
        assertEquals(listOf(5,4,3,2,1,0),nothingBarChannels(1f,6))
        assertEquals(listOf(5,4,3),nothingBarChannels(.5f,6))
        assertEquals(listOf(5),nothingBarChannels(.01f,6))
        assertTrue(nothingBarChannels(0f,6).isEmpty())
        assertTrue(nothingBarChannels(Float.NaN,6).isEmpty())
        assertTrue(nothingBarChannels(1f,0).isEmpty())
        assertEquals(listOf(3,2,1,0),nothingBarChannels(2f,4))
    }
    @Test fun notificationGroupsRouteRankingAndUnknownTypesAndPreferencesDoNotResetActiveFocus() {
        assertEquals(NothingNotificationGroup.POLLS,NothingNotificationGroup.fromCategory("ranking_polls"))
        assertEquals(NothingNotificationGroup.CLASS,NothingNotificationGroup.fromCategory("seatmap"))
        NothingExperience.restore("{}") {}
        NothingExperience.toggleFocus(1000)
        val deadline=NothingExperience.focus.deadlineMillis
        assertTrue(NothingExperience.readPreferences("{\"preferences\":{\"systemNotifications\":true}}").systemNotifications)
        assertEquals(deadline,NothingExperience.focus.deadlineMillis)
        assertFalse(NothingExperience.readPreferences("broken").systemNotifications)
        NothingExperience.restore("{}") {}
    }
    private fun contrast(a:Color,b:Color):Float {
        val x=a.luminance();val y=b.luminance()
        return (maxOf(x,y)+.05f)/(minOf(x,y)+.05f)
    }
}
