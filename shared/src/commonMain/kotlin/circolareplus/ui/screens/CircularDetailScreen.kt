package circolareplus.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaAssistantBadge
import circolareplus.design.AilaBackBar
import circolareplus.design.AilaCard
import circolareplus.design.AilaDot
import circolareplus.design.AilaIconTile
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.ailaAppear
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.appSafeDrawingPadding
import circolareplus.domain.model.Circular
import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.CircularAttachment
import circolareplus.domain.model.CircularRelevanceBadge
import circolareplus.ai.CalendarDuplicates
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.ExtractedDeadline
import circolareplus.pdf.renderPdfPages
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Dettaglio di una circolare, con il **PDF mostrato dentro l'app**.
 *
 * Prima qui c'era solo un segnaposto ("Documento PDF ufficiale") e un tasto che apriva il file nel
 * visualizzatore di sistema: per leggere una circolare si usciva da AILA. Ora le pagine vengono
 * disegnate e scorrono in linea (su iOS con PDFKit, vedi `PdfPageRenderer.ios.kt`); il tasto per
 * aprirla fuori resta come alternativa, ed è l'unica strada se il PDF è protetto o illeggibile.
 */
@Composable
fun CircularDetailScreen(
    circular: Circular,
    classification: CircularAiClassification?,
    isClassifying: Boolean = false,
    /** In attesa che finisca l'analisi di un'altra circolare: sul telefono ne gira una alla volta. */
    isQueued: Boolean = false,
    /**
     * Circolare troppo lunga per l'AI del telefono, che ne leggerebbe solo l'inizio: il riassunto
     * completo arriva dal server. "Analizza" la analizza comunque sul telefono.
     */
    isAwaitingServer: Boolean = false,
    /** `false` quando l'analisi in corso e' di Gemini e non del modello sul telefono. */
    analysisOnDevice: Boolean = true,
    /** Ferma l'analisi in corso o in coda (tasto quadrato accanto al titolo della sezione). */
    onStopAnalysis: (() -> Unit)? = null,
    onBackClick: () -> Unit = {},
    onDownloadPdfClick: () -> Unit = {},
    onOpenAttachmentClick: (CircularAttachment) -> Unit = {},
    onLoadPdfBytes: (suspend () -> ByteArray)? = null,
    /**
     * Scarica i byte di un allegato PDF (`attachment.isPdf == true`) da rendere in coda al
     * documento principale, con lo stesso visualizzatore — vedi [DocumentSection].
     */
    onLoadAttachmentPdfBytes: (suspend (CircularAttachment) -> ByteArray)? = null,
    onReanalyze: (() -> Unit)? = null,
    /**
     * Crea davvero l'evento in calendario e restituisce un messaggio sull'esito.
     *
     * È qui che la classificazione smette di essere solo un riassunto: le scadenze che il modello
     * riconosce nel PDF diventano voci del calendario. Prima venivano estratte e buttate via —
     * `detectedDeadlines` non era letto da nessuna schermata.
     *
     * L'evento non si crea da solo: lo si aggiunge con un tocco. La scrittura va sul calendario
     * **condiviso della classe**, quindi un modello che sbaglia una data la sbaglierebbe per
     * tutti; e un modello da qualche miliardo di parametri che gira su un telefono una data ogni
     * tanto la sbaglia. Un tocco di conferma costa niente e rende l'errore innocuo.
     */
    onCreateCalendarEvent: (suspend (ExtractedDeadline) -> String)? = null,
    /**
     * Gli eventi gia' in calendario: una scadenza che c'e' gia' non mostra il tasto
     * "Aggiungi al calendario", che creava un evento doppio.
     */
    calendarEvents: List<CalendarEvent> = emptyList()
) {
    val scope = rememberCoroutineScope()
    // Scadenze già aggiunte in questa visita, con l'esito: evita di ricreare due volte lo stesso
    // evento premendo due volte, e mostra cosa ha risposto il server (che sui doppioni avvisa
    // invece di creare).
    val actionOutcomes = remember(circular.number) { mutableStateMapOf<String, String>() }
    var actionInFlight by remember(circular.number) { mutableStateOf<String?>(null) }
    // Il documento principale è la prima sezione (label null); ogni allegato PDF ne aggiunge
    // un'altra in coda, così le sue pagine scorrono nello stesso visualizzatore invece di essere
    // solo un link da aprire fuori dall'app.
    var sections by remember(circular.number) { mutableStateOf<List<DocumentSection>>(emptyList()) }
    var isRenderingPdf by remember(circular.number) { mutableStateOf(onLoadPdfBytes != null) }
    var pdfError by remember(circular.number) { mutableStateOf<String?>(null) }

    LaunchedEffect(circular.number) {
        val loader = onLoadPdfBytes ?: return@LaunchedEffect
        isRenderingPdf = true
        pdfError = null
        sections = emptyList()
        var receivedAny = false

        suspend fun renderInto(label: String?, bytes: ByteArray) {
            // La prima pagina disegnata sparisce dallo spinner e appare subito: non si aspettano
            // piu' tutte le pagine prima di mostrarne una, che per un documento lungo voleva dire
            // uno spinner fermo per parecchi secondi.
            renderPdfPages(bytes).collect { page ->
                if (!receivedAny) {
                    receivedAny = true
                    isRenderingPdf = false
                }
                sections = if (sections.isNotEmpty() && sections.last().label == label) {
                    val last = sections.last()
                    sections.dropLast(1) + last.copy(pages = last.pages + page)
                } else {
                    sections + DocumentSection(label, listOf(page))
                }
            }
        }

        try {
            renderInto(null, loader())

            // Allegati PDF (colonna "Allegati" di Spaggiari): renderizzati in coda al documento
            // principale, uno alla volta. Un allegato che non si scarica o non si apre non deve
            // bloccare gli altri né il documento principale già mostrato.
            val attachmentLoader = onLoadAttachmentPdfBytes
            if (attachmentLoader != null) {
                for (attachment in circular.attachments) {
                    if (!attachment.isPdf) continue
                    try {
                        renderInto(attachment.label, attachmentLoader(attachment))
                    } catch (e: Exception) {
                        // Ignorato di proposito: l'allegato resta comunque disponibile dalla
                        // sezione "Allegati" qui sopra, che lo apre nel browser di sistema.
                    }
                }
            }

            if (!receivedAny) {
                pdfError = "Anteprima non disponibile per questo documento."
            }
        } catch (e: Exception) {
            pdfError = "Impossibile scaricare il documento: ${e.message ?: e::class.simpleName}"
        } finally {
            isRenderingPdf = false
        }
    }

    val (badgeColor, badgeText) = when (classification?.badge) {
        CircularRelevanceBadge.RELEVANT -> AppTheme.BadgeRelevantGreen to "Ti riguarda"
        CircularRelevanceBadge.POTENTIAL -> AppTheme.BadgePotentialYellow to "Potenziale interesse"
        CircularRelevanceBadge.NOT_RELEVANT -> AppTheme.BadgeNotRelevantGray to "Non sembra riguardarti"
        null -> AppTheme.TextFaint to "Da classificare"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
            .appSafeDrawingPadding()
    ) {
        AilaBackBar(
            title = "Circolare n. ${circular.number}",
            onBackClick = onBackClick,
            action = {
                AilaSecondaryButton(
                    text = "Apri fuori",
                    onClick = onDownloadPdfClick,
                    compact = true,
                    icon = { tint -> AppIcons.Document(modifier = Modifier.size(14.dp), color = tint) }
                )
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppTheme.Space16,
                end = AppTheme.Space16,
                top = AppTheme.Space16,
                bottom = AppTheme.Space32
            ),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().ailaAppear(0),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AilaDot(color = badgeColor)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                    }
                    Text(text = circular.publishDate, fontSize = 13.sp, color = AppTheme.TextMuted)
                }
            }

            item {
                Text(
                    text = circular.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TextDark,
                    lineHeight = 26.sp,
                    modifier = Modifier.ailaAppear(1)
                )
            }

            // --- Analisi di AILA Assistant ---------------------------------------------------------------
            item {
                AilaCard(containerColor = AppTheme.TintSlate, modifier = Modifier.ailaAppear(2)) {
                    Column(modifier = Modifier.padding(AppTheme.Space16)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AilaAssistantBadge(text = "Analisi AILA Assistant")
                            // Rianalizza: senza questo, una circolare già classificata restava
                            // con il risultato vecchio per sempre — impossibile riprovare dopo
                            // aver messo la chiave AI o corretto il prompt.
                            if (isClassifying && onStopAnalysis != null) {
                                StopAnalysisButton(onClick = onStopAnalysis)
                            }
                            if (onReanalyze != null && !isClassifying) {
                                AilaSecondaryButton(
                                    text = if (classification == null) "Analizza" else "Rianalizza",
                                    onClick = onReanalyze,
                                    compact = true,
                                    icon = { tint -> AppIcons.Sparkle(modifier = Modifier.size(13.dp), color = tint) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(AppTheme.Space8))
                        if (isClassifying) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = AppTheme.PrimaryBlue
                                )
                                Spacer(modifier = Modifier.width(AppTheme.Space8))
                                Text(
                                    text = when {
                                        isQueued -> "In coda: parte appena finisce l'analisi in corso."
                                        analysisOnDevice -> "Analisi del documento in corso sul dispositivo…"
                                        else -> "Analisi del documento in corso con Gemini…"
                                    },
                                    fontSize = 13.sp,
                                    color = AppTheme.TextMuted
                                )
                            }
                        } else {
                            Text(
                                text = classification?.personalSummary
                                    ?: if (isAwaitingServer) {
                                        "Circolare lunga: l'AI del telefono ne leggerebbe solo le " +
                                            "prime pagine. Il riassunto completo arriva dal server " +
                                            "con Gemini appena pronto. Se non vuoi aspettare, " +
                                            "tocca \"Analizza\"."
                                    } else {
                                        "Nessuna analisi disponibile per questa circolare."
                                    },
                                fontSize = 13.sp,
                                color = AppTheme.TextMuted,
                                lineHeight = 19.sp
                            )
                            // Questa analisi può arrivare dalla cache condivisa sul server, cioè
                            // prodotta da un altro utente: mostrare il modello che l'ha fatta
                            // permette di giudicarne l'affidabilità senza doverla rifare per
                            // controllare (es. un'euristica di riserva conviene rianalizzarla).
                            classification?.let {
                                Spacer(modifier = Modifier.height(AppTheme.Space4))
                                Text(
                                    text = "Analisi a cura di: ${it.modelLabel}",
                                    fontSize = 11.sp,
                                    color = AppTheme.TextMuted
                                )
                            }
                        }

                        // --- Scadenze riconosciute ---------------------------------------
                        val deadlines = classification?.detectedDeadlines.orEmpty()
                        if (deadlines.isNotEmpty() && onCreateCalendarEvent != null) {
                            Spacer(modifier = Modifier.height(AppTheme.Space16))
                            val allInCalendar = deadlines.all {
                                CalendarDuplicates.findExisting(it, calendarEvents) != null
                            }
                            Text(
                                text = when {
                                    allInCalendar && deadlines.size == 1 -> "Scadenza gia' in calendario"
                                    allInCalendar -> "Scadenze gia' in calendario"
                                    deadlines.size == 1 -> "Ho trovato una scadenza"
                                    else -> "Ho trovato ${deadlines.size} scadenze"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.TextDark
                            )
                            Spacer(modifier = Modifier.height(AppTheme.Space8))
                            deadlines.forEach { deadline ->
                                val key = deadline.dueDate + "|" + deadline.title
                                ProposedDeadlineRow(
                                    deadline = deadline,
                                    outcome = actionOutcomes[key],
                                    isAdding = actionInFlight == key,
                                    existingEvent = CalendarDuplicates.findExisting(deadline, calendarEvents),
                                    onAdd = {
                                        if (actionInFlight == null && actionOutcomes[key] == null) {
                                            actionInFlight = key
                                            scope.launch {
                                                actionOutcomes[key] = onCreateCalendarEvent(deadline)
                                                actionInFlight = null
                                            }
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(AppTheme.Space8))
                            }
                        }
                    }
                }
            }

            // --- Allegati esterni -----------------------------------------------------------
            // Solo quelli che NON sono PDF in cache (colonna "Allegati" di Spaggiari con un link
            // verso un'altra pagina del sito, es. "Allegati" → pagina informazioni famiglie):
            // gli allegati PDF sono già renderizzati in coda al documento qui sotto, un secondo
            // link alla stessa cosa sarebbe ridondante.
            val externalAttachments = circular.attachments.filter { !it.isPdf }
            if (externalAttachments.isNotEmpty()) {
                item {
                    Text(
                        text = if (externalAttachments.size == 1) "Allegato" else "Allegati",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextDark,
                        modifier = Modifier.padding(top = AppTheme.Space8)
                    )
                }
                itemsIndexed(externalAttachments) { index, attachment ->
                    AilaCard(
                        modifier = Modifier.ailaAppear(index),
                        onClick = { onOpenAttachmentClick(attachment) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(AppTheme.Space16),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AilaIconTile(tint = AppTheme.TintSlate, size = 36.dp) {
                                AppIcons.Document(modifier = Modifier.size(16.dp), color = AppTheme.PrimaryBlue)
                            }
                            Spacer(modifier = Modifier.width(AppTheme.Space12))
                            Text(
                                text = attachment.label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.TextDark,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // --- Documento ----------------------------------------------------------------
            // Include le pagine di eventuali allegati PDF, disegnate in coda alle pagine del
            // documento principale (vedi [DocumentSection] e la funzione renderInto sopra).
            val totalPages = sections.sumOf { it.pages.size }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = AppTheme.Space8),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Documento",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextDark
                    )
                    if (totalPages > 0) {
                        Text(
                            text = if (totalPages == 1) "1 pagina" else "$totalPages pagine",
                            fontSize = 12.sp,
                            color = AppTheme.TextFaint
                        )
                    }
                }
            }

            if (isRenderingPdf) {
                item {
                    AilaCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.Space48),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = AppTheme.PrimaryBlue)
                            Spacer(modifier = Modifier.height(AppTheme.Space16))
                            Text(
                                text = "Preparazione del documento…",
                                fontSize = 13.sp,
                                color = AppTheme.TextMuted
                            )
                        }
                    }
                }
            } else if (totalPages > 0) {
                sections.forEachIndexed { sectionIndex, section ->
                    if (section.pages.isEmpty()) return@forEachIndexed
                    // Intestazione solo per gli allegati (label non nulla): il documento
                    // principale non ne ha bisogno, è già introdotto dal titolo "Documento".
                    if (section.label != null) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = AppTheme.Space8),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcons.Document(modifier = Modifier.size(14.dp), color = AppTheme.TextMuted)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Allegato: ${section.label}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.TextMuted
                                )
                            }
                        }
                    }
                    itemsIndexed(section.pages) { index, page ->
                        AilaCard(modifier = Modifier.ailaAppear(sectionIndex + index)) {
                            Column {
                                Image(
                                    bitmap = page,
                                    contentDescription = "Pagina ${index + 1}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(AppTheme.CardCornerRadius))
                                        .then(
                                            if (AppTheme.isDarkMode) {
                                                Modifier.background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.15f))
                                            } else Modifier
                                        )
                                )
                                Text(
                                    text = "Pagina ${index + 1} di ${section.pages.size}",
                                    fontSize = 11.sp,
                                    color = AppTheme.TextFaint,
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(vertical = AppTheme.Space8)
                                )
                            }
                        }
                    }
                }
            } else {
                item {
                    // Nessuna anteprima: PDF protetto da password o corrotto (su entrambe le piattaforme).
                    AilaCard {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(AppTheme.Space24),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AilaIconTile(tint = AppTheme.TintRed, size = 68.dp) {
                                AppIcons.Document(modifier = Modifier.size(30.dp), color = AppTheme.TintRedInk)
                            }
                            Spacer(modifier = Modifier.height(AppTheme.Space12))
                            Text(
                                text = "Anteprima non disponibile",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.TextDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pdfError ?: "Apri il documento nel visualizzatore del telefono.",
                                fontSize = 13.sp,
                                color = AppTheme.TextMuted,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(AppTheme.Space16))
                            AilaPrimaryButton(text = "Apri il PDF", onClick = onDownloadPdfClick)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Un gruppo di pagine renderizzate nello stesso visualizzatore: [label] `null` per il documento
 * principale, oppure il nome dell'allegato (es. "Allegato a") per le pagine aggiunte in coda.
 */
private data class DocumentSection(val label: String?, val pages: List<ImageBitmap>)

/**
 * Una scadenza riconosciuta dall'AI, con il tasto per metterla in calendario.
 *
 * La data si mostra in giorno/mese/anno perché il modello la produce in ISO (YYYY-MM-DD, il
 * formato che il backend vuole) ma nessuno legge le date così.
 */
@Composable
private fun ProposedDeadlineRow(
    deadline: ExtractedDeadline,
    outcome: String?,
    isAdding: Boolean,
    /** L'evento che corrisponde gia' in calendario, se c'e': al posto del tasto si dice che c'e'. */
    existingEvent: CalendarEvent?,
    onAdd: () -> Unit
) {
    val shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppTheme.SurfaceWhite)
            .padding(AppTheme.Space12)
    ) {
        Text(
            text = deadline.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextDark,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = buildString {
                append(readableDate(deadline.dueDate))
                deadline.time?.let { append(" alle ").append(it) }
                append(" \u00B7 ")
                append(readableCategory(deadline.category))
            },
            fontSize = 11.sp,
            color = AppTheme.TextMuted
        )
        Spacer(modifier = Modifier.height(AppTheme.Space8))
        if (outcome != null) {
            Text(
                text = outcome,
                fontSize = 11.sp,
                color = AppTheme.TextMuted,
                lineHeight = 15.sp
            )
        } else if (existingEvent != null) {
            Text(
                text = "Gia' in calendario: ${existingEvent.title}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TintGreenInk,
                lineHeight = 15.sp
            )
        } else {
            AilaSecondaryButton(
                text = if (isAdding) "Aggiungo\u2026" else "Aggiungi al calendario",
                onClick = onAdd,
                compact = true,
                icon = { tint -> AppIcons.Sparkle(modifier = Modifier.size(13.dp), color = tint) }
            )
        }
    }
}

/** "2026-10-20" diventa "20/10/2026". Se non è una data ISO si lascia com'è. */
private fun readableDate(isoDate: String): String {
    val parts = isoDate.split("-")
    return if (parts.size == 3) parts[2] + "/" + parts[1] + "/" + parts[0] else isoDate
}

/** Le categorie del backend sono maiuscole con underscore: qui si mostrano leggibili. */
private fun readableCategory(category: String): String = when (category.uppercase()) {
    "VERIFICA" -> "Verifica"
    "INTERROGAZIONE" -> "Interrogazione"
    "PAGAMENTO" -> "Pagamento"
    "USCITA_DIDATTICA" -> "Uscita didattica"
    "AVVISO" -> "Avviso"
    else -> "Altro"
}

/**
 * Il tasto per fermare l'analisi: un quadrato con dentro il simbolo di stop, come nei lettori
 * multimediali. Si ferma solo l'analisi su questo telefono; se nel frattempo un compagno o il
 * server finiscono il riassunto, compare quello.
 */
@Composable
private fun StopAnalysisButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(AppTheme.TintRed)
            .clickable(onClickLabel = "Ferma l'analisi", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppTheme.TintRedInk)
        )
    }
}
