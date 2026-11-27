package circolareplus.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import circolareplus.ai.AiProvider
import circolareplus.ai.AssistantProviderModel
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
import kotlin.math.abs
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

    @Test fun welcomeDragDrawerUserInitialsAndNoBackOrIncognitoHeader() {
        var incognito by mutableStateOf(false)
        var openedConversationId: String? = null
        var sentPrompt: String? = null

        activity.get().setContent {
            AilaTheme {
                AssistantChatScreen(
                    messages = emptyList(),
                    isThinking = false,
                    conversations = sampleConversations,
                    onSend = { sentPrompt = it },
                    onBackClick = {},
                    onClearChat = {},
                    onOpenConversation = { openedConversationId = it.id },
                    onDeleteConversation = {},
                    onOpenSource = {},
                    onAttachContent = {},
                    isIncognito = incognito,
                    onIncognitoChange = { incognito = it },
                    userInitials = "SB",
                    userDisplayName = "Simone Bianchin"
                )
            }
        }

        compose.onNodeWithText("Da dove iniziamo?").assertIsDisplayed()
        // Nessun tasto Indietro in alto, un solo microfono, selettore modello in alto
        compose.onNodeWithContentDescription("Indietro").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Registra vocale").assertCountEquals(1)
        compose.onNodeWithContentDescription("Seleziona modello e ragionamento").assertIsDisplayed()

        // Scrittura e invio
        compose.onNode(hasSetTextAction()).performTextInput("Quando è la verifica?")
        compose.onNodeWithContentDescription("Invia").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("Quando è la verifica?", sentPrompt) }

        // Tema scuro + apertura pannello cronologia TRASCINANDO da metà schermo verso destra:
        // deve muoversi SOLO il pannello a sinistra, mai la chat dietro!
        compose.runOnIdle { AppTheme.isDarkMode = true }
        val welcomeLeftBefore = compose.onNodeWithText("Da dove iniziamo?").getUnclippedBoundsInRoot().left
        screenshot("aila-b5-welcome-dark")
        compose.onRoot().performTouchInput { swipeRight(startX = centerX * 0.45f, endX = right * 0.9f) }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Fissate").assertIsDisplayed()
        compose.onNodeWithText("SB").assertIsDisplayed()
        compose.onNodeWithText("Simone Bianchin").assertIsDisplayed()
        val welcomeLeftAfter = compose.onNodeWithText("Da dove iniziamo?").getUnclippedBoundsInRoot().left
        assertEquals(welcomeLeftBefore, welcomeLeftAfter, "La chat non deve spostarsi quando si trascina il pannello")
        screenshot("aila-b5-drawer-dark")

        // Ricerca dalla sidebar
        compose.onNodeWithContentDescription("Cerca nelle conversazioni").performClick()
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("Campo ricerca chat").performTextInput("verifica")
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithText("Verifica di storia lunedì", substring = true).performClick()
        compose.runOnIdle { assertEquals("c3", openedConversationId) }

        // Modalità incognito: nessuna pillola "Incognito" nella barra in alto
        compose.onNodeWithContentDescription("Chat in incognito").performClick()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithText("Incognito").assertDoesNotExist()
        compose.onNodeWithText("Chat in incognito").assertIsDisplayed()
        compose.onNodeWithContentDescription("Esci dalla chat in incognito").performClick()
    }

    @Test fun pickerUsesTheSelectedModelsReasoningOptions() {
        var provider by mutableStateOf(AiProvider.AILA_CLOUD.id)
        var model by mutableStateOf("")
        var effort by mutableStateOf("none")
        val hydrogen = AssistantProviderModel(
            "gemini-4-hydrogen", "Gemini 4 Hydrogen", listOf("", "none", "low", "medium", "high", "xhigh")
        )
        var openedAiSettings = false

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
                    onOpenAiSettings = { openedAiSettings = true },
                    selectedAiProvider = provider,
                    onSelectAiProvider = { provider = it },
                    localModelLabel = "Qwen 3 4B",
                    hasGoogleKey = true,
                    compatibleProviders = mapOf("OPENAI" to "OpenAI", "ANTHROPIC" to "Anthropic"),
                    localModelInstalled = true,
                    selectedModelId = model,
                    selectedReasoning = effort,
                    loadModels = { listOf(hydrogen) },
                    onSelectModel = { model = it },
                    onSelectReasoning = { effort = it },
                    userInitials = "SB",
                    userDisplayName = "Simone Bianchin"
                )
            }
        }

        compose.runOnIdle { AppTheme.isDarkMode = true }
        // Apri il picker modelli in alto vicino al menu
        compose.onNodeWithContentDescription("Seleziona modello e ragionamento").performClick()
        compose.mainClock.advanceTimeBy(450)
        compose.onNodeWithText("OpenAI").assertIsDisplayed()
        compose.onNodeWithText("Anthropic").assertIsDisplayed()
        compose.onNodeWithText("Endpoint da configurare").assertDoesNotExist()
        compose.onNodeWithText("Google Gemini").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(AiProvider.GOOGLE_AI_STUDIO.id, provider) }

        compose.onNodeWithContentDescription("Modello").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(450)
        compose.onNodeWithText("Gemini 4 Hydrogen").performClick()
        compose.runOnIdle {
            assertEquals(hydrogen.id, model)
            assertEquals("low", effort)
        }

        compose.onNodeWithContentDescription("Livello di ragionamento").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(450)
        val pill = compose.onNodeWithContentDescription("Pillola del ragionamento", useUnmergedTree = true)
        pill.performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Massimo").assertDoesNotExist()
        compose.onNodeWithText("Predefinito").assertDoesNotExist()
        compose.onNodeWithText("Disattivato").assertDoesNotExist()
        compose.onNodeWithText("Medio").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("medium", effort) }
        pill.performScrollTo().performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals("xhigh", effort) }
        val track = pill.getUnclippedBoundsInRoot()
        listOf("Basso", "Medio", "Alto").forEachIndexed { index, label ->
            val bounds = compose.onNodeWithText(label).getUnclippedBoundsInRoot()
            val expectedCenter = track.left + track.width * ((index + .5f) / 4f)
            val actualCenter = (bounds.left + bounds.right) / 2f
            assertTrue(abs((actualCenter - expectedCenter).value) <= 1f, "$label must align with its tick")
        }
        screenshot("aila-provider-model-picker-dark")

        compose.onNodeWithText("Impostazioni AI").performScrollTo().assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { assertTrue(openedAiSettings) }
    }

    @Test fun activeConversationRendersCleanHeaderAndProse() {
        var toastOpened = false
        val chatMessages = listOf(
            AssistantMessage(
                id = "u1",
                author = AssistantAuthor.USER,
                text = "Preparami una scaletta veloce per ripassare fisica stasera",
                attachedFiles = listOf(
                    circolareplus.ai.assistant.AssistantAttachedFile(
                        localId = "voice-1",
                        name = "vocale_fisica.m4a",
                        service = "AUDIO"
                    )
                )
            ),
            AssistantMessage(
                id = "a1",
                author = AssistantAuthor.ASSISTANT,
                text = "Una sessione leggera funziona meglio se dividi teoria ed esercizi.\n\n## Piano di ripasso\n- **Inizia facile:** Rileggi le formule di cinematica.\n- **Blocco centrale:** Fai 3 esercizi guidati.\n\nVuoi 5 domande di autoverifica?",
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
                    isIncognito = true,
                    onSend = {},
                    onBackClick = {},
                    onClearChat = {},
                    onOpenConversation = { if (it.id == "c2") toastOpened = true },
                    onDeleteConversation = {},
                    onOpenSource = {},
                    onAttachContent = {},
                    userInitials = "SB",
                    userDisplayName = "Simone Bianchin",
                    finishedToastConversation = sampleConversations[1],
                    onDismissFinishedToast = {}
                )
            }
        }

        compose.mainClock.advanceTimeBy(1200)
        // Anche in incognito dentro la chat non compare la scritta "Chat in incognito" in alto
        compose.onNodeWithText("Chat in incognito").assertDoesNotExist()
        compose.onNodeWithText("Piano di ripasso", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Copia risposta").assertIsDisplayed()
        compose.onNodeWithContentDescription("Riproduci vocale").assertIsDisplayed()
        screenshot("aila-b6-chat-voice-light")

        compose.runOnIdle { AppTheme.isDarkMode = true }
        screenshot("aila-b6-chat-voice-dark")

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
