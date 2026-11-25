package circolareplus.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import circolareplus.ai.AiProvider
import circolareplus.ai.assistant.AssistantAuthor
import circolareplus.ai.assistant.AssistantConversation
import circolareplus.ai.assistant.AssistantMessage
import circolareplus.ai.assistant.AssistantSource
import circolareplus.ai.assistant.AssistantSourceKind
import circolareplus.design.AilaTheme
import circolareplus.design.AppTheme
import circolareplus.design.UiStyle
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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h850dp-xxhdpi", manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssistantClaudeUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>

    private val now = System.currentTimeMillis()
    private val sampleConversations = listOf(
        AssistantConversation(
            id = "c1",
            title = "Piano studio fisica e matematica",
            createdAtMillis = now - 12 * 60_000L,
            updatedAtMillis = now - 12 * 60_000L,
            pinned = true,
            messages = listOf(
                AssistantMessage("u1", AssistantAuthor.USER, "Prepara un piano per la verifica di fisica"),
                AssistantMessage("a1", AssistantAuthor.ASSISTANT, "Ecco una scaletta chiara per ripassare.")
            )
        ),
        AssistantConversation(
            id = "c2",
            title = "Riassunto circolare n. 214 gita",
            createdAtMillis = now - 3 * 3600_000L,
            updatedAtMillis = now - 3 * 3600_000L,
            pinned = true,
            messages = listOf(
                AssistantMessage("u2", AssistantAuthor.USER, "Riassumi la circolare 214"),
                AssistantMessage("a2", AssistantAuthor.ASSISTANT, "Scadenza acconto il 15 aprile.", sources = listOf(AssistantSource(AssistantSourceKind.CIRCULAR, "Circolare n. 214", 214)))
            )
        ),
        AssistantConversation(
            id = "c3",
            title = "Verifica di storia lunedì",
            createdAtMillis = now - 26 * 3600_000L,
            updatedAtMillis = now - 26 * 3600_000L,
            pinned = false,
            messages = listOf(
                AssistantMessage("u3", AssistantAuthor.USER, "Quando è la verifica di storia?"),
                AssistantMessage("a3", AssistantAuthor.ASSISTANT, "Lunedì alla terza ora.")
            )
        )
    )

    @Before fun setup() {
        AppTheme.uiStyle = UiStyle.EXPRESSIVE
        AppTheme.isDarkMode = false
        activity = Robolectric.buildActivity(ComponentActivity::class.java)
        activity.get().setTheme(android.R.style.Theme_Material_NoActionBar)
        activity.setup()
    }

    @After fun teardown() {
        activity.pause().stop().destroy()
        AppTheme.uiStyle = UiStyle.AUTO
        AppTheme.isDarkMode = false
    }

    @Test fun welcomeComposerTypingSingleMicDrawerAndSearchFlow() {
        var incognito by mutableStateOf(false)
        var openedConversationId: String? = null
        var sentPrompt: String? = null
        var wentBack = false

        activity.get().setContent {
            AilaTheme {
                AssistantChatScreen(
                    messages = emptyList(),
                    isThinking = false,
                    conversations = sampleConversations,
                    onSend = { sentPrompt = it },
                    onBackClick = { wentBack = true },
                    onClearChat = {},
                    onOpenConversation = { openedConversationId = it.id },
                    onDeleteConversation = {},
                    onOpenSource = {},
                    onAttachContent = {},
                    isIncognito = incognito,
                    onIncognitoChange = { incognito = it }
                )
            }
        }

        compose.onNodeWithText("Da dove iniziamo?").assertIsDisplayed()
        // Nessuna barra tab inferiore, nessuna ricerca in alto a destra, un solo microfono
        compose.onNodeWithContentDescription("Cerca nelle chat").assertDoesNotExist()
        compose.onNodeWithContentDescription("Avvia voce").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Registra vocale").assertCountEquals(1)
        compose.onNodeWithContentDescription("Seleziona modello e ragionamento").assertIsDisplayed()
        screenshot("aila-gemini-welcome-light")

        // Verifica che si possa scrivere subito senza perdere il focus e inviare
        compose.onNode(hasSetTextAction()).performTextInput("Quando è la verifica?")
        compose.onNodeWithContentDescription("Invia").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("Quando è la verifica?", sentPrompt) }

        // Tema scuro OLED
        compose.runOnIdle { AppTheme.isDarkMode = true }
        screenshot("aila-gemini-welcome-dark")

        // Apri cassetto laterale in dark mode
        compose.onNodeWithContentDescription("Apri menu chat").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Fissate").assertIsDisplayed()
        compose.onNodeWithText("Tutte").assertIsDisplayed()
        compose.onNodeWithText("Nuova chat").assertIsDisplayed()
        compose.onNodeWithText("Scorciatoie").assertDoesNotExist()
        compose.onNodeWithText("Piano di studio").assertDoesNotExist()
        screenshot("aila-gemini-drawer-dark")

        // Ricerca dalla barra del cassetto
        compose.onNodeWithContentDescription("Cerca nelle conversazioni").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Campo ricerca chat").performTextInput("verifica")
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("Verifica di storia lunedì", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Verifica di storia lunedì", substring = true).performClick()
        compose.runOnIdle { assertEquals("c3", openedConversationId) }

        compose.runOnIdle { AppTheme.isDarkMode = false }

        // Modalità incognito
        compose.onNodeWithContentDescription("Chat in incognito").performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithText("Questa conversazione non viene salvata nello storico del telefono.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Esci dalla chat in incognito").performClick()
        compose.mainClock.advanceTimeBy(350)

        // Pulsante Indietro in alto a sinistra
        compose.onNodeWithContentDescription("Indietro").performClick()
        compose.runOnIdle { assertTrue(wentBack) }
    }

    @Test fun modelAndProviderPickerAlwaysAccessibleAndUpdatesSelection() {
        var provider by mutableStateOf(AiProvider.AILA_CLOUD.id)
        var effort by mutableIntStateOf(1)

        activity.get().setContent {
            AilaTheme {
                AssistantChatScreen(
                    messages = emptyList(),
                    isThinking = false,
                    conversations = emptyList(),
                    onSend = {},
                    onBackClick = {},
                    onClearChat = {},
                    onOpenConversation = {},
                    onDeleteConversation = {},
                    onOpenSource = {},
                    onAttachContent = {},
                    selectedAiProvider = provider,
                    onSelectAiProvider = { provider = it },
                    localModelLabel = "Qwen 3 4B",
                    hasGoogleKey = true,
                    hasCompatibleConfig = false,
                    localModelInstalled = true,
                    effortLevel = effort,
                    onEffortLevelChange = { effort = it }
                )
            }
        }

        // Il selettore modello/provider è visibile subito anche a campo vuoto
        compose.runOnIdle { AppTheme.isDarkMode = true }
        compose.onNodeWithContentDescription("Seleziona modello e ragionamento").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Modello e provider").assertIsDisplayed()
        compose.onNodeWithText("Google Gemini").assertIsDisplayed()
        screenshot("aila-gemini-model-sheet-dark")

        compose.onNodeWithText("Google Gemini").performClick()
        compose.runOnIdle { assertEquals(AiProvider.GOOGLE_AI_STUDIO.id, provider) }

        compose.onNodeWithContentDescription("Ragionamento: Alto").performClick()
        compose.runOnIdle { assertEquals(2, effort) }
    }

    @Test fun activeConversationShowsModelInHeaderAndCleanActionRow() {
        var toastOpened = false
        val chatMessages = listOf(
            AssistantMessage("u1", AssistantAuthor.USER, "Preparami una scaletta veloce per ripassare fisica stasera"),
            AssistantMessage(
                id = "a1",
                author = AssistantAuthor.ASSISTANT,
                text = "Una sessione leggera funziona meglio se dividi teoria ed esercizi.\n\n## Piano di ripasso\n- **Inizia facile:** Rileggi le formule di cinematica e i grafici spazio-tempo.\n- **Blocco centrale:** Fai 3 esercizi guidati su moto uniformemente accelerato.\n- **Chiusura rapida:** Scrivi su un foglio i passaggi che ti confondono di più.\n\nVuoi che prepari anche 5 domande di autoverifica?",
                sources = listOf(
                    AssistantSource(AssistantSourceKind.CALENDAR, "Verifica di Fisica · Domani"),
                    AssistantSource(AssistantSourceKind.CIRCULAR, "Circolare n. 214", 214)
                ),
                modelLabel = "AILA Cloud · Gemma 4"
            )
        )

        activity.get().setContent {
            AilaTheme {
                AssistantChatScreen(
                    messages = chatMessages,
                    isThinking = false,
                    conversations = sampleConversations,
                    activeConversationId = "c1",
                    onSend = {},
                    onBackClick = {},
                    onClearChat = {},
                    onOpenConversation = { if (it.id == "c2") toastOpened = true },
                    onDeleteConversation = {},
                    onOpenSource = {},
                    onAttachContent = {},
                    finishedToastConversation = sampleConversations[1],
                    onDismissFinishedToast = {}
                )
            }
        }

        compose.mainClock.advanceTimeBy(1200)
        // Il nome del modello è nell'intestazione sotto il titolo, una sola volta
        compose.onNodeWithText("AILA Cloud · Gemma 4").assertIsDisplayed()
        compose.onNodeWithText("Piano di ripasso", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Copia risposta").assertIsDisplayed()
        screenshot("aila-assistant-chat-light")

        compose.runOnIdle { AppTheme.isDarkMode = true }
        screenshot("aila-assistant-chat-dark")

        compose.onNodeWithText("Risposta pronta").performClick()
        compose.runOnIdle { assertTrue(toastOpened) }
    }

    private fun screenshot(name: String) {
        val directory = File(System.getProperty("aila.ui.screenshots") ?: "build/ui-screenshots").apply { mkdirs() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
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
