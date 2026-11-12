package circolareplus.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import circolareplus.design.AilaTheme
import circolareplus.design.AppTheme
import circolareplus.design.UiStyle
import circolareplus.util.today
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h850dp-xxhdpi", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarUiRegressionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>

    @Before fun setup() {
        AppTheme.uiStyle = UiStyle.EXPRESSIVE
        AppTheme.isDarkMode = true
        AppTheme.reduceMotion = false
        activity = Robolectric.buildActivity(ComponentActivity::class.java)
        activity.get().setTheme(android.R.style.Theme_Material_NoActionBar)
        activity.setup()
        activity.get().window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    @After fun teardown() {
        android.provider.Settings.Global.putFloat(activity.get().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        activity.pause().stop().destroy()
        AppTheme.reduceMotion = false
        AppTheme.isDarkMode = true
        AppTheme.uiStyle = UiStyle.AUTO
    }

    private fun calendar(date: String = "2026-11-11") {
        activity.get().setContent {
            AilaTheme { CalendarScreen(events = emptyList(), classLabel = "4CSA", focusDateIso = date) }
        }
        compose.mainClock.advanceTimeBy(1000)
    }

    private fun openTimetable() {
        compose.onNodeWithContentDescription("Apri orario della classe").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithContentDescription("Torna al calendario").assertIsDisplayed()
    }

    @Test fun timetableKeepsDateAndNavigatesAcrossMonthBoundary() {
        calendar("2026-11-30")
        openTimetable()
        compose.onNodeWithText("30", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Giorno successivo").performClick()
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Torna al calendario").performClick()
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText("Dicembre 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Mese successivo").performClick()
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText("Gennaio 2027").assertIsDisplayed()
    }

    @Test fun morphFollowsDragThenCancelsBelowThreshold() {
        calendar()
        compose.onNodeWithContentDescription("Apri orario della classe").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, height * 0.6f), 700)
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText("Novembre 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Apri orario della classe").assertIsDisplayed()
        compose.onNodeWithText("Apri orario ufficiale").assertDoesNotExist()
    }

    @Test fun fullDragAndInverseDragUseSameSurface() {
        calendar()
        compose.onNodeWithContentDescription("Apri orario della classe").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, height * 4.5f), 700)
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithContentDescription("Torna al calendario").assertIsDisplayed()
        screenshot("calendar-morph-timetable")
        compose.onNodeWithContentDescription("Torna al calendario").performTouchInput {
            swipe(center, center - androidx.compose.ui.geometry.Offset(0f, height * 3.1f), 700)
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithContentDescription("Apri orario della classe").assertIsDisplayed()
        compose.onNodeWithText("11", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun sundayReducedMotionLightModeAndRepeatedTransitions() {
        android.provider.Settings.Global.putFloat(activity.get().contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        AppTheme.isDarkMode = false
        calendar("2026-11-15")
        repeat(3) {
            openTimetable()
            compose.onNodeWithText("Nessuna lezione prevista per questa data").assertIsDisplayed()
            compose.onNodeWithContentDescription("Torna al calendario").performClick()
        }
        openTimetable()
        compose.onNodeWithText("Oggi").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("${today().day}", useUnmergedTree = true).assertIsDisplayed()
        screenshot("calendar-morph-light-reduced-motion")
    }

    @Test fun fastReleaseCanCompleteBeforeDistanceThreshold() {
        calendar()
        compose.onNodeWithContentDescription("Apri orario della classe").performTouchInput {
            swipe(center, center + androidx.compose.ui.geometry.Offset(0f, height * 1.5f), 80)
        }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithContentDescription("Torna al calendario").assertIsDisplayed()
    }

    @Test fun scrollingEventsDoesNotOpenTimetable() {
        calendar()
        compose.onNodeWithText("Nessun evento").performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithContentDescription("Torna al calendario").assertDoesNotExist()
    }

    @Test fun interruptedGestureReturnsToOriginAndMovesBeforeRelease() {
        calendar()
        val before = compose.onNodeWithText("11", useUnmergedTree = true).getUnclippedBoundsInRoot().top
        compose.onNodeWithContentDescription("Apri orario della classe").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, height * 2.8f), 300)
        }
        compose.mainClock.advanceTimeByFrame()
        val during = compose.onNodeWithText("11", useUnmergedTree = true).getUnclippedBoundsInRoot().top
        assertTrue(during < before, "The selected day must move while the pointer is still down")
        screenshot("calendar-morph-interactive", settle = false)
        compose.onNodeWithContentDescription("Apri orario della classe").performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText("Novembre 2026").assertIsDisplayed()
    }

    @Test fun returningFromTimetablePreservesEventClicks() {
        var clicked: String? = null
        val event = circolareplus.domain.model.CalendarEvent("test", "Evento di prova", "2026-11-11",
            category = circolareplus.domain.model.CalendarEventCategory.VERIFICA)
        activity.get().setContent {
            AilaTheme { CalendarScreen(events = listOf(event), classLabel = "4CSA", focusDateIso = event.date,
                onEventClick = { clicked = it.id }) }
        }
        compose.mainClock.advanceTimeBy(1000)
        openTimetable()
        compose.onNodeWithContentDescription("Torna al calendario").performClick()
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText(event.title).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(event.id, clicked) }
    }

    @Test @Config(qualifiers = "w1000dp-h850dp-xhdpi")
    fun tabletMorphExpandsAcrossEventPane() {
        activity.get().setContent {
            androidx.compose.runtime.CompositionLocalProvider(circolareplus.design.LocalWideLayout provides true) {
                AilaTheme { CalendarScreen(events = emptyList(), classLabel = "4CSA", focusDateIso = "2026-11-11") }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        openTimetable()
        compose.onNodeWithContentDescription("Giorno successivo").performClick()
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText("12", useUnmergedTree = true).assertIsDisplayed()
        screenshot("calendar-morph-tablet")
    }

    @Test fun recreatedCompositionRestoresModeAndSelectedDate() {
        val visible = androidx.compose.runtime.mutableStateOf(true)
        val focus = androidx.compose.runtime.mutableStateOf<String?>("2026-11-11")
        var registry = androidx.compose.runtime.saveable.SaveableStateRegistry(null) { true }
        activity.get().setContent {
            AilaTheme {
                if (visible.value) androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.runtime.saveable.LocalSaveableStateRegistry provides registry
                ) {
                    CalendarScreen(events = emptyList(), classLabel = "4CSA", focusDateIso = focus.value,
                        onFocusConsumed = { focus.value = null })
                }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        openTimetable()
        compose.onNodeWithContentDescription("Giorno successivo").performClick()
        compose.mainClock.advanceTimeBy(1200)
        var saved: Map<String, List<Any?>> = emptyMap()
        compose.runOnIdle {
            saved = registry.performSave()
            visible.value = false
        }
        compose.waitForIdle()
        compose.runOnIdle {
            registry = androidx.compose.runtime.saveable.SaveableStateRegistry(saved) { true }
            visible.value = true
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithContentDescription("Torna al calendario").assertIsDisplayed()
        compose.onNodeWithText("12", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun glassSurfaceSupportsTheSameRoundTrip() {
        AppTheme.uiStyle = UiStyle.GLASS
        calendar()
        openTimetable()
        screenshot("calendar-morph-glass")
        compose.onNodeWithContentDescription("Torna al calendario").performClick()
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText("Novembre 2026").assertIsDisplayed()
        screenshot("calendar-morph-glass-restored")
    }

    @Test fun systemBackReturnsToCalendarBeforeLeavingSection() {
        calendar()
        openTimetable()
        compose.runOnIdle { activity.get().onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithContentDescription("Apri orario della classe").assertIsDisplayed()
    }

    @Test fun eventFormScrollsToRecipientsAndKeepsSubmitAccessible() {
        var createdTitle: String? = null
        var createdCategory: circolareplus.domain.model.CalendarEventCategory? = null
        activity.get().setContent {
            AilaTheme {
                AddCalendarEventDialog(
                    onDismiss = {},
                    onConfirm = { title, _, _, _, _, category, _, _, _ -> createdTitle = title; createdCategory = category },
                    initialStep = EventCreationStep.MANUAL,
                    initialDateIso = today().toIso(), classLabel = "4CSA"
                )
            }
        }
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithContentDescription("Titolo").performTextInput("Lezione")
        compose.onNodeWithText("Dettagli evento").assertIsDisplayed()
        compose.onNodeWithText("Aggiungi evento").assertIsDisplayed()
        screenshot("event-form-top")
        compose.onAllNodesWithText("Non impostata")[0].performScrollTo().performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Ora iniziale").assertIsDisplayed()
        compose.onNodeWithText("3ª ora").performTouchInput { click() }
        compose.onNodeWithText("Ora iniziale").assertDoesNotExist()
        compose.onNodeWithText("Non impostata").performScrollTo().performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Ora finale").assertIsDisplayed()
        compose.onNodeWithText("1ª ora").assertDoesNotExist()
        screenshot("event-end-hour-picker")
        compose.onNodeWithText("4ª ora").performTouchInput { click() }
        compose.onNodeWithText("Ora finale").assertDoesNotExist()
        compose.onNodeWithText("3ª ora").assertIsDisplayed()
        compose.onNodeWithText("4ª ora").assertIsDisplayed()
        compose.onNodeWithText("Verifica").performScrollTo().performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Altro").assertIsDisplayed()
        screenshot("event-type-picker")
        compose.onNodeWithText("Interrogazione").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithText("Un solo giorno").performScrollTo().performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        screenshot("event-duration-picker")
        compose.onNodeWithText("Più giorni consecutivi").assertIsDisplayed().performTouchInput { click() }
        compose.onNodeWithText("Giorni consecutivi fino al").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Aggiungi evento").assertIsNotEnabled()
        compose.onNodeWithText("Più giorni consecutivi").performScrollTo().performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Un solo giorno").performTouchInput { click() }
        compose.onNodeWithText("Giorni consecutivi fino al").assertDoesNotExist()
        compose.onNodeWithText("Destinatari").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tutta la classe").performScrollTo().assertIsDisplayed()
        screenshot("event-form-bottom")
        compose.onNodeWithText("Aggiungi evento").assertIsEnabled().performTouchInput { click() }
        compose.runOnIdle {
            assertEquals("Lezione", createdTitle)
            assertEquals(circolareplus.domain.model.CalendarEventCategory.INTERROGAZIONE, createdCategory)
        }
    }

    @Test fun assistantComposerRemainsEditableAndSendsTheDraft() {
        var sent: String? = null
        activity.get().setContent {
            AilaTheme {
                AssistantChatScreen(
                    messages = listOf(circolareplus.ai.assistant.AssistantMessage("a", circolareplus.ai.assistant.AssistantAuthor.ASSISTANT, "Pronto per la domanda.")),
                    isThinking = false, conversations = emptyList(),
                    onSend = { sent = it }, onBackClick = {}, onClearChat = {},
                    onOpenConversation = {}, onDeleteConversation = {}, onOpenSource = {}
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Confronta i due calendari")
        screenshot("assistant-composer-focused")
        compose.onNodeWithContentDescription("Invia").assertIsEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals("Confronta i due calendari", sent) }
    }

    private fun screenshot(name: String, settle: Boolean = true) {
        val directory = File(System.getProperty("aila.ui.screenshots") ?: "build/ui-screenshots").apply { mkdirs() }
        if (settle) compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        // PixelCopy waits for a real window frame; Robolectric renders synchronously to Canvas.
        compose.runOnIdle {
            val view = android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull()
                ?: activity.get().window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(directory, "$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
