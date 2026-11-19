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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import circolareplus.design.*
import circolareplus.util.plusDays
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
                HomeScreen(studentFirstName = "Simone", calendarEvents = listOf(circolareplus.domain.model.CalendarEvent(
                    "today", "Fisica · energia", circolareplus.util.today().toIso(),
                    category=circolareplus.domain.model.CalendarEventCategory.INTERROGAZIONE)), onNavigateToCalendar = { destination = "calendar" },
                    onNavigateToCirculars = { destination = "circulars" })
                }
            }
        }
        compose.onNodeWithText("Ciao, Simone", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("OGGI").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("calendar", destination) }
        compose.onNodeWithText("Circolari", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("circulars", destination) }
        compose.onAllNodesWithText("Fisica · energia").assertCountEquals(1)
        compose.onNodeWithContentDescription("Anteprima Glyph Bar a 6 segmenti").assertDoesNotExist()
        screenshot("nothing-home-light")
        compose.runOnIdle {AppTheme.isDarkMode=true}
        screenshot("nothing-home-dark")
        compose.onNodeWithText("Focus").assertDoesNotExist()
        compose.onNodeWithText("Incastri in agenda", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNode(hasText("Nessun incastro nei prossimi 7 giorni") and hasAnyAncestor(isDialog()))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Chiudi incastri").performClick()
    }

    @Test fun publishedMapPrioritizesTheClassroomAndKeepsNamesAndManagementAccessible() {
        val students=(0 until 22).map {circolareplus.domain.model.User(id="s$it",firstName="Nome$it",lastName="Cognome completo $it",username="s$it")}.associateBy {it.id}
        val desks=(0 until 11).map {circolareplus.algorithms.DeskAssignment(it/3,it%3,"s${it*2}","s${it*2+1}")}
        activity.get().setContent { AilaTheme {
            SeatMapScreen("s20",true,desks,students,false)
        } }
        compose.onNodeWithText("Preferenze sociali").assertDoesNotExist()
        compose.onNodeWithText("Leggi nomi").assertDoesNotExist()
        screenshot("nothing-seat-map-light")
        compose.runOnIdle {AppTheme.isDarkMode=true}
        screenshot("nothing-seat-map-dark")
        compose.onNodeWithText("Il mio posto").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Dettaglio banco F4C2").assertIsDisplayed().performClick()
        compose.onNodeWithText("Nome20 Cognome completo 20").assertIsDisplayed()
        screenshot("nothing-seat-detail")
        compose.onNodeWithContentDescription("Chiudi dettaglio banco").performClick()
        compose.onNodeWithText("Gestione").performClick()
        compose.onNodeWithText("Preferenze sociali").assertExists()
        screenshot("nothing-seat-management")
        compose.onNodeWithContentDescription("Chiudi gestione aula").performClick()
        compose.onNodeWithText("Preferenze sociali").assertDoesNotExist()
        compose.onNodeWithText("Gestione").assertIsDisplayed()
    }

    @Test fun representativeSeatManagementOpensInASheetInTheOtherStyles() {
        var open by androidx.compose.runtime.mutableStateOf(false)
        activity.get().setContent { AilaTheme {
            SeatMapScreen("student", true, emptyList(), emptyMap(), open,
                onTogglePreferencesWindow = { open = it })
        } }
        for (style in listOf(UiStyle.EXPRESSIVE, UiStyle.GLASS)) {
            compose.runOnIdle {
                AppTheme.uiStyle = style
                open = false
            }
            compose.onNodeWithText("Apri votazione").assertDoesNotExist()
            compose.onNodeWithText("Gestione", useUnmergedTree = true).performScrollTo().performClick()
            compose.onNodeWithText("Gestione aula").assertIsDisplayed()
            compose.onNodeWithText("Apri votazione").performClick()
            compose.runOnIdle { kotlin.test.assertTrue(open) }
            compose.onNodeWithContentDescription("Chiudi gestione aula").performClick()
            compose.onNodeWithText("Preferenze sociali").assertDoesNotExist()
        }
    }

    @Test fun generatedSeatMapAppearsAfterManagementCloses() {
        var generating by androidx.compose.runtime.mutableStateOf(false)
        var ready by androidx.compose.runtime.mutableStateOf(false)
        var revealed by androidx.compose.runtime.mutableStateOf(false)
        activity.get().setContent { AilaTheme {
            if (revealed) androidx.compose.material3.Text("Mappa generata")
            else SeatMapScreen("student", true, emptyList(), emptyMap(), false,
                isGeneratingProposals = generating,
                generatedProposalsReady = ready,
                onShowGeneratedProposals = { revealed = true })
        } }
        compose.onNodeWithText("Gestione").performClick()
        compose.runOnIdle { generating = true }
        compose.onNodeWithText("Gestione aula").assertIsDisplayed()
        compose.onNodeWithText("Mappa generata").assertDoesNotExist()
        compose.runOnIdle {
            generating = false
            ready = true
        }
        compose.onNodeWithText("Gestione aula").assertDoesNotExist()
        compose.onNodeWithText("Mappa generata").assertIsDisplayed()
    }

    @Test fun chatShowsPhotoAlongsideFileAndVoice() {
        activity.get().setContent { AilaTheme {
            AssistantChatScreen(
                messages = emptyList(), isThinking = false, conversations = emptyList(),
                onSend = {}, onBackClick = {}, onClearChat = {},
                onOpenConversation = {}, onDeleteConversation = {}, onOpenSource = {},
                onAttachContent = {}
            )
        } }
        compose.onNodeWithText("ACA").assertDoesNotExist()
        compose.onNodeWithContentDescription("Menu allegati").performClick()
        val titles = listOf("File", "Foto", "Vocale").map { title ->
            compose.onNodeWithText(title, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertEquals(titles.first().top, titles[1].top)
        assertEquals(titles.first().top, titles[2].top)
    }

    @Test fun pollRequiresEveryDateAndLocksVotingWhenClosed() {
        var slots by androidx.compose.runtime.mutableStateOf(listOf(SlotUiItem("a","2026-11-18","Fisica",4),SlotUiItem("b","2026-11-19","Fisica",4)))
        var sent=0
        var expired by androidx.compose.runtime.mutableStateOf(false)
        activity.get().setContent { AilaTheme {
            Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight)) {
                PollNavigation(0,false,{},{},null)
                PollsScreen("Fisica",slots,0,isExpired=expired,onCastVote={id,v->slots=slots.map {if(it.slotId==id)it.copy(currentVote=v)else it}},onSubmit={sent++})
            }
        } }
        screenshot("nothing-poll-flow-light")
        compose.runOnIdle {AppTheme.isDarkMode=true}
        screenshot("nothing-poll-flow-dark")
        compose.onNodeWithText("Come votare").performClick()
        compose.onNodeWithText("Ho capito").performClick()
        val dates = compose.onNode(hasScrollToIndexAction())
        val yes = hasText("Ci sto") and hasClickAction()
        fun scrollToSubmit(): SemanticsNodeInteraction {
            dates.performScrollToNode(hasText("Invia le mie scelte"))
            return compose.onNodeWithText("Invia le mie scelte")
        }
        scrollToSubmit().assertIsNotEnabled()
        dates.performScrollToKey("a")
        compose.onAllNodes(yes).onFirst().performScrollTo().performClick()
        scrollToSubmit().assertIsNotEnabled()
        dates.performScrollToKey("b")
        compose.onNode(yes and isNotSelected()).performScrollTo().performClick()
        scrollToSubmit().assertIsEnabled().performClick()
        compose.runOnIdle {assertEquals(1,sent);expired=true}
        compose.onNodeWithText("Invia le mie scelte").assertDoesNotExist()
        dates.performScrollToKey("a")
        compose.onAllNodes(yes).onFirst().performScrollTo().assertIsNotEnabled()
    }

    @Test fun homeDestinationsRemainAccessibleWithLargeText() {
        var destination=""
        activity.get().setContent { AilaTheme {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(3f,1.3f)) {
                HomeScreen(studentFirstName="Alessandro",onNavigateToCirculars={destination="Circolari"},onNavigateToCalendar={destination="Calendario"},
                    onNavigateToBoard={destination="Bacheca"},onOpenForum={destination="Pere"},onNavigateToPolls={destination="Sondaggi"},onNavigateToSeatMap={destination="Posti"})
            }
        } }
        listOf("Circolari","Calendario","Bacheca","Pere","Sondaggi","Posti").forEach {label->
            compose.onNodeWithText(label,useUnmergedTree=true).performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle {assertEquals(label,destination)}
        }
        compose.onNodeWithText("Ciao, Alessandro").performScrollTo()
        screenshot("nothing-home-large-text")
    }

    @Test fun rankingPollKeepsReorderingSubmissionAndHistoryNavigationWorking() {
        var history by androidx.compose.runtime.mutableStateOf(false)
        var section by androidx.compose.runtime.mutableStateOf(1)
        var order=listOf<String>()
        val poll=circolareplus.data.remote.dto.RankingPollDto("r1","Dove andiamo in gita?",false,"2026-11-17",listOf(
            circolareplus.data.remote.dto.RankingPollOptionDto("a","Museo della scienza"),
            circolareplus.data.remote.dto.RankingPollOptionDto("b","Osservatorio astronomico"),
            circolareplus.data.remote.dto.RankingPollOptionDto("c","Teatro")))
        activity.get().setContent { AilaTheme {
            Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight)) {
                PollNavigation(section,history,{section=it},{history=it},null)
                RankingPollsScreen(listOf(poll),totalStudents=22,isRepresentative=true,submittingPollId=null,
                    onSubmitRanking={_,ids->order=ids},onClosePoll={},onDeletePoll={},onCreatePoll={})
            }
        } }
        compose.onAllNodesWithContentDescription("Sposta giù")[0].performClick()
        screenshot("nothing-ranking-light")
        compose.onNodeWithText("Invia la mia classifica").performScrollTo().performClick()
        compose.runOnIdle {assertEquals(listOf("b","a","c"),order);AppTheme.isDarkMode=true}
        screenshot("nothing-ranking-dark")
        compose.onNodeWithContentDescription("Apri storico sondaggi").performClick()
        compose.onNodeWithText("Storico sondaggi").assertIsDisplayed()
        compose.onNodeWithContentDescription("Torna ai sondaggi in corso").performClick()
        compose.runOnIdle {kotlin.test.assertFalse(history)}
        compose.onNodeWithText("Interrogazioni").performClick()
        compose.runOnIdle {assertEquals(0,section)}
    }

    @Test fun allStylesOpenPollHistoryFromTheHeaderAndKeepTheSelectedCategory() {
        var history by androidx.compose.runtime.mutableStateOf(false)
        var section by androidx.compose.runtime.mutableStateOf(0)
        activity.get().setContent {
            AilaTheme {
                Column {
                    PollNavigation(section, history, { section = it }, { history = it }, null)
                }
            }
        }

        for (style in listOf(UiStyle.NOTHING, UiStyle.EXPRESSIVE, UiStyle.GLASS)) {
            compose.runOnIdle {
                AppTheme.uiStyle = style
                section = 0
            }
            compose.onNodeWithText("In corso").assertDoesNotExist()
            compose.onNodeWithText("Come si vota").assertIsDisplayed()
            compose.onNodeWithText("Indica in quali date puoi essere interrogato.").assertIsDisplayed()
            compose.onNodeWithContentDescription("Apri storico sondaggi").performClick()
            compose.onNodeWithText("Storico sondaggi").assertIsDisplayed()
            compose.onNodeWithText("Ordinamento").performClick()
            compose.runOnIdle { assertEquals(1, section) }
            compose.onNodeWithContentDescription("Torna ai sondaggi in corso").performClick()
            compose.onNodeWithText("Sondaggi").assertIsDisplayed()
            compose.onNodeWithText("Metti le opzioni in ordine, dalla tua preferita all’ultima.").assertIsDisplayed()
            compose.runOnIdle { assertEquals(1, section) }
        }
    }

    @Test fun homeBriefUsesOneOverviewAndRetainsTheNextEventWhenTheFeedIsEmpty() {
        val event=circolareplus.domain.model.CalendarEvent("e1","Fisica · energia",circolareplus.util.today().toIso(),category=circolareplus.domain.model.CalendarEventCategory.INTERROGAZIONE)
        val item=circolareplus.data.remote.dto.AilaBriefItemDto("e1","r1",circolareplus.data.remote.dto.AilaBriefKind.EVENT,"e1",event.title,"","Apri evento",date=event.date,canPlanStudy=true)
        var feed by androidx.compose.runtime.mutableStateOf(listOf(item))
        var opened=""
        var planned=""
        activity.get().setContent { AilaTheme {
            NothingHomeScreen("Simone",listOf(event),null,true,feed,false,null,{}, {opened=it.id},{feed=emptyList()}, {planned=it},false,emptyMap(),
                NothingHomeActions({},{},{},{},{},{},{},{},{opened=it.id},{}),false,false)
        } }
        compose.onAllNodesWithText(event.title).assertCountEquals(1)
        screenshot("nothing-home-brief")
        compose.onNodeWithText("Apri Brief").performClick()
        compose.onNodeWithText("Pianifica studio").performScrollTo().performClick()
        compose.runOnIdle {kotlin.test.assertTrue(planned.contains(event.title))}
        compose.onNodeWithContentDescription("Non ora: ${event.title}").performClick()
        compose.onAllNodesWithText(event.title).assertCountEquals(1)
        compose.onNodeWithText("PROSSIMO IMPEGNO").assertIsDisplayed()
        compose.onNodeWithText(event.title).performClick()
        compose.runOnIdle {assertEquals(event.id,opened)}
    }

    @Test fun calendarGlyphCountsDatesAndOpensTheSelectedDay() {
        var opened = ""
        val date = circolareplus.util.today().plusDays(1).toIso()
        val conflict = testConflict("first", date)
        activity.get().setContent { AilaTheme {
            CalendarConflictGlyph(
                listOf(conflict, testConflict("other", date)),
                loading = false, unavailable = false, homeVisible = true,
                onOpenEvent = { opened = it.date }, onOpenCalendar = {}
            )
        } }
        compose.onNodeWithText("01").assertIsDisplayed()
        screenshot("nothing-calendar-glyph-light")
        compose.runOnIdle { AppTheme.isDarkMode = true }
        compose.onNodeWithText("Incastri in agenda", useUnmergedTree = true).performClick()
        compose.onAllNodesWithText("Sovrapposizione di orario").assertCountEquals(2)
        screenshot("nothing-calendar-glyph-detail")
        compose.onNodeWithText("Verifica first · Assemblea", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(date, opened) }
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
        compose.runOnIdle { assertEquals("e4",dismissed) }
        compose.runOnIdle { AppTheme.isDarkMode=true }
        compose.onNodeWithText("Brief.").performScrollTo()
        screenshot("nothing-brief-feed-dark")
    }

    @Test fun glyphSignalsOnlyNewConfirmedOverlapsAndStopsThePhysicalCue() {
        var pulses = 0
        var stops = 0
        val hardware = object : circolareplus.platform.NothingGlyphConnection {
            override val ownerLabel = "Nothing test"
            override val ready = true
            override val status = "Test"
            override fun pulse(zone: Int, periodMillis: Int): Boolean { pulses++; return true }
            override fun stop() { stops++ }
        }
        val date = circolareplus.util.today().plusDays(1).toIso()
        var conflicts by androidx.compose.runtime.mutableStateOf(listOf(testConflict("initial", date)))
        NothingExperience.configure(NothingPreferences(conflictLights = true))
        activity.get().setContent { AilaTheme {
            CompositionLocalProvider(circolareplus.platform.LocalNothingGlyph provides hardware) {
                CalendarConflictGlyph(conflicts, false, false, true, {}, {})
            }
        } }
        compose.runOnIdle { assertEquals(0, pulses) }
        compose.runOnIdle {
            conflicts = conflicts + testConflict("possible", date).copy(kind = CalendarConflictKind.POSSIBLE)
        }
        compose.runOnIdle { assertEquals(0, pulses) }
        compose.runOnIdle { conflicts = conflicts + testConflict("new", date) }
        compose.runOnIdle { assertEquals(1, pulses) }
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { kotlin.test.assertTrue(stops > 0) }
    }

    private fun testConflict(id: String, date: String): CalendarConflict {
        val first = circolareplus.domain.model.CalendarEvent(
            id, "Verifica $id", date, "09:00", circolareplus.domain.model.CalendarEventCategory.VERIFICA
        )
        val second = first.copy(id = "$id-other", title = "Assemblea")
        return CalendarConflict(date, first, second, CalendarConflictKind.OVERLAP)
    }

    @Test fun nothingCircularsHaveARealListAndKeepFilteringAndDocumentNavigation() {
        val circ=circolareplus.domain.model.Circular(143,"Assemblea d’istituto e organizzazione delle attività","17.11.2026","143.pdf")
        val classification=circolareplus.domain.model.CircularAiClassification(143,circolareplus.domain.model.CircularRelevanceBadge.RELEVANT,"L’assemblea riguarda tutte le classi. Leggi le indicazioni per partecipare.")
        var opened=0
        activity.get().setContent { AilaTheme {
            CircularsScreen(listOf(circ),mapOf(143 to classification),{opened=it.number},aiEnabled=true)
        } }
        compose.onNodeWithText(circ.title).assertIsDisplayed()
        screenshot("nothing-circulars-light")
        compose.runOnIdle { AppTheme.isDarkMode=true }
        screenshot("nothing-circulars-dark")
        compose.onNodeWithText("Altre").performClick()
        compose.onNodeWithText("Nessun risultato").assertIsDisplayed()
        compose.onNodeWithText("Per te").performClick()
        compose.onNodeWithText("Apri documento").performClick()
        compose.runOnIdle { assertEquals(143,opened) }
    }

    @Test fun nothingBoardPereAndPollUseTheSameSurfaceAndControlLanguage() {
        var scene by androidx.compose.runtime.mutableStateOf(0)
        var created=false
        var vote:circolareplus.algorithms.InterrogationVoteType?=null
        val proposal=circolareplus.domain.model.Proposal("p1",authorName="Anonimo",isAnonymous=true,title="Spazio per lo studio in classe",description="Una proposta per organizzare meglio i materiali condivisi.",category="Didattica",createdAt="2026-11-17")
        activity.get().setContent { AilaTheme {
            when(scene) {
                0 -> BoardScreen(listOf(proposal),onCreateProposalClick={created=true})
                1 -> Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight).verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(20.dp)) {
                    ForumIntroduction(false,{created=true},{})
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                    ForumEmptyState()
                }
                else -> PollsScreen("Fisica",listOf(SlotUiItem("s1","2026-11-18","Fisica",4)),0,onCastVote={_,v->vote=v})
            }
        } }
        compose.onNodeWithText(proposal.title).assertIsDisplayed()
        screenshot("nothing-board-light")
        compose.onNodeWithText("Nuova proposta").performClick()
        compose.runOnIdle { kotlin.test.assertTrue(created);scene=1 }
        compose.onNodeWithText("Revisione tra pere").assertIsDisplayed()
        screenshot("nothing-pere-light")
        compose.runOnIdle { AppTheme.isDarkMode=true }
        screenshot("nothing-pere-dark")
        compose.runOnIdle { scene=2 }
        compose.onNodeWithText("Scegli le date che vanno bene per te.").assertIsDisplayed()
        screenshot("nothing-poll-dark")
        compose.runOnIdle { AppTheme.isDarkMode=false }
        screenshot("nothing-poll-light")
        compose.onNode(hasText("Ci sto") and hasClickAction()).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(circolareplus.algorithms.InterrogationVoteType.GREEN,vote) }
    }

    @Test fun glyphSettingsOpenTheRealNotificationCategory() {
        var configured: NothingNotificationGroup? = null
        val hardware = object : circolareplus.platform.NothingGlyphConnection {
            override val ownerLabel = "Phone (4a)"
            override val ready = true
            override val status = "Connessione di test"
            override val notificationSettingsAvailable = true
            override fun pulse(zone: Int, periodMillis: Int) = true
            override fun stop() = Unit
            override fun openNotificationSettings(group: NothingNotificationGroup): Boolean {
                configured = group
                return true
            }
        }
        NothingExperience.configure(NothingPreferences(systemNotifications = true))
        activity.get().setContent { AilaTheme {
            CompositionLocalProvider(circolareplus.platform.LocalNothingGlyph provides hardware) {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    NothingGlyphNotificationSettings()
                }
            }
        } }
        compose.onNodeWithText("Configura Circolari").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(NothingNotificationGroup.CIRCULARS, configured) }
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
                        NothingMatrixLoader(Modifier.size(48.dp))
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
