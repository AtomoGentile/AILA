package circolareplus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import circolareplus.algorithms.DeskAssignment
import circolareplus.algorithms.OptimizerWeights
import circolareplus.algorithms.SeatMapOptimizer
import circolareplus.design.AilaIconButton
import circolareplus.design.AilaCard
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaDot
import circolareplus.design.ailaAppear
import circolareplus.design.ailaFieldColors
import circolareplus.design.ailaPressable
import circolareplus.design.AilaScreenHeader
import circolareplus.design.AilaSegmentedTabs
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.domain.model.User
import kotlinx.coroutines.launch

@Composable
fun SeatMapScreen(
    currentUserId: String,
    isRepresentative: Boolean,
    assignments: List<DeskAssignment>,
    studentsMap: Map<String, User>,
    isPreferencesOpen: Boolean,
    /** Quanti hanno votato le preferenze; null finche' non e' stato caricato. */
    preferencesProgress: circolareplus.data.remote.dto.PreferencesProgressDto? = null,
    onTogglePreferencesWindow: (Boolean) -> Unit = {},
    onGenerateProposals: (OptimizerWeights, seatsPerDesk: Int) -> Unit = { _, _ -> },
    isExportingPdf: Boolean = false,
    onExportPdf: () -> Unit = {},
    /** Proposte in calcolo: il pulsante mostra l'attesa e non si puo' ripremere. */
    isGeneratingProposals: Boolean = false
) {
    var searchQuery by remember { mutableStateOf("") }
    // Chi evidenziare nella mappa: "Il mio posto" o il compagno cercato. Prima il tasto era solo
    // un'icona di localizzazione (sembrava chiedere il GPS) e la ricerca non faceva nulla.
    var focusedStudentId by remember { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    // Il compagno cercato: basta l'inizio del nome o del cognome (maiuscole indifferenti).
    val searchMatch: User? = remember(searchQuery, studentsMap, assignments) {
        val q = searchQuery.trim().lowercase()
        if (q.length < 2) null else {
            val seated = assignments.flatMap { listOfNotNull(it.studentAId, it.studentBId, it.studentCId) }.toSet()
            studentsMap.values
                .filter { it.id in seated }
                .firstOrNull { u ->
                    val full = "${u.firstName} ${u.lastName}".lowercase()
                    full.startsWith(q) || u.lastName.lowercase().startsWith(q) || full.contains(" $q")
                }
        }
    }
    val highlightedId = searchMatch?.id ?: focusedStudentId
    val highlightedDeskIndex = remember(highlightedId, assignments) {
        if (highlightedId == null) -1 else assignments.indexOfFirst {
            it.studentAId == highlightedId || it.studentBId == highlightedId || it.studentCId == highlightedId
        }
    }
    // I banchi sono gli ultimi elementi della griglia: l'indice assoluto si ricava dal totale.
    fun scrollToDesk(deskIndex: Int) {
        if (deskIndex < 0) return
        coroutineScope.launch {
            val total = gridState.layoutInfo.totalItemsCount
            val target = (total - assignments.size + deskIndex).coerceAtLeast(0)
            gridState.animateScrollToItem(target)
        }
    }
    LaunchedEffect(searchMatch?.id) {
        if (searchMatch != null) scrollToDesk(highlightedDeskIndex)
    }

    // Pesi slider per Rappresentante (0.5x - 1.5x)
    var wSocial by remember { mutableStateOf(1.0f) }
    var wDiscipline by remember { mutableStateOf(1.0f) }
    var wDidactic by remember { mutableStateOf(1.0f) }
    // Banchi da coppia (2) o da trio (3): stesso algoritmo, vedi SeatMapOptimizer.optimize.
    var seatsPerDesk by remember { mutableStateOf(SeatMapOptimizer.SEATS_PER_DESK_PAIR) }

    // Una disposizione pubblicata usa banchi da trio se un banco ha capienza 3 (campo `seats`,
    // salvato nel JSON) o, per le mappe vecchie senza il campo, un terzo occupante.
    val hasTrioDesks = remember(assignments) { assignments.any { it.seats >= 3 || it.studentCId != null } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
    ) {
        // Intestazione chiara comune (design AILA), con l'azione rapida a destra.
        AilaScreenHeader(
            title = "Mappa Posti",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                    // Esporta la disposizione pubblicata in PDF (disegno del layout, non solo
                    // testo): utile a chiunque veda la mappa, non solo al Rappresentante.
                    if (assignments.isNotEmpty()) {
                        AilaIconButton(
                            contentDescription = if (isExportingPdf) "Sto generando il PDF" else "Esporta la mappa in PDF",
                            onClick = onExportPdf,
                            enabled = !isExportingPdf
                        ) { tint ->
                            AppIcons.Download(modifier = Modifier.size(19.dp), color = tint)
                        }
                    }
                }
            }
        )

        // Tutto dentro un'unica griglia scorrevole: prima intestazione, ricerca, pannello e
        // cattedra stavano in una Column fissa e solo i banchi scorrevano, quindi con il pannello
        // del rappresentante aperto il fondo della schermata veniva tagliato via.
        val searchBlock: @Composable () -> Unit = {

        // Ricerca compagno: evidenzia il suo banco e ci scorre sopra. "Il mio posto" sta dentro
        // la barra, a destra: prima era un pulsante grande su una riga a sé, che occupava spazio
        // e sembrava staccato dalla ricerca pur facendo la stessa cosa (evidenziare un banco).
        val isShowingMine = focusedStudentId == currentUserId && searchQuery.isEmpty()
        val onToggleMySeat: () -> Unit = {
            searchQuery = ""
            if (focusedStudentId == currentUserId) {
                focusedStudentId = null
            } else {
                focusedStudentId = currentUserId
                scrollToDesk(assignments.indexOfFirst {
                    it.studentAId == currentUserId || it.studentBId == currentUserId || it.studentCId == currentUserId
                })
            }
        }
        Column(modifier = Modifier.ailaAppear(0)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    if (it.isNotEmpty()) focusedStudentId = null
                },
                placeholder = { Text("Cerca un compagno...", style = MaterialTheme.typography.bodyMedium, maxLines = 1) },
                leadingIcon = { AppIcons.Search(modifier = Modifier.size(18.dp), color = AppTheme.TextMuted) },
                trailingIcon = {
                    // Mentre si scrive c'è la X per svuotare; a campo vuoto, "Il mio posto".
                    AnimatedContent(
                        targetState = searchQuery.isNotEmpty(),
                        // Prima il contenitore si ridimensionava (pillola larga -> X piccola)
                        // tagliando i due elementi a meta' della dissolvenza. Ora non si ritaglia,
                        // la misura segue una molla e i due si scambiano con scala + dissolvenza.
                        transitionSpec = {
                            (fadeIn(circolareplus.design.ailaFadeSpec(180, delayMillis = 60)) +
                                scaleIn(circolareplus.design.ailaSpatialSpring(), initialScale = 0.6f)) togetherWith
                                (fadeOut(circolareplus.design.ailaFadeSpec(90)) + scaleOut(circolareplus.design.ailaMoveSpec(120), targetScale = 0.6f)) using
                                androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                                    circolareplus.design.ailaSpatialSpring()
                                }
                        },
                        contentAlignment = Alignment.CenterEnd,
                        label = "searchTrailing"
                    ) { typing ->
                        when {
                            typing -> Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .ailaPressable(pressedScale = 0.88f) { searchQuery = "" },
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcons.Close(modifier = Modifier.size(16.dp), color = AppTheme.TextMuted)
                            }
                            assignments.isNotEmpty() -> MySeatPill(
                                active = isShowingMine,
                                onClick = onToggleMySeat
                            )
                            else -> Spacer(modifier = Modifier.size(1.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(AppTheme.CardCornerRadius),
                colors = ailaFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            val status = when {
                searchQuery.trim().length >= 2 && searchMatch == null -> "Nessun compagno trovato"
                highlightedId != null && highlightedDeskIndex < 0 -> "Nessun posto assegnato"
                highlightedId != null -> {
                    val d = assignments[highlightedDeskIndex]
                    val who = if (highlightedId == currentUserId) "Sei" else "${studentsMap[highlightedId]?.firstName ?: "Il compagno"} è"
                    "$who in fila ${d.row + 1}, colonna ${d.column + 1}"
                }
                else -> null
            }
            // Tiene l'ultimo testo mostrato: durante l'animazione d'uscita status e' gia' null, e
            // senza questo la riga si svuoterebbe prima di chiudersi.
            val lastStatus = remember { arrayOf("") }
            if (status != null) lastStatus[0] = status
            val found = !lastStatus[0].startsWith("Nessun")
            // L'esito compare e scompare con un'animazione invece di spingere di colpo la mappa.
            AnimatedVisibility(
                visible = status != null,
                // Si apre con la molla dello stile e il testo compare quando c'e' gia' spazio,
                // invece di sovrapporsi all'apertura.
                enter = expandVertically(circolareplus.design.ailaSpatialSpring()) +
                    fadeIn(circolareplus.design.ailaFadeSpec(200, delayMillis = 80)),
                exit = fadeOut(circolareplus.design.ailaFadeSpec(100)) + shrinkVertically(circolareplus.design.ailaSpatialSpring())
            ) {
                Row(
                    modifier = Modifier.padding(top = AppTheme.Space8),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcons.Locate(
                        modifier = Modifier.size(14.dp),
                        color = if (found) AppTheme.TintAmberInk else AppTheme.TextFaint
                    )
                    Spacer(modifier = Modifier.width(AppTheme.Space8))
                    // Cambiando compagno cercato il testo scorre al nuovo invece di cambiare di colpo.
                    AnimatedContent(
                        targetState = lastStatus[0],
                        transitionSpec = {
                            (fadeIn(circolareplus.design.ailaFadeSpec(180)) + slideInVertically(circolareplus.design.ailaSpatialSpring()) { it / 2 }) togetherWith
                                (fadeOut(circolareplus.design.ailaFadeSpec(100)) + slideOutVertically(circolareplus.design.ailaMoveSpec(140)) { -it / 2 })
                        },
                        label = "seatStatusText"
                    ) { text ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (!text.startsWith("Nessun")) AppTheme.TextDark else AppTheme.TextMuted
                        )
                    }
                }
            }
        }
        }
        val adminPanel: @Composable () -> Unit = {
            AilaCard(containerColor = AppTheme.TintSlate, modifier = Modifier.ailaAppear(1)) {
                // animateContentSize: quando arriva il conteggio dei voti il pannello cresce
                // morbido invece di spingere giu' la mappa di scatto.
                Column(modifier = Modifier.animateContentSize().padding(AppTheme.Space12)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcons.Sliders(modifier = Modifier.size(16.dp), color = AppTheme.TextDark)
                        Spacer(modifier = Modifier.width(AppTheme.Space8))
                        Text(
                            text = "Pannello Rappresentante",
                            style = MaterialTheme.typography.titleSmall,
                            color = AppTheme.TextDark
                        )
                    }

                    Spacer(modifier = Modifier.height(AppTheme.Space8))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val windowColor by animateColorAsState(
                            targetValue = if (isPreferencesOpen) AppTheme.PollGreen else AppTheme.PollDarkRed,
                            animationSpec = circolareplus.design.ailaColorSpec(),
                            label = "prefWindowColor"
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            AilaDot(color = windowColor)
                            Spacer(modifier = Modifier.width(AppTheme.Space8))
                            AnimatedContent(
                                targetState = isPreferencesOpen,
                                transitionSpec = {
                                    (fadeIn(circolareplus.design.ailaFadeSpec(200)) + slideInVertically(circolareplus.design.ailaMoveSpec(220)) { it / 2 }) togetherWith
                                        (fadeOut(circolareplus.design.ailaFadeSpec(120)) + slideOutVertically(circolareplus.design.ailaMoveSpec(160)) { -it / 2 })
                                },
                                label = "prefWindowLabel"
                            ) { open ->
                                Text(
                                    text = if (open) "Preferenze: APERTE" else "Preferenze: CHIUSE",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = windowColor
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(AppTheme.Space8))
                        AilaPrimaryButton(
                            text = if (isPreferencesOpen) "Chiudi votazione" else "Apri votazione",
                            onClick = { onTogglePreferencesWindow(!isPreferencesOpen) },
                            compact = true
                        )
                    }

                    if (preferencesProgress != null && preferencesProgress.totalStudents > 0) {
                        Spacer(modifier = Modifier.height(AppTheme.Space8))
                        PreferencesProgressBlock(progress = preferencesProgress)
                    }

                    Spacer(modifier = Modifier.height(AppTheme.Space12))

                    // Pesi dell'algoritmo. **C'era un solo slider per tre pesi**: quello del peso
                    // sociale. Gli altri due comparivano nell'etichetta ma non erano regolabili in
                    // nessun modo — restavano a 1.0x qualunque cosa si facesse. Ora ognuno ha il
                    // suo cursore, e sotto c'è scritto cosa cambia.
                    WeightSlider(
                        label = "Preferenze sociali",
                        help = "Quanto contano le simpatie e le antipatie dichiarate dai compagni",
                        value = wSocial,
                        onValueChange = { wSocial = it }
                    )
                    WeightSlider(
                        label = "Disciplina",
                        help = "Quanto pesa separare chi fa chiasso",
                        value = wDiscipline,
                        onValueChange = { wDiscipline = it }
                    )
                    WeightSlider(
                        label = "Didattica",
                        help = "Quanto pesa affiancare chi va bene a chi fa più fatica",
                        value = wDidactic,
                        onValueChange = { wDidactic = it }
                    )

                    Spacer(modifier = Modifier.height(AppTheme.Space8))

                    // Banchi da coppia o da trio: stesso algoritmo (stesse funzioni di
                    // punteggio, applicate a tutte le coppie del banco), cambia solo quante
                    // persone ci mette insieme.
                    Text(
                        text = "Posti per banco",
                        style = MaterialTheme.typography.labelLarge,
                        color = AppTheme.TextDark
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space4))
                    // Selettore a pillole (connected button group in Material, segmented
                    // control in Glass) invece di due riquadri separati.
                    val seatOptions = listOf(SeatMapOptimizer.SEATS_PER_DESK_PAIR, SeatMapOptimizer.SEATS_PER_DESK_TRIO)
                    AilaSegmentedTabs(
                        labels = listOf("Coppie (2)", "Trii (3)"),
                        selectedIndex = seatOptions.indexOf(seatsPerDesk).coerceAtLeast(0),
                        onSelect = { index -> seatsPerDesk = seatOptions[index] },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(AppTheme.Space8))

                    AilaPrimaryButton(
                        text = if (isGeneratingProposals) "Calcolo in corso…" else "Calcola 3 proposte",
                        enabled = !isGeneratingProposals,
                        // Mentre calcola: la forma che cambia (Material) o i puntini (Glass),
                        // invece di un pulsante che non reagisce per qualche secondo.
                        icon = if (isGeneratingProposals) {
                            { _ ->
                                if (AppTheme.isGlass) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = AppTheme.PrimaryBlue
                                    )
                                } else {
                                    circolareplus.design.AilaMorphingLoader(size = 18.dp, color = AppTheme.PrimaryBlue)
                                }
                            }
                        } else null,
                        onClick = {
                            onGenerateProposals(
                                OptimizerWeights(
                                    wSocial = wSocial.toDouble(),
                                    wDiscipline = wDiscipline.toDouble(),
                                    wDidactic = wDidactic.toDouble()
                                ),
                                seatsPerDesk
                            )
                        },
                        fillMaxWidth = true
                    )
                }
            }
        }
        // Tablet e iPad larghi: ricerca e pannello del Rappresentante in una colonna a sinistra,
        // la mappa dell'aula a destra, sempre in vista mentre si regolano i pesi o si cerca.
        val wide = circolareplus.design.LocalWideLayout.current
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
        if (wide) {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = AppTheme.Space16,
                        top = AppTheme.Space16,
                        bottom = AppTheme.Space32 + circolareplus.design.LocalBottomBarPadding.current
                    ),
                verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)
            ) {
                searchBlock()
                if (isRepresentative) adminPanel()
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12),
            contentPadding = PaddingValues(
                start = AppTheme.Space16,
                end = AppTheme.Space16,
                top = AppTheme.Space16,
                bottom = AppTheme.Space32 + circolareplus.design.LocalBottomBarPadding.current
            ),
            state = gridState,
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
        if (!wide) {
            fullRow { searchBlock() }
            if (isRepresentative) fullRow { adminPanel() }
        }

        // Cattedra & Lavagna
        fullRow {
            Box(
                modifier = Modifier
                    .ailaAppear(2)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                    .background(AppTheme.HeroGradient)
                    .padding(vertical = AppTheme.Space12),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LAVAGNA & CATTEDRA",
                    style = MaterialTheme.typography.labelMedium,
                    color = AppTheme.OnHeroPrimary
                )
            }
        }

        // Banchi. Se non c'è ancora nessuna disposizione pubblicata la griglia restava
        // semplicemente vuota, senza spiegare perché: ora c'è uno stato vuoto esplicito.
        if (assignments.isEmpty()) {
            fullRow {
                AilaEmptyState(
                    modifier = Modifier.ailaAppear(3),
                    title = "Nessuna disposizione pubblicata",
                    message = if (isRepresentative)
                        "Apri la votazione delle preferenze, poi calcola e pubblica una delle tre proposte."
                    else
                        "Il Rappresentante non ha ancora pubblicato la disposizione dei banchi.",
                    icon = { AppIcons.Chair(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                )
            }
        }

            // I banchi entrano a cascata (prime file) e quello evidenziato "pulsa" al centro della
            // scena: prima la mappa compariva tutta di colpo e il banco trovato cambiava colore
            // e basta, facile da non notare.
            itemsIndexed(assignments) { index, desk ->
                SeatMapDeskCard(
                    modifier = Modifier.ailaAppear(3 + index),
                    desk = desk,
                    studentsMap = studentsMap,
                    showThirdSeat = hasTrioDesks,
                    focusedStudentId = highlightedId
                )
            }
        }
        }
    }
}

/**
 * "Il mio posto", dentro la barra di ricerca della mappa: pillola che si accende (gradiente del
 * brand) quando il proprio banco e' evidenziato e si spegne al secondo tocco.
 */
@Composable
private fun MySeatPill(active: Boolean, onClick: () -> Unit) {
    val ink by animateColorAsState(
        if (active) Color.White else AppTheme.TintBlueInk, circolareplus.design.ailaColorSpec(), label = "mySeatInk"
    )
    val fill by animateFloatAsState(if (active) 1f else 0f, circolareplus.design.ailaMoveSpec(220), label = "mySeatFill")
    Row(
        modifier = Modifier
            .padding(end = AppTheme.Space8)
            .ailaPressable(pressedScale = 0.92f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(AppTheme.TintBlue)
            .drawBehind {
                // Il gradiente sfuma dentro sopra il fondo chiaro invece di scattare.
                drawRect(brush = AppTheme.PrimaryGradient, alpha = fill)
            }
            .semantics { contentDescription = if (active) "Nascondi il mio posto" else "Trova il mio posto" }
            .padding(horizontal = AppTheme.Space12, vertical = AppTheme.Space8),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcons.Chair(modifier = Modifier.size(15.dp), color = ink)
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        Text(text = "Il mio posto", style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1)
    }
}

/**
 * Chi ha gia' votato le preferenze e chi no, per il Rappresentante: una barra con il conteggio e,
 * finche' manca qualcuno, i nomi da sollecitare. Quando hanno votato tutti lo dice, e' il
 * momento di calcolare le proposte.
 */
@Composable
private fun PreferencesProgressBlock(progress: circolareplus.data.remote.dto.PreferencesProgressDto) {
    val total = progress.totalStudents
    val voted = progress.votedCount.coerceIn(0, total)
    val targetFraction = if (total == 0) 0f else voted.toFloat() / total
    // La barra si riempie invece di comparire gia' piena: si vede che il conteggio e' cambiato.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val fraction by animateFloatAsState(
        targetValue = if (started) targetFraction else 0f,
        animationSpec = circolareplus.design.ailaMoveSpec(600, easing = FastOutSlowInEasing),
        label = "prefProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(
                when {
                    progress.allVoted -> AppTheme.TintGreen
                    AppTheme.isGlass -> AppTheme.FieldSurface
                    // Material: "surface container highest", pieno e senza bordo.
                    AppTheme.isDarkMode -> Color(0xFF2E3138)
                    else -> Color(0xFFE1E3EE)
                }
            )
            .then(
                if (AppTheme.isGlass || progress.allVoted) Modifier.border(
                    1.dp,
                    if (progress.allVoted) AppTheme.PollGreen else AppTheme.FieldOutline,
                    RoundedCornerShape(AppTheme.SmallElementRadius)
                ) else Modifier
            )
            .padding(AppTheme.Space12)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (progress.allVoted) "Hanno votato tutti" else "Hanno votato $voted su $total",
                style = MaterialTheme.typography.labelLarge,
                color = if (progress.allVoted) AppTheme.TintGreenInk else AppTheme.TextDark
            )
            Text(
                text = if (progress.allVoted) "$voted/$total" else "mancano ${total - voted}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (progress.allVoted) AppTheme.TintGreenInk else AppTheme.TextMuted
            )
        }
        Spacer(modifier = Modifier.height(AppTheme.Space8))
        circolareplus.design.AilaProgressBar(
            progress = fraction,
            color = if (progress.allVoted) AppTheme.PollGreen else AppTheme.PrimaryBlue
        )
        if (!progress.allVoted && progress.pending.isNotEmpty()) {
            Spacer(modifier = Modifier.height(AppTheme.Space8))
            Text(
                text = "Non hanno ancora votato: " +
                    progress.pending.joinToString(", ") { "${it.firstName} ${it.lastName.take(1)}." },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Normal,
                color = AppTheme.TextMuted
            )
        }
    }
}

/**
 * Un peso dell'algoritmo della mappa posti: nome, valore corrente e cursore da 0.5x a 1.5x.
 * Prima i tre pesi ne condividevano uno solo, quindi due su tre erano di fatto bloccati a 1.0x.
 */
@Composable
private fun WeightSlider(
    label: String,
    help: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = AppTheme.Space8)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = AppTheme.TextDark)
            Text(
                // Era `(value * 10).toInt() / 10f`: il troncamento faceva sparire i valori come
                // 1.2x e 1.4x, che in virgola mobile arrivano come 11.999998 e venivano tagliati
                // a 1.1x. Con l'arrotondamento il numero segue davvero il cursore.
                text = "${kotlin.math.round(value * 10) / 10f}x",
                style = MaterialTheme.typography.labelLarge,
                color = AppTheme.PrimaryBlue
            )
        }
        Text(text = help, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Normal, color = AppTheme.TextMuted)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.5f..1.5f,
            steps = 9
        )
    }
}


/**
 * Riga a tutta larghezza dentro la griglia dei banchi: serve per intestazioni, pannello del
 * rappresentante e cattedra, che non sono celle da tre per riga.
 */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullRow(
    content: @Composable () -> Unit
) {
    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { content() }
}
