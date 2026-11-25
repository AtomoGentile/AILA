package circolareplus.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
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
        ),
        AssistantConversation(
            id = "c4",
            title = "Dove sono seduto nella nuova mappa",
            createdAtMillis = now - 52 * 3600_000L,
            updatedAtMillis = now - 52 * 3600_000L,
            pinned = false,
            messages = listOf(
                AssistantMessage("u4", AssistantAuthor.USER, "Dove sono seduto?"),
                AssistantMessage("a4", AssistantAuthor.ASSISTANT, "Seconda fila, banco centrale.")
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

    @Test fun welcomeIncognitoDrawerAndSearchFlow() {
        var incognito by mutableStateOf(false)
        var openedConversationId: String? = null
        var selectedTab: MainTab? = null

        activity.get().setContent {
            AilaTheme {
                AssistantChatScreen(
                    messages = emptyList(),
                    isThinking = false,
                    conversations = sampleConversations,
                    onSend = {},
                    onBackClick = {},
                    onClearChat = {},
                    onOpenConversation = { openedConversationId = it.id },
                    onDeleteConversation = {},
                    onOpenSource = {},
                    onAttachContent = {},
                    isIncognito = incognito,
                    onIncognitoChange = { incognito = it },
                    unreadTabs = setOf(MainTab.CLASS, MainTab.POLLS),
                    onSelectMainTab = { selectedTab = it }
                )
            }
        }

        compose.onNodeWithText("Come posso aiutarti oggi?").assertIsDisplayed()
        screenshot("claude-welcome-light")

        // Tema scuro
        compose.runOnIdle { AppTheme.isDarkMode = true }
        screenshot("claude-welcome-dark")
        compose.runOnIdle { AppTheme.isDarkMode = false }

        // Attiva modalità incognito
        compose.onNodeWithContentDescription("Chat in incognito").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Questa conversazione non viene salvata nello storico del telefono.").assertIsDisplayed()
        screenshot("claude-incognito")
        compose.onNodeWithContentDescription("Esci dalla chat in incognito").performClick()
        compose.mainClock.advanceTimeBy(400)

        // Apri cassetto laterale sinistro
        compose.onNodeWithContentDescription("Apri menu chat").performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("AILA").assertIsDisplayed()
        compose.onNodeWithText("Fissate").assertIsDisplayed()
        compose.onNodeWithText("Recenti").assertIsDisplayed()
        screenshot("claude-drawer")

        // Apri ricerca dal footer del cassetto e cerca "verifica"
        compose.onNodeWithContentDescription("Cerca nelle conversazioni").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Campo ricerca chat").performTextInput("verifica")
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("Verifica di storia lunedì", substring = true).assertIsDisplayed()
        screenshot("claude-search")
        compose.onNodeWithText("Verifica di storia lunedì", substring = true).performClick()
        compose.runOnIdle { assertEquals("c3", openedConversationId) }

        // Tocca una tab di AILA nella barra inferiore stile Claude
        compose.onNodeWithContentDescription("Calendario").performTouchInput { click() }
        compose.runOnIdle { assertEquals(MainTab.CALENDAR, selectedTab) }
    }

    @Test fun modelAndEffortBottomSheetUpdatesProviderAndLevel() {
        var provider by mutableStateOf(AiProvider.AILA_CLOUD.id)
        var effort by mutableIntStateOf(1)

        activity.get().setContent {
            AilaTheme {
                AssistantChatScreen(
                    messages = emptyList(),
                    isThinking = false,
                    conversations = emptyList(),
                    initialDraft = "Spiegami il moto parabolico",
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
                    effortLevel = effort,
                    onEffortLevelChange = { effort = it },
                    onSelectMainTab = {}
                )
            }
        }

        // Composer espanso mostra la pillola modello/ragionamento
        compose.onNodeWithContentDescription("Seleziona modello e ragionamento").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithText("Modello").assertIsDisplayed()
        compose.onNodeWithText("Google Gemini").assertIsDisplayed()
        screenshot("claude-model-sheet")

        // Cambia modello in Google Gemini
        compose.onNodeWithText("Google Gemini").performClick()
        compose.runOnIdle { assertEquals(AiProvider.GOOGLE_AI_STUDIO.id, provider) }

        // Entra nella schermata Ragionamento
        compose.onNodeWithContentDescription("Modifica livello di ragionamento").performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithText("Ragionamento").assertIsDisplayed()
        compose.onNodeWithContentDescription("Livello di ragionamento: Medio").assertIsDisplayed()
        screenshot("claude-effort-sheet")
    }

    @Test fun activeConversationRendersEditorialProseActionsAndToast() {
        var toastOpened = false
        val chatMessages = listOf(
            AssistantMessage("u1", AssistantAuthor.USER, "Preparami una scaletta veloce per ripassare fisica stasera"),
            AssistantMessage(
                id = "a1",
                author = AssistantAuthor.ASSISTANT,
                text = "Una sessione leggera funziona meglio se dividi teoria ed esercizi.\n\n## Piano di ripasso\n- **Inizia facile:** Rileggi le formule di cinematica e i grafici spazio-tempo.\n- **Blocco centrale:** Fai 3 esercizi guidati su moto uniformemente accelerato e caduta libera.\n- **Chiusura rapida:** Scrivi su un foglio i 4 passaggi che ti confondono di più.\n\nVuoi che prepari anche 5 domande di autoverifica?",
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
                    unreadTabs = setOf(MainTab.CLASS),
                    onSelectMainTab = {},
                    finishedToastConversation = sampleConversations[1],
                    onDismissFinishedToast = {}
                )
            }
        }

        compose.mainClock.advanceTimeBy(1200)
        compose.onNodeWithText("Piano di ripasso", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Risposta pronta").assertIsDisplayed()
        compose.onNodeWithContentDescription("Copia risposta").assertIsDisplayed()
        screenshot("claude-chat-light")

        compose.runOnIdle { AppTheme.isDarkMode = true }
        screenshot("claude-chat-dark")

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
