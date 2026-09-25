package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaIconButton
import circolareplus.design.AilaBackBar
import circolareplus.design.AilaCard
import circolareplus.design.AilaListRow
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaSectionTitle
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.AilaSegmentedTabs
import circolareplus.design.AilaSwitch
import circolareplus.design.ailaAppear
import circolareplus.design.ailaFieldColors
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.ai.LocalAiModel
import circolareplus.ai.deviceTierForRam
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Categorie di notifica che si possono spegnere una per una. */
enum class NotificationKind(val key: String, val label: String, val description: String) {
    CIRCULARS("circulars", "Circolari", "Quando la scuola pubblica una circolare nuova"),
    CALENDAR("calendar", "Calendario", "Nuove verifiche, scadenze e pagamenti"),
    BOARD("board", "Bacheca", "Nuove proposte della classe"),
    SEATMAP("seatmap", "Mappa posti", "Nuova disposizione o votazione aperta"),
    POLLS("polls", "Sondaggi", "Nuovi sondaggi interrogazioni")
}

/**
 * Impostazioni.
 *
 * Prima non esistevano: la chiave AI stava in mezzo al profilo, le notifiche si potevano solo
 * accendere/spegnere in blocco per la bacheca, e non c'era alcun modo di cambiare tema.
 */
@Composable
fun SettingsScreen(
    apiKey: String,
    onSaveApiKey: (String) -> Unit,
    onTestApiKey: suspend (String) -> String,
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    isNotificationKindEnabled: (NotificationKind) -> Boolean,
    onNotificationKindChange: (NotificationKind, Boolean) -> Unit,
    boardNotificationsEnabled: Boolean = true,
    onToggleBoardNotifications: (Boolean) -> Unit = {},
    systemNotificationsEnabled: Boolean = true,
    onToggleSystemNotifications: (Boolean) -> Unit = {},
    aiProvider: String = "GOOGLE_AI_STUDIO",
    onAiProviderChange: (String) -> Unit = {},
    /** Frase da mostrare se l'AI locale non è utilizzabile qui (iPhone, emulatore non ARM). */
    localAiUnavailableReason: String? = null,
    /** RAM totale del telefono in MB, 0 se non rilevabile. */
    deviceRamMb: Int = 0,
    /** Modelli selezionabili, con il consigliato per questo telefono in prima posizione. */
    localModels: List<LocalAiModel> = emptyList(),
    selectedLocalModelId: String = "",
    onSelectLocalModel: (LocalAiModel) -> Unit = {},
    isLocalModelInstalled: (LocalAiModel) -> Boolean = { false },
    /**
     * Scarica il modello riportando l'avanzamento e restituisce un messaggio finale.
     *
     * È sospesa e gira nello scope di questa schermata: uscire dalle Impostazioni interrompe il
     * download. Non è una perdita — il pezzo già scaricato resta su disco e la volta dopo si
     * riparte da lì — ma va detto all'utente, ed è quello che fa la riga sotto al pulsante.
     */
    downloadLocalModel: suspend (LocalAiModel, (Long, Long) -> Unit) -> String = { _, _ -> "" },
    /** Ferma il download in corso (su Android anche il Worker che continua in background). */
    onCancelLocalModelDownload: (LocalAiModel) -> Unit = {},
    onDeleteLocalModel: (LocalAiModel) -> Unit = {},
    onTestLocalModel: suspend () -> String = { "Non disponibile" },
    /**
     * Byte occupati da modelli non piu' in catalogo, e come liberarli.
     *
     * Senza questo, chi aveva scaricato un modello poi rimosso — i build solo-GPU, tolti perche'
     * non si avviavano — si terrebbe qualche giga occupato da un file che nessuna schermata
     * mostra piu' e che quindi non potrebbe nemmeno cancellare dall'app.
     */
    orphanModelBytes: Long = 0L,
    onDeleteOrphanModels: () -> Long = { 0L },
    appVersion: String = "1.0",
    onBackClick: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(apiKey) }
    var apiKeyStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var selectedAiProvider by remember { mutableStateOf(aiProvider) }
    var showAllModels by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadedBytes by remember { mutableStateOf(0L) }
    var downloadTotalBytes by remember { mutableStateOf(0L) }
    var downloadStatus by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var isTestingLocal by remember { mutableStateOf(false) }
    // Come per i filtri delle notifiche: "il modello è installato" è un file su disco, non stato
    // di Compose. Senza questo contatore la scheda continuerebbe a mostrare "Scarica" anche
    // dopo un download finito, finché non si esce e si rientra nella schermata.
    var modelsRevision by remember { mutableStateOf(0) }
    var selectedModelIdState by remember { mutableStateOf(selectedLocalModelId) }
    var orphansCleared by remember { mutableStateOf(false) }
    val activeModel = remember(selectedModelIdState, localModels) {
        localModels.firstOrNull { it.id == selectedModelIdState } ?: localModels.firstOrNull()
    }
    // I filtri delle notifiche stanno in LocalSettingsManager, che non è stato di Compose:
    // senza questo contatore gli interruttori non si muoverebbero al tocco, pur salvando.
    var notificationRevision by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.BackgroundLight)) {
        AilaBackBar(title = "Impostazioni", onBackClick = onBackClick)

        LazyColumn(
            contentPadding = PaddingValues(
                start = AppTheme.Space16,
                end = AppTheme.Space16,
                top = AppTheme.Space16,
                bottom = AppTheme.Space32
            ),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)
        ) {
            // --- Aspetto ------------------------------------------------------------------
            item { AilaSectionTitle(text = "Aspetto", modifier = Modifier.ailaAppear(0)) }
            item {
                AilaCard(modifier = Modifier.ailaAppear(1)) {
                    Column(modifier = Modifier.padding(AppTheme.Space16)) {
                        Text(
                            text = "Tema",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.TextDark
                        )
                        Spacer(modifier = Modifier.height(AppTheme.Space8))
                        AilaSegmentedTabs(
                            labels = listOf("Chiaro", "Scuro"),
                            selectedIndex = if (isDarkMode) 1 else 0,
                            onSelect = { index -> onDarkModeChange(index == 1) },
                            modifier = Modifier.fillMaxWidth()
                        )

                    }
                }
            }

            // --- Notifiche ----------------------------------------------------------------
            item { AilaSectionTitle(text = "Notifiche", modifier = Modifier.ailaAppear(2)) }
            item {
                AilaCard(modifier = Modifier.ailaAppear(3)) {
                    NotificationKind.entries.forEachIndexed { index, kind ->
                        AilaListRow(
                            title = kind.label,
                            subtitle = kind.description,
                            tint = AppTheme.TintBlue,
                            icon = { AppIcons.Bell(modifier = Modifier.size(19.dp), color = AppTheme.TintBlueInk) },
                            trailing = {
                                AilaSwitch(
                                    checked = run {
                                        notificationRevision
                                        isNotificationKindEnabled(kind)
                                    },
                                    onCheckedChange = {
                                        onNotificationKindChange(kind, it)
                                        notificationRevision++
                                    }
                                )
                            }
                        )
                        HorizontalDivider(color = AppTheme.Hairline, modifier = Modifier.padding(start = 72.dp))
                    }
                    AilaListRow(
                        title = "Bacheca",
                        subtitle = "Avvisi per le nuove proposte create",
                        tint = AppTheme.TintBlue,
                        icon = { AppIcons.Bell(modifier = Modifier.size(19.dp), color = AppTheme.TintBlueInk) },
                        trailing = {
                            AilaSwitch(
                                checked = boardNotificationsEnabled,
                                onCheckedChange = { onToggleBoardNotifications(it) }
                            )
                        }
                    )
                    HorizontalDivider(color = AppTheme.Hairline, modifier = Modifier.padding(start = 72.dp))
                    AilaListRow(
                        title = "Notifiche di sistema",
                        subtitle = "Mostra notifiche nel pannello del dispositivo",
                        tint = AppTheme.TintBlue,
                        icon = { AppIcons.Bell(modifier = Modifier.size(19.dp), color = AppTheme.TintBlueInk) },
                        trailing = {
                            AilaSwitch(
                                checked = systemNotificationsEnabled,
                                onCheckedChange = { onToggleSystemNotifications(it) }
                            )
                        }
                    )
                }
            }

            // --- AI ------------------------------------------------------------------------
            item { AilaSectionTitle(text = "Chiave AI di AILA Assistant", modifier = Modifier.ailaAppear(4)) }
            item {
                AilaCard(modifier = Modifier.ailaAppear(5)) {
                    Column(modifier = Modifier.padding(AppTheme.Space16)) {
                        Text(
                            text = "La chiave resta su questo dispositivo e serve ad analizzare le " +
                                "circolari in riservatezza. Se ne ottiene una gratuita, senza carta " +
                                "di credito, da Google AI Studio: aistudio.google.com/apikey",
                            fontSize = 12.sp,
                            color = AppTheme.TextMuted,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(AppTheme.Space12))

                        OutlinedTextField(
                            value = apiKeyInput,
                            onValueChange = {
                                apiKeyInput = it
                                apiKeyStatus = null
                            },
                            placeholder = { Text("AIzaSy…", fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false
            ),
                            shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp),
                            colors = ailaFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (apiKeyStatus != null) {
                            Spacer(modifier = Modifier.height(AppTheme.Space8))
                            Text(
                                text = apiKeyStatus!!,
                                fontSize = 12.sp,
                                color = AppTheme.TextMuted,
                                lineHeight = 17.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(AppTheme.Space12))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // "Prova la chiave" è la risposta al non poter capire perché l'AI non
                            // rispondeva: fa una chiamata minima e riporta la risposta di Google
                            // per esteso, invece di lasciare indovinare.
                            AilaSecondaryButton(
                                text = if (isTesting) "Provo…" else "Prova la chiave",
                                onClick = {
                                    if (!isTesting) {
                                        isTesting = true
                                        apiKeyStatus = null
                                        scope.launch {
                                            apiKeyStatus = onTestApiKey(apiKeyInput.trim())
                                            isTesting = false
                                        }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            AilaPrimaryButton(
                                text = "Salva",
                                onClick = {
                                    val trimmed = apiKeyInput.trim()
                                    onSaveApiKey(trimmed)
                                    apiKeyInput = trimmed
                                    // Prima il tasto non dava alcun segno: la chiave veniva salvata
                                    // ma sembrava che non fosse successo niente.
                                    apiKeyStatus = if (trimmed.isEmpty()) {
                                        "Chiave rimossa da questo dispositivo."
                                    } else {
                                        "Chiave salvata. Usa \"Prova la chiave\" per verificare che funzioni."
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // --- AI locale sul telefono -------------------------------------------------
            item { AilaSectionTitle(text = "AI locale (sul telefono)", modifier = Modifier.ailaAppear(6)) }
            item {
                AilaCard(modifier = Modifier.ailaAppear(7)) {
                    Column(modifier = Modifier.padding(AppTheme.Space16)) {
                        if (localAiUnavailableReason != null) {
                            // Su iPhone (e sugli emulatori non ARM) non c'è motore di inferenza:
                            // si dice perché, invece di mostrare un pulsante che non farebbe nulla.
                            Text(
                                text = "Non disponibile su questo dispositivo",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.TextDark
                            )
                            Spacer(modifier = Modifier.height(AppTheme.Space8))
                            Text(
                                text = localAiUnavailableReason,
                                fontSize = 12.sp,
                                color = AppTheme.TextMuted,
                                lineHeight = 17.sp
                            )
                        } else {
                            Text(
                                text = "Provider",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.TextDark
                            )
                            Spacer(modifier = Modifier.height(AppTheme.Space8))
                            AilaSegmentedTabs(
                                labels = listOf("Google AI", "AI locale (Beta)"),
                                selectedIndex = if (selectedAiProvider == "ON_DEVICE") 1 else 0,
                                onSelect = { index ->
                                    selectedAiProvider = if (index == 1) "ON_DEVICE" else "GOOGLE_AI_STUDIO"
                                    onAiProviderChange(selectedAiProvider)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(AppTheme.Space8))
                            if (selectedAiProvider == "ON_DEVICE") {
                                Text(
                                    text = "L'AI sul telefono e' in beta: su molti telefoni e' " +
                                        "lenta. I riassunti delle circolari arrivano comunque " +
                                        "dal server appena pronti e fermano l'analisi sul telefono.",
                                    fontSize = 11.sp,
                                    color = AppTheme.TextMuted,
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(AppTheme.Space4))
                            }
                            Text(
                                text = "Qualunque sia la scelta, se il provider principale non " +
                                    "risponde l'app prova in automatico con l'altro.",
                                fontSize = 11.sp,
                                color = AppTheme.TextFaint,
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(AppTheme.Space16))
                            HorizontalDivider(color = AppTheme.Hairline)
                            Spacer(modifier = Modifier.height(AppTheme.Space16))

                            Text(
                                text = "Modello",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.TextDark
                            )
                            Spacer(modifier = Modifier.height(AppTheme.Space4))
                            Text(
                                text = if (deviceRamMb > 0) {
                                    "Memoria rilevata: ${formatRam(deviceRamMb)} — fascia " +
                                        deviceTierForRam(deviceRamMb).label
                                } else {
                                    "Memoria del dispositivo non rilevata."
                                },
                                fontSize = 11.sp,
                                color = AppTheme.TextFaint,
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(AppTheme.Space12))

                            // Stesso elenco su entrambe le piattaforme. Su iOS il catalogo contiene solo
                            // Apple Intelligence (modello di sistema, nessun download), su Android i modelli
                            // scaricabili piu' AICore: la UI non deve distinguere, lo fa il catalogo.
                            // --- Android: elenco di modelli scaricabili ---
                            // Solo il modello scelto, con la possibilità di aprire l'elenco
                            // completo: mostrare cinque schede tutte insieme renderebbe la
                            // pagina illeggibile, e nella pratica si sceglie una volta sola.
                            modelsRevision // rilegge lo stato su disco dopo download/eliminazione
                            val shownModels = if (showAllModels) localModels else listOfNotNull(activeModel)
                            shownModels.forEach { model ->
                                LocalModelRow(
                                    model = model,
                                    isSelected = model.id == activeModel?.id,
                                    isInstalled = isLocalModelInstalled(model),
                                    isRecommended = model.id == localModels.firstOrNull()?.id,
                                    fits = model.fitsComfortablyIn(deviceRamMb),
                                    supportsActions = model.supportsActions,
                                    onClick = {
                                        if (model.id != activeModel?.id) {
                                            selectedModelIdState = model.id
                                            onSelectLocalModel(model)
                                            downloadStatus = null
                                            downloadedBytes = 0L
                                            downloadTotalBytes = 0L
                                        }
                                        showAllModels = false
                                    }
                                )
                                Spacer(modifier = Modifier.height(AppTheme.Space8))
                            }

                            if (orphanModelBytes > 0 && !orphansCleared) {
                                Spacer(modifier = Modifier.height(AppTheme.Space8))
                                Text(
                                    text = "Ci sono ${formatBytes(orphanModelBytes)} di modelli " +
                                        "non piu' usati. Libera spazio",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.TintRedInk,
                                    lineHeight = 16.sp,
                                    modifier = Modifier
                                        .clickable {
                                            val freed = onDeleteOrphanModels()
                                            orphansCleared = true
                                            downloadStatus = "Liberati ${formatBytes(freed)}."
                                        }
                                        .padding(vertical = AppTheme.Space4)
                                )
                            }

                            if (localModels.size > 1) {
                                Text(
                                    text = if (showAllModels) "Chiudi l'elenco" else "Scegli un altro modello",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.PrimaryBlue,
                                    modifier = Modifier
                                        .clickable { showAllModels = !showAllModels }
                                        .padding(vertical = AppTheme.Space4)
                                )
                            }

                            if (isDownloading) {
                                Spacer(modifier = Modifier.height(AppTheme.Space12))
                                DownloadProgressBar(
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = downloadTotalBytes
                                )
                            }

                            // Esito di "Prova il modello", download, eliminazione: prima su iOS era
                            // nascosto, quindi il tasto "Prova il modello" non mostrava mai nulla.
                            if (downloadStatus != null) {
                                Spacer(modifier = Modifier.height(AppTheme.Space8))
                                Text(
                                    text = downloadStatus!!,
                                    fontSize = 12.sp,
                                    color = AppTheme.TextMuted,
                                    lineHeight = 17.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(AppTheme.Space12))

                            val installed = activeModel != null && isLocalModelInstalled(activeModel)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (installed) {
                                    AilaSecondaryButton(
                                        text = if (isTestingLocal) "Provo…" else "Prova il modello",
                                        onClick = {
                                            if (!isTestingLocal) {
                                                isTestingLocal = true
                                                downloadStatus = null
                                                scope.launch {
                                                    downloadStatus = onTestLocalModel()
                                                    isTestingLocal = false
                                                }
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    // I modelli di sistema (Apple Intelligence, AICore) non sono file
                                    // dell'app: non c'e' niente da eliminare, e il pulsante dichiarava
                                    // "hai liberato 0 MB" senza fare nulla.
                                    if (activeModel?.isSystemModel != true) {
                                        AilaIconButton(
                                            contentDescription = "Elimina il modello dal telefono",
                                            onClick = {
                                                activeModel?.let(onDeleteLocalModel)
                                                downloadStatus = "Modello eliminato: hai liberato " +
                                                    "${activeModel?.readableSize ?: ""}."
                                                modelsRevision++
                                            }
                                        ) { tint -> AppIcons.Trash(modifier = Modifier.size(19.dp), color = tint) }
                                    }
                                } else if (isDownloading && activeModel != null) {
                                    // Il download va fermato da qui, non solo dalla notifica: chi
                                    // si accorge di essere sotto rete dati o di aver scelto il
                                    // modello sbagliato non deve aspettare qualche giga.
                                    Spacer(modifier = Modifier.weight(1f))
                                    AilaSecondaryButton(
                                        text = "Interrompi download",
                                        onClick = {
                                            downloadJob?.cancel()
                                            downloadJob = null
                                            onCancelLocalModelDownload(activeModel)
                                            isDownloading = false
                                            downloadStatus = "Download interrotto. Quello che era già " +
                                                "arrivato resta: se riprovi riparte da lì."
                                            modelsRevision++
                                        }
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                    AilaPrimaryButton(
                                        text = when {
                                            activeModel == null -> "Scarica"
                                            activeModel.isSystemModel -> "Attiva"
                                            else -> "Scarica (${activeModel.readableSize})"
                                        },
                                        enabled = !isDownloading && activeModel != null,
                                        onClick = {
                                            val model = activeModel ?: return@AilaPrimaryButton
                                            if (isDownloading) return@AilaPrimaryButton
                                            isDownloading = true
                                            downloadStatus = null
                                            downloadedBytes = 0L
                                            downloadTotalBytes = model.approxSizeBytes
                                            downloadJob = scope.launch {
                                                val message = downloadLocalModel(model) { done, total ->
                                                    downloadedBytes = done
                                                    downloadTotalBytes = total
                                                }
                                                // Interrotto a mano: il messaggio l'ha gia' scritto il
                                                // pulsante, "Download annullato." dal Worker lo coprirebbe.
                                                if (isDownloading) downloadStatus = message
                                                isDownloading = false
                                                downloadJob = null
                                                modelsRevision++
                                            }
                                        }
                                    )
                                }
                            }

                            if (!installed && activeModel?.isSystemModel != true) {
                                Spacer(modifier = Modifier.height(AppTheme.Space8))
                                Text(
                                    text = "Scarica con il Wi-Fi. Se il download si interrompe " +
                                        "riprende da dove era arrivato, non da capo.",
                                    fontSize = 11.sp,
                                    color = AppTheme.TextFaint,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            // --- Info ----------------------------------------------------------------------
            item { AilaSectionTitle(text = "Informazioni", modifier = Modifier.ailaAppear(8)) }
            item {
                AilaCard(modifier = Modifier.ailaAppear(7)) {
                    AilaListRow(
                        title = "AILA",
                        subtitle = "Versione $appVersion",
                        tint = AppTheme.TintSlate,
                        showChevron = false,
                        icon = { AppIcons.Sparkle(modifier = Modifier.size(19.dp), color = AppTheme.TintSlateInk) }
                    )
                }
            }
        }
    }
}

/**
 * Una riga dell'elenco modelli: nome, peso, stato e — quando serve — l'avviso che su questo
 * telefono il modello rischia di non partire.
 *
 * L'avviso c'e' perche' il peso del file da solo inganna: un modello da 2 GB non gira su un
 * telefono da 4 GB, dove il sistema ne occupa gia' oltre la meta'. Scoprirlo dopo aver scaricato
 * qualche giga sotto la rete della scuola sarebbe la cosa peggiore che questa funzione possa
 * fare, quindi si dice prima. Resta un avviso e non un divieto: la stima puo' sbagliare, e la
 * scelta e' dell'utente.
 */
@Composable
private fun LocalModelRow(
    model: LocalAiModel,
    isSelected: Boolean,
    isInstalled: Boolean,
    isRecommended: Boolean,
    fits: Boolean,
    supportsActions: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) AppTheme.TintBlue else AppTheme.SurfaceWhite)
            .border(1.dp, if (isSelected) AppTheme.PrimaryBlue else AppTheme.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(AppTheme.Space12)
    ) {
        // Il nome e il tag stanno sulla stessa riga, il peso va sotto: con tutti e tre in fila
        // ("Android AICore (Gemini Nano)", "Incluso nel sistema", "Installato") la riga non stava
        // in larghezza, il tag veniva schiacciato e andava a capo una lettera per riga — una
        // colonna alta e verde che allargava la scheda di centinaia di pixel.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = model.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark,
                modifier = Modifier.weight(1f)
            )
            val tag = when {
                isInstalled -> "Installato"
                isRecommended -> "Consigliato"
                else -> null
            }
            if (tag != null) {
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                Text(
                    text = tag,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    color = if (isInstalled) AppTheme.TintGreenInk else AppTheme.TintSlateInk,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                        .background(if (isInstalled) AppTheme.TintGreen else AppTheme.TintSlate)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = model.readableSize,
            fontSize = 11.sp,
            color = AppTheme.TextMuted
        )
        Spacer(modifier = Modifier.height(AppTheme.Space4))
        Text(
            text = model.description,
            fontSize = 11.sp,
            color = AppTheme.TextMuted,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(AppTheme.Space4))
        // Un modello che non sa produrre azioni resta utile per il riassunto, ma non riempie il
        // calendario da solo: e' una differenza concreta e va detta prima di scaricarlo, non
        // dopo.
        Text(
            text = if (supportsActions) {
                "Sa proporre scadenze da aggiungere al calendario."
            } else {
                "Solo riassunti: non propone scadenze per il calendario."
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (supportsActions) AppTheme.TintGreenInk else AppTheme.TextFaint,
            lineHeight = 15.sp
        )
        if (!fits) {
            Spacer(modifier = Modifier.height(AppTheme.Space4))
            Text(
                text = "Questo modello ha bisogno di circa ${model.recommendedRamMb / 1000} GB " +
                    "di memoria: su questo telefono potrebbe non riuscire a partire.",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TintRedInk,
                lineHeight = 15.sp
            )
        }
    }
}

/**
 * Barra di avanzamento del download, disegnata a mano invece di usare LinearProgressIndicator:
 * il resto della schermata usa le forme e i colori di AppTheme, e un indicatore Material vi
 * stonerebbe dentro.
 */
@Composable
private fun DownloadProgressBar(downloadedBytes: Long, totalBytes: Long) {
    val fraction = if (totalBytes > 0) {
        (downloadedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppTheme.TintSlate)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(AppTheme.PrimaryBlue)
            )
        }
        Spacer(modifier = Modifier.height(AppTheme.Space4))
        Text(
            text = "${formatBytes(downloadedBytes)} di ${formatBytes(totalBytes)} " +
                "(${(fraction * 100).toInt()}%)",
            fontSize = 11.sp,
            color = AppTheme.TextFaint
        )
    }
}

/** "1,4 GB" / "820 MB": la stessa forma usata nel catalogo dei modelli. */
private fun formatBytes(bytes: Long): String {
    val mb = bytes / 1_000_000
    return if (mb >= 1000) {
        val tenthsOfGb = mb / 100
        "${tenthsOfGb / 10},${tenthsOfGb % 10} GB"
    } else {
        "$mb MB"
    }
}

/** La RAM del telefono in GB con un decimale: "7,6 GB". */
private fun formatRam(totalRamMb: Int): String {
    val tenthsOfGb = totalRamMb * 10 / 1024
    return "${tenthsOfGb / 10},${tenthsOfGb % 10} GB"
}
