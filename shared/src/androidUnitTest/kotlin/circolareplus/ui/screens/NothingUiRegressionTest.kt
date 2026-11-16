package circolareplus.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
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
        compose.onNodeWithText("25:00").assertIsDisplayed()
        compose.onNodeWithText("Avvia").performClick()
        compose.onNodeWithText("Pausa").assertIsDisplayed().performClick()
        compose.onNodeWithText("Riprendi").assertIsDisplayed()
        screenshot("nothing-home-light")
        compose.runOnIdle { AppTheme.isDarkMode = true }
        screenshot("nothing-home-dark")
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
        compose.onNodeWithContentDescription("Chiudi Glyph Studio").performClick()
        compose.mainClock.advanceTimeBy(400)
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

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle {
            val view = activity.get().window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val folder = File(System.getProperty("aila.ui.screenshots") ?: "build/ui-screenshots").apply { mkdirs() }
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
