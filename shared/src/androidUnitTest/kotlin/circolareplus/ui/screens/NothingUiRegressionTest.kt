package circolareplus.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import circolareplus.design.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h850dp-xxhdpi", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NothingUiRegressionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>

    @Before fun setup() {
        AppTheme.uiStyle = UiStyle.NOTHING
        AppTheme.isDarkMode = false
        NothingExperience.restore("{}") {}
        activity = Robolectric.buildActivity(ComponentActivity::class.java)
        activity.get().setTheme(android.R.style.Theme_Material_NoActionBar)
        activity.setup()
    }
    @After fun teardown() {
        android.provider.Settings.Global.putFloat(activity.get().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        activity.pause().stop().destroy()
        NothingExperience.restore("{}") {}
        AppTheme.uiStyle = UiStyle.AUTO
        AppTheme.isDarkMode = true
        AppTheme.accent = AilaAccent.Default
        AppTheme.secondaryAccent = AilaAccent.VIOLET
        AppTheme.tertiaryAccent = AilaAccent.TEAL
    }

    @Test fun nothingHomeHasWorkingWidgetsInBothThemes() {
        var destination = ""
        activity.get().setContent {
            AilaTheme {
                CompositionLocalProvider(LocalProfileEntry provides AilaProfileEntry("SB") {}) {
                HomeScreen(studentFirstName = "Simone", onNavigateToCalendar = { destination = "calendar" },
                    onNavigateToCirculars = { destination = "circulars" })
                }
            }
        }
        compose.onNodeWithText("SIMONE", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("OGGI").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("calendar", destination) }
        compose.onNodeWithText("Circolari", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("circulars", destination) }
        compose.onNodeWithText("Apri radar").assertIsDisplayed()
        compose.onNodeWithText("Focus", useUnmergedTree = true).performClick()
        compose.onNodeWithText("25:00").assertIsDisplayed()
        compose.onNodeWithText("Avvia").performClick()
        compose.onNodeWithText("Pausa").assertIsDisplayed().performClick()
        compose.onNodeWithText("Riprendi").assertIsDisplayed()
        screenshot("nothing-home-light")
        compose.runOnIdle { AppTheme.isDarkMode = true }
        screenshot("nothing-home-dark")
    }

    @Test fun radarShowsRealCategoriesAndOpensTheSelectedEntry() {
        var opened = ""
        val entries = listOf(
            GlyphRadarEntry("e1", GlyphChannel.CALENDAR, "Fisica · energia", "Oggi alle 10:00", true) { opened = "e1" },
            GlyphRadarEntry("c1", GlyphChannel.CIRCULARS, "Assemblea d’istituto", "Apri circolare") { opened = "c1" },
            GlyphRadarEntry("p1", GlyphChannel.CLASS, "Sondaggio gita", "Vota") { opened = "p1" })
        activity.get().setContent { AilaTheme {
            Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight).padding(20.dp)) {
                NothingRadarWidget(GlyphRadarData(entries), Modifier.fillMaxSize())
            }
        } }
        compose.onNodeWithContentDescription("Apri Radar: 3 voci, 1 oggi").assertIsDisplayed()
        screenshot("nothing-radar-light")
        compose.runOnIdle { AppTheme.isDarkMode = true }
        screenshot("nothing-radar-dark")
        compose.onNodeWithText("Apri radar").performClick()
        compose.onNodeWithText("Sondaggio gita").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("p1", opened); assertEquals(0, NothingExperience.completedSessions) }
        compose.onNodeWithText("Apri radar").performClick()
        compose.onNodeWithText("Assemblea d’istituto").performScrollTo().assertIsDisplayed()
        screenshot("nothing-radar-inbox")
    }

    @Test fun briefActionsExpandDismissAndPlanWithTheActualEventContext() {
        val event = circolareplus.data.remote.dto.AilaBriefItemDto("e1", "r1", circolareplus.data.remote.dto.AilaBriefKind.EVENT,
            "physics", "Fisica · energia e quantità di moto", "", "Apri evento", date = "2026-11-18", canPlanStudy = true)
        val extras = (2..4).map { event.copy(id="e$it", sourceId="c$it", kind=circolareplus.data.remote.dto.AilaBriefKind.CIRCULAR,
            title="Circolare $it", actionLabel="Leggi circolare", canPlanStudy=false) }
        var dismissed = ""
        var prompt = ""
        var opened = ""
        activity.get().setContent { AilaTheme {
            Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp)) {
                AilaBriefCard(listOf(event)+extras, { dismissed=it.id }, { opened=it.id }, { prompt=it })
            }
        } }
        compose.onNodeWithText("Brief.").assertIsDisplayed()
        screenshot("nothing-brief-feed-light")
        compose.onNodeWithText("Apri evento").performScrollTo().performClick()
        compose.onNodeWithText("Pianifica studio").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("e1",opened); kotlin.test.assertTrue(prompt.contains(event.title)); kotlin.test.assertTrue(prompt.contains("2026-11-18")) }
        compose.onNodeWithText("Tutte le 4 voci").performScrollTo().performClick()
        compose.onNodeWithText("Circolare 4").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Non ora: Circolare 4").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("e4",dismissed); assertEquals(0,NothingExperience.completedSessions) }
        compose.runOnIdle { AppTheme.isDarkMode=true }
        compose.onNodeWithText("Brief.").performScrollTo()
        screenshot("nothing-brief-feed-dark")
    }

    @Test fun personalAccentsDoNotLeakIntoNothingAndAreRestoredOnExit() {
        AppTheme.accent = AilaAccent.GREEN
        AppTheme.secondaryAccent = AilaAccent.VIOLET
        AppTheme.tertiaryAccent = AilaAccent.TEAL
        val nothingRed = AppTheme.PrimaryBlue
        assertEquals(AppTheme.TextDark, AppTheme.SecondaryIndigo)
        assertEquals(AppTheme.TextMuted, AppTheme.AccentCyan)
        AppTheme.uiStyle = UiStyle.EXPRESSIVE
        assertEquals(androidx.compose.ui.graphics.Color(AilaAccent.GREEN.primaryLight), AppTheme.PrimaryBlue)
        AppTheme.uiStyle = UiStyle.NOTHING
        assertEquals(nothingRed, AppTheme.PrimaryBlue)
    }

    @Test fun nothingHeadersAndMatrixRespectReducedMotion() {
        android.provider.Settings.Global.putFloat(activity.get().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        activity.get().setContent {
            AilaTheme {
                Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight)) {
                    AilaScreenHeader("Calendario", "Le scadenze della classe")
                    Column(Modifier.padding(20.dp)) {
                        NothingGlyphWidget()
                        AilaSectionTitle("Prossimi eventi")
                        AilaProgressBar(.62f)
                    }
                }
            }
        }
        compose.onNodeWithText("Calendario").assertIsDisplayed()
        compose.onNodeWithText("Le scadenze della classe").assertIsDisplayed()
        screenshot("nothing-components-light")
        compose.runOnIdle { AppTheme.isDarkMode = true }
        screenshot("nothing-components-dark")
        android.provider.Settings.Global.putFloat(activity.get().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    @Test fun glyphStudioChangesAreSavedAndTimerCanBeReset() {
        var saved = ""
        NothingExperience.restore("{}") { saved = it }
        activity.get().setContent { AilaTheme { HomeScreen(studentFirstName = "Simone") } }
        compose.onNodeWithContentDescription("Personalizza Glyph").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Orbita").performScrollTo().performClick()
        compose.onNodeWithText("15 min").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("STUDIO")
        compose.runOnIdle {
            assertEquals(GlyphFace.ORBIT,NothingExperience.preferences.face)
            assertEquals(15,NothingExperience.preferences.focusMinutes)
            assertEquals("STUDIO",NothingExperience.preferences.signature)
            kotlin.test.assertTrue(saved.contains("ORBIT"))
        }
        screenshot("nothing-glyph-studio")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Prova").performClick()
        repeat(8) { frame ->
            compose.mainClock.advanceTimeBy(80)
            screenshot("nothing-glyph-motion-${frame.toString().padStart(2,'0')}",settle=false)
        }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Chiudi Glyph Studio").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Focus", useUnmergedTree = true).performClick()
        compose.onNodeWithText("15:00").assertIsDisplayed()
        compose.onNodeWithText("STUDIO").assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun longBriefTitlesAndColoredSubjectsRemainReadableWithLargerText() {
        val title = "Fisica · interrogazione su energia e quantità di moto"
        val item = circolareplus.data.remote.dto.AilaBriefItemDto("f1","r1",circolareplus.data.remote.dto.AilaBriefKind.EVENT,"e1",title,"","Apri",date="2026-11-18")
        activity.get().setContent {
            AilaTheme {
                val density = androidx.compose.ui.platform.LocalDensity.current
                CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density,1.3f)) {
                    Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight).padding(16.dp)) {
                        AilaBriefCard(listOf(item),{},{},{},subjectColorsEnabled=true)
                    }
                }
            }
        }
        compose.onNodeWithText(title).assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(title).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        compose.runOnIdle { kotlin.test.assertFalse(layouts.single().hasVisualOverflow) }
        screenshot("nothing-brief-large-text-light")
        compose.runOnIdle { AppTheme.isDarkMode = true }
        screenshot("nothing-brief-large-text-dark")
    }

    @Test fun nothingDialogAndInputCanBeReadAndConfirmed() {
        var confirmed = false
        activity.get().setContent {
            AilaTheme {
                AilaAlertDialog(onDismissRequest={},title={ androidx.compose.material3.Text("Conferma modifica") },
                    text={ androidx.compose.material3.Text("L’orario sarà aggiornato per tutta la classe.") },
                    confirmButton={ AilaTextButton({confirmed=true}) { androidx.compose.material3.Text("Conferma") } },
                    dismissButton={ AilaTextButton({}) { androidx.compose.material3.Text("Annulla") } })
            }
        }
        compose.onNodeWithText("Conferma modifica").assertIsDisplayed()
        screenshot("nothing-dialog-light")
        compose.runOnIdle { AppTheme.isDarkMode = true }
        screenshot("nothing-dialog-dark")
        compose.onNodeWithText("Conferma").performClick()
        compose.runOnIdle { kotlin.test.assertTrue(confirmed) }
    }

    @Test fun nothingCalendarAndTimetableKeepTheirSelectedDay() {
        val event = circolareplus.domain.model.CalendarEvent("physics","Fisica: energia e quantità di moto","2026-11-17",category=circolareplus.domain.model.CalendarEventCategory.INTERROGAZIONE)
        activity.get().setContent { AilaTheme { CalendarScreen(events=listOf(event),classLabel="4CSA",focusDateIso="2026-11-17") } }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText(event.title).assertIsDisplayed()
        screenshot("nothing-calendar-light")
        compose.runOnIdle { AppTheme.isDarkMode=true }
        screenshot("nothing-calendar-dark")
        compose.onNodeWithContentDescription("Apri orario della classe").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithContentDescription("Torna al calendario").assertIsDisplayed()
        screenshot("nothing-timetable-dark")
    }

    @Test fun nothingAssistantComposerCanSendALegibleDraft() {
        var sent = ""
        activity.get().setContent { AilaTheme {
            AssistantChatScreen(messages=listOf(circolareplus.ai.assistant.AssistantMessage("a1",circolareplus.ai.assistant.AssistantAuthor.ASSISTANT,"Rivediamo energia cinetica e quantità di moto.")),
                isThinking=false,conversations=emptyList(),onSend={sent=it},onBackClick={},onClearChat={},onOpenConversation={},onDeleteConversation={},onOpenSource={})
        } }
        compose.mainClock.advanceTimeBy(800)
        compose.onNode(hasSetTextAction()).performTextInput("Prepara un riepilogo di fisica")
        screenshot("nothing-assistant-light")
        compose.runOnIdle { AppTheme.isDarkMode=true }
        screenshot("nothing-assistant-dark")
        compose.onNodeWithContentDescription("Invia").performClick()
        compose.runOnIdle { assertEquals("Prepara un riepilogo di fisica",sent) }
    }

    @Test fun nothingOptionMenuShowsAndSavesTheSelectedLessonHour() {
        var chosen = -1
        activity.get().setContent { AilaTheme {
            EventFormOptionsSheet("Ora iniziale",(1..6).map { "${it}ª ora" },1,{}, { chosen=it })
        } }
        compose.onNodeWithText("2ª ora").assertIsSelected()
        screenshot("nothing-options-light")
        compose.runOnIdle { AppTheme.isDarkMode=true }
        screenshot("nothing-options-dark")
        compose.onNodeWithText("4ª ora").performClick()
        compose.runOnIdle { assertEquals(3,chosen) }
    }

    @Test fun nothingOriginMenuClosesOnceAndRestoresTheNavigationBar() {
        var closed=0
        var bar=true
        activity.get().setContent { AilaTheme {
            CompositionLocalProvider(LocalFloatingTabBarVisibility provides { bar=it }) {
                var opened by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
                var requested by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                if(opened) AilaOriginSheet("nothing-test",{closed++;opened=false},closeRequested=requested) {
                    Column(Modifier.padding(20.dp)) {
                        androidx.compose.material3.Text("Nuovo evento",style=androidx.compose.material3.MaterialTheme.typography.titleLarge)
                        AilaPrimaryButton("Salva",{requested=true})
                    }
                }
            }
        } }
        compose.runOnIdle { kotlin.test.assertFalse(bar) }
        screenshot("nothing-origin-menu")
        compose.onNodeWithText("Salva").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { assertEquals(1,closed);kotlin.test.assertTrue(bar) }
    }

    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Test fun nothingDateMenuRespectsDisabledDatesAndConfirmsSelection() {
        val initial=circolareplus.util.daysBetween(circolareplus.util.CivilDate(1970,1,1),circolareplus.util.CivilDate(2026,11,16))*86400000L
        var selected:Long?=null
        activity.get().setContent { AilaTheme {
            val state=androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis=initial,
                initialDisplayedMonthMillis=initial,yearRange=2026..2027,selectableDates=object:androidx.compose.material3.SelectableDates {
                    override fun isSelectableDate(utcTimeMillis:Long)=utcTimeMillis!=initial+86400000L
                })
            AilaDatePickerDialog(state,{},"Conferma data") { selected=state.selectedDateMillis }
        } }
        compose.onNodeWithContentDescription("17 Novembre 2026").assertIsNotEnabled()
        compose.onNodeWithContentDescription("18 Novembre 2026").performClick().assertIsSelected()
        screenshot("nothing-date-menu-light")
        compose.runOnIdle { AppTheme.isDarkMode=true }
        screenshot("nothing-date-menu-dark")
        compose.onNodeWithContentDescription("Scegli anno").performClick()
        compose.onNodeWithText("2027").assertIsDisplayed()
        screenshot("nothing-year-menu-dark")
        compose.onNodeWithText("2027").performClick()
        compose.onNodeWithText("Novembre 2027").assertIsDisplayed()
        compose.onNodeWithText("Conferma data").performClick()
        compose.runOnIdle { assertEquals(initial+2*86400000L,selected) }
    }

    @Test fun nothingOverflowMenuInvokesItsCommand() {
        var invoked=false
        activity.get().setContent { AilaTheme {
            androidx.compose.foundation.layout.Box {
                AilaDropdownMenu(true,{}) { AilaDropdownMenuItem(text={androidx.compose.material3.Text("Conserva proposta")},onClick={invoked=true}) }
            }
        } }
        compose.onNodeWithText("Conserva proposta").assertIsDisplayed().performClick()
        compose.runOnIdle { kotlin.test.assertTrue(invoked) }
    }

    private fun screenshot(name: String, settle: Boolean = true) {
        if (settle) compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle {
            // Compose dialogs have their own Android window; drawing only the Activity
            // would silently capture the dimmed background instead of the actual sheet.
            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
            val view = dialog?.window?.decorView ?: activity.get().window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File(System.getProperty("aila.ui.screenshots") ?: "build/ui-screenshots").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
