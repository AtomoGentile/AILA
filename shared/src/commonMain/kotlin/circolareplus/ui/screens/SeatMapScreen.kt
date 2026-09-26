package circolareplus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.unit.sp
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
    onExportPdf: () -> Unit = {}
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
            subtitle = "Layout 2D orientato rispetto alla Cattedra",
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12),
            contentPadding = PaddingValues(
                start = AppTheme.Space16,
                end = AppTheme.Space16,
                top = AppTheme.Space16,
                bottom = AppTheme.Space32
            ),
            state = gridState,
            modifier = Modifier.fillMaxSize()
        ) {
        fullRow {

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
                placeholder = { Text("Cerca un compagno...", fontSize = 13.sp, maxLines = 1) },
                leadingIcon = { AppIcons.Search(modifier = Modifier.size(18.dp), color = AppTheme.TextMuted) },
                trailingIcon = {
                    // Mentre si scrive c'è la X per svuotare; a campo vuoto, "Il mio posto".
                    AnimatedContent(
                        targetState = searchQuery.isNotEmpty(),
                        transitionSpec = {
                            (fadeIn(tween(160)) + scaleIn(tween(160), initialScale = 0.85f)) togetherWith
                                (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.85f))
                        },
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
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180))
            ) {
                Row(
                    modifier = Modifier.padding(top = AppTheme.Space8),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcons.Locate(
                        modifier = Modifier.size(14.dp),
                        color = if (found) AppTheme.TintAmberInk else AppTheme.TextFaint
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = lastStatus[0],
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (found) AppTheme.TextDark else AppTheme.TextMuted
                    )
                }
            }
        }
        }

        // Pannello Admin per il Rappresentante (Slider & Finestra Votazione)
        if (isRepresentative) {
        fullRow {
            AilaCard(containerColor = AppTheme.TintSlate, modifier = Modifier.ailaAppear(1)) {
                // animateContentSize: quando arriva il conteggio dei voti il pannello cresce
                // morbido invece di spingere giu' la mappa di scatto.
                Column(modifier = Modifier.animateContentSize().padding(AppTheme.Space12)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcons.Sliders(modifier = Modifier.size(16.dp), color = AppTheme.TextDark)
                        Spacer(modifier = Modifier.width(AppTheme.Space8))
                        Text(
                            text = "Pannello Rappresentante",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
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
                            animationSpec = tween(250),
                            label = "prefWindowColor"
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            AilaDot(color = windowColor)
                            Spacer(modifier = Modifier.width(6.dp))
                            AnimatedContent(
                                targetState = isPreferencesOpen,
                                transitionSpec = {
                                    (fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 2 }) togetherWith
                                        (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 2 })
                                },
                                label = "prefWindowLabel"
                            ) { open ->
                                Text(
                                    text = if (open) "Preferenze: APERTE" else "Preferenze: CHIUSE",
                                    fontSize = 12.sp,
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)
                    ) {
                        SeatsPerDeskOption(
                            label = "Coppie (2)",
                            isSelected = seatsPerDesk == SeatMapOptimizer.SEATS_PER_DESK_PAIR,
                            onClick = { seatsPerDesk = SeatMapOptimizer.SEATS_PER_DESK_PAIR }
                        )
                        SeatsPerDeskOption(
                            label = "Trii (3)",
                            isSelected = seatsPerDesk == SeatMapOptimizer.SEATS_PER_DESK_TRIO,
                            onClick = { seatsPerDesk = SeatMapOptimizer.SEATS_PER_DESK_TRIO }
                        )
                    }

                    Spacer(modifier = Modifier.height(AppTheme.Space8))

                    AilaPrimaryButton(
                        text = "Calcola 3 proposte",
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
        }

        // Cattedra & Lavagna
        fullRow {
            Box(
                modifier = Modifier
                    .ailaAppear(2)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                    .background(AppTheme.HeroGradient)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LAVAGNA & CATTEDRA",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
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

/**
 * "Il mio posto", dentro la barra di ricerca della mappa: pillola che si accende (gradiente del
 * brand) quando il proprio banco e' evidenziato e si spegne al secondo tocco.
 */
@Composable
private fun MySeatPill(active: Boolean, onClick: () -> Unit) {
    val ink by animateColorAsState(
        if (active) Color.White else AppTheme.TintBlueInk, tween(200), label = "mySeatInk"
    )
    val fill by animateFloatAsState(if (active) 1f else 0f, tween(220), label = "mySeatFill")
    Row(
        modifier = Modifier
            .padding(end = 6.dp)
            .ailaPressable(pressedScale = 0.92f, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(AppTheme.TintBlue)
            .drawBehind {
                // Il gradiente sfuma dentro sopra il fondo chiaro invece di scattare.
                drawRect(brush = AppTheme.PrimaryGradient, alpha = fill)
            }
            .semantics { contentDescription = if (active) "Nascondi il mio posto" else "Trova il mio posto" }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcons.Chair(modifier = Modifier.size(15.dp), color = ink)
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "Il mio posto", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
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
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "prefProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(if (progress.allVoted) AppTheme.TintGreen else AppTheme.SurfaceWhite)
            .border(
                1.dp,
                if (progress.allVoted) AppTheme.PollGreen else AppTheme.Hairline,
                RoundedCornerShape(AppTheme.SmallElementRadius)
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
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (progress.allVoted) AppTheme.TintGreenInk else AppTheme.TextDark
            )
            Text(
                text = if (progress.allVoted) "$voted/$total" else "mancano ${total - voted}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (progress.allVoted) AppTheme.TintGreenInk else AppTheme.TextMuted
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(AppTheme.TintSlate)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(if (progress.allVoted) AppTheme.PollGreen else AppTheme.PrimaryBlue)
            )
        }
        if (!progress.allVoted && progress.pending.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Non hanno ancora votato: " +
                    progress.pending.joinToString(", ") { "${it.firstName} ${it.lastName.take(1)}." },
                fontSize = 11.sp,
                color = AppTheme.TextMuted,
                lineHeight = 15.sp
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
            Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppTheme.TextDark)
            Text(
                // Era `(value * 10).toInt() / 10f`: il troncamento faceva sparire i valori come
                // 1.2x e 1.4x, che in virgola mobile arrivano come 11.999998 e venivano tagliati
                // a 1.1x. Con l'arrotondamento il numero segue davvero il cursore.
                text = "${kotlin.math.round(value * 10) / 10f}x",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.PrimaryBlue
            )
        }
        Text(text = help, fontSize = 11.sp, color = AppTheme.TextMuted, lineHeight = 15.sp)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.5f..1.5f,
            steps = 9
        )
    }
}

/** Una delle due opzioni "Coppie (2)" / "Trii (3)" per i posti per banco. */
@Composable
private fun RowScope.SeatsPerDeskOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    // Colori animati e leggera pressione: prima la selezione cambiava di colpo.
    val bg by animateColorAsState(
        if (isSelected) AppTheme.PrimaryBlue else AppTheme.SurfaceWhite, tween(200), label = "seatsBg"
    )
    val stroke by animateColorAsState(
        if (isSelected) AppTheme.PrimaryBlue else AppTheme.Hairline, tween(200), label = "seatsStroke"
    )
    val ink by animateColorAsState(
        if (isSelected) Color.White else AppTheme.TextDark, tween(200), label = "seatsInk"
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .ailaPressable(pressedScale = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(bg)
            .border(1.dp, stroke, RoundedCornerShape(AppTheme.SmallElementRadius))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = ink
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
