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
        activity = Robolectric.buildActivity(ComponentActivity::class.java)
        activity.get().setTheme(android.R.style.Theme_Material_NoActionBar)
        activity.setup()
    }

    @After fun teardown() {
        activity.pause().stop().destroy()
        AppTheme.uiStyle = UiStyle.AUTO
    }

    @Test fun timetableOpensAboveCalendarAndAcceptsTouches() {
        activity.get().setContent { AilaTheme { CalendarScreen(events = emptyList(), classLabel = "4CSA") } }
        compose.onNodeWithText("Lezioni del giorno").performScrollTo().performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithText("Gio").performTouchInput { click() }
        compose.onNodeWithContentDescription("Filosofia, 4ª ora").assertIsDisplayed()
        screenshot("timetable-thursday")
        compose.onNodeWithText("Dom").performScrollTo().performTouchInput { click() }
        compose.onNodeWithText("Nessuna lezione prevista").assertIsDisplayed()
        compose.onNodeWithContentDescription("Chiudi orario").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(1500)
        compose.onNodeWithText("Apri orario ufficiale").assertDoesNotExist()
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
        compose.onNodeWithText("Verifica").performScrollTo().performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
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

    private fun screenshot(name: String) {
        val directory = File(System.getProperty("aila.ui.screenshots") ?: "build/ui-screenshots").apply { mkdirs() }
        compose.mainClock.advanceTimeBy(500)
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
