package circolareplus.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import circolareplus.algorithms.*
import circolareplus.design.*
import circolareplus.domain.model.User
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h850dp-xxhdpi", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SeatMapProposalsUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun expressiveMapIsVisibleImmediatelyAndSelectionOpensTheRightProposal() = verify(UiStyle.EXPRESSIVE)
    @Test fun glassMapIsVisibleImmediatelyAndSelectionOpensTheRightProposal() = verify(UiStyle.GLASS)

    private fun verify(style: UiStyle) {
        AppTheme.uiStyle = style
        AppTheme.isDarkMode = true
        AppTheme.reduceMotion = true
        val activity = Robolectric.buildActivity(ComponentActivity::class.java)
        activity.get().setTheme(android.R.style.Theme_Material_NoActionBar)
        activity.setup()
        try {
            val students = (0 until 22).map { User(id = "s$it", firstName = "Nome$it", lastName = "", username = "s$it") }
            var next = 0
            val layout = (0 until 4).flatMap { row -> (0 until 3).map { col ->
                if (row == 3 && col == 1) DeskAssignment(row, col, null, null, seats = 0)
                else DeskAssignment(row, col, "s${next++}", "s${next++}")
            } }
            val proposals = (1..3).map { i -> SeatMapProposal(
                id = "p$i", assignments = layout, totalScore = (100 - i).toDouble(),
                satisfaction = SeatMapOptimizer.VoteSatisfaction(80.0, 22, 0), socialScore = 40.0,
                disciplinePenalty = 0.0, didacticScore = 10.0, heightPenalty = 0.0,
                memoryPenalty = 0.0, burnoutPenalty = 0.0
            ) }
            var chosen: String? = null
            activity.get().setContent {
                AilaTheme { SeatMapProposalsScreen(proposals, students.associateBy { it.id }, { chosen = it.id }) }
            }
            compose.mainClock.advanceTimeBy(1000)
            compose.onNodeWithText("Nome0", substring = true, useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("Modifica").assertIsDisplayed()
            compose.onNodeWithText("Punteggio complessivo", substring = true).assertDoesNotExist()
            compose.waitForIdle()
            compose.runOnIdle {
                val view = activity.get().window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val dir = File("build/reports/tests/ui-screenshots").apply { mkdirs() }
                File(dir, "seat-proposals-${style.key}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            compose.onNodeWithText("Proposta 2").performClick()
            compose.onNodeWithText("Modifica").performClick()
            compose.runOnIdle { assertEquals("p2", chosen) }
        } finally {
            activity.pause().stop().destroy()
            AppTheme.uiStyle = UiStyle.AUTO
            AppTheme.reduceMotion = false
        }
    }
}
