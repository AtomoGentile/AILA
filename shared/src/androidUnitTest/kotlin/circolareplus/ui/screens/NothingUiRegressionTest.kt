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
        activity = Robolectric.buildActivity(ComponentActivity::class.java)
        activity.get().setTheme(android.R.style.Theme_Material_NoActionBar)
        activity.setup()
    }
    @After fun teardown() {
        activity.pause().stop().destroy()
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
        compose.onNodeWithContentDescription("Glyph AILA", useUnmergedTree = true).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasAnyDescendant(hasContentDescription("Glyph AILA")), useUnmergedTree = true)
            .performClick()
        compose.mainClock.advanceTimeBy(1800)
        compose.onNodeWithText("HELLO, NOTHING.").assertIsDisplayed()
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
