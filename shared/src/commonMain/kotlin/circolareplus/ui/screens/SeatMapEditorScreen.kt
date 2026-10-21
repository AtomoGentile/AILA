package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import circolareplus.algorithms.DeskAssignment
import circolareplus.algorithms.SeatMapOptimizer
import circolareplus.design.AilaCard
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaGlassSurface
import circolareplus.design.ailaDeskLabel
import circolareplus.design.ailaDeskBadge
import circolareplus.domain.model.SocialPreferenceScore
import circolareplus.domain.model.User
import kotlinx.coroutines.withTimeoutOrNull

/** Riferimento a un posto selezionato nell'editor: indice del banco + indice del posto (0=A,
 * 1=B, 2=C — quest'ultimo solo per i banchi da trio). */
private data class EditableSeatRef(val deskIndex: Int, val seatIndex: Int)

/** Bordo di un banco nella griglia, in coordinate schermo: serve a individuare il banco sotto
 * il dito durante un trascinamento (long-press) fra banchi. */
private data class DeskBounds(val position: Offset, val size: androidx.compose.ui.unit.IntSize) {
    val center: Offset
        get() = Offset(position.x + size.width / 2f, position.y + size.height / 2f)
}

/**
 * Editor manuale della disposizione scelta dal Rappresentante: tap-to-swap (si seleziona un posto,
 * poi un secondo per scambiarli) con ricalcolo live del punteggio passato da fuori, pulsante di
 * ripristino alla proposta originale dell'algoritmo e pulsante di pubblicazione finale.
 *
 * Oltre al tap-to-swap dei posti, la presa di ogni banco supporta il drag: touch con pressione
 * prolungata, mouse appena inizia il movimento. Sul touch si può anche toccare la presa di origine
 * e poi quella di destinazione. Un banco si sposta su un altro banco — anche uno vuoto — e al
 * rilascio si chiama [onSwapDesks].
 */
@Composable
fun SeatMapEditorScreen(
    assignments: List<DeskAssignment>,
    studentsMap: Map<String, User>,
    socialPreferences: Map<Pair<String, String>, SocialPreferenceScore>,
    breakdown: SeatMapOptimizer.ScoreBreakdown,
    satisfaction: SeatMapOptimizer.VoteSatisfaction?,
    isPublishing: Boolean,
    onSwapSeats: (deskIndex1: Int, seatIndex1: Int, deskIndex2: Int, seatIndex2: Int) -> Unit,
    // Drag & drop di banchi interi (long-press): indice di partenza e banco su cui e' stato
    // lasciato. Pari-passo con onSwapSeats ma a grana diversa (posto / banco): il MainAppShell fa
    // editingSeatMapProposal = SeatMapOptimizer.swapDesks(...).
    onSwapDesks: (deskIndexFrom: Int, deskIndexTo: Int) -> Unit = { _, _ -> },
    onRestore: () -> Unit,
    onPublish: () -> Unit,
    // Modalità scelta per QUESTA generazione (coppia o trio), come in SeatMapProposalsScreen:
    // dedurla dai dati (nessun banco con un terzo occupante -> "coppie") faceva sparire il terzo
    // posto anche in modalità trio quando la classe è piccola o i trii vengono spezzati dai vincoli.
    seatsPerDesk: Int = SeatMapOptimizer.SEATS_PER_DESK_PAIR
) {
    var selectedSeat by remember { mutableStateOf<EditableSeatRef?>(null) }
    var selectedDeskForMove by remember { mutableStateOf<Int?>(null) }
    val (deskColumns, deskCells) = remember(assignments) { deskGridCells(assignments) }
    val gridState = rememberLazyGridState()

    // --- Stato del drag&drop dei banchi --------------------------------------------------
    // Banco in trascinamento e banco evidenziato come bersaglio: gli indici sono deskIndex
    // (posizione nella lista assignments), la stessa unita' di onSwapSeats/onSwapDesks.
    var draggingDeskIndex by remember { mutableStateOf<Int?>(null) }
    var hoveredDeskIndex by remember { mutableStateOf<Int?>(null) }
    // Bordo di ogni banco visibile, aggiornato a ogni layout (scroll compreso): durante il drag
    // il banco sotto il dito e' quello col centro piu' vicino al puntatore, fra i banchi attual-
    // mente visibili (un banco fuori schermo non e' un bersaglio sensato e il suo bordo sarebbe
    // stante: si filtrano sui banchi visibili, vedi deskIndexUnderPointer).
    val deskBounds = remember { androidx.compose.runtime.mutableStateMapOf<Int, DeskBounds>() }

    fun endDeskDrag() {
        val from = draggingDeskIndex
        val target = hoveredDeskIndex
        draggingDeskIndex = null
        hoveredDeskIndex = null
        selectedDeskForMove = null
        if (from != null && target != null && target != from) {
            onSwapDesks(from, target)
            // Lo scambio ha spostato banchi INTERI: la selezione precedente potrebbe puntare a un
            // banco ormai spostato, la si azzera.
            selectedSeat = null
        }
    }

    // --- Presa di trascinamento dei banchi ------------------------------------------------
    fun startGripDrag(from: Int) {
        selectedDeskForMove = null
        selectedSeat = null
        if (draggingDeskIndex == null) {
            draggingDeskIndex = from
            hoveredDeskIndex = null
        }
    }

    // Bersaglio dinamico: stesso helper del ramo long-press, il puntatore arriva pero' gia' in
    // coordinate root (posizione della presa + offset locale dell'evento).
    fun dragGripFrom(deskIndex: Int, pointerInRoot: Offset) {
        if (draggingDeskIndex == deskIndex) {
            hoveredDeskIndex = deskIndexUnderPointer(
                pointerInRoot = pointerInRoot,
                deskBounds = deskBounds,
                visibleCellIndexes = gridState.layoutInfo.visibleItemsInfo.map { it.index },
                cells = deskCells
            )
        }
    }

    fun endGripDrag(deskIndex: Int) {
        if (draggingDeskIndex == deskIndex) endDeskDrag()
    }

    fun tapDeskGrip(deskIndex: Int) {
        val source = selectedDeskForMove
        if (source == null) {
            selectedDeskForMove = deskIndex
            selectedSeat = null
        } else {
            selectedDeskForMove = null
            if (source != deskIndex) {
                onSwapDesks(source, deskIndex)
                selectedSeat = null
            }
        }
    }

    // Uno scambio può cambiare la composizione dei banchi: se la selezione punta a un indice
    // ormai fuori range, la si azzera invece di lasciarla puntare a un posto inesistente. Stessa
    // ragione per il drag in corso: se la lista cambia a trascinamento avviato, si resetta.
    LaunchedEffect(assignments) {
        val seat = selectedSeat
        if (seat != null && seat.deskIndex >= assignments.size) {
            selectedSeat = null
        }
        draggingDeskIndex = null
        hoveredDeskIndex = null
        selectedDeskForMove = null
    }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.BackgroundLight)) {
        AilaCard(
            containerColor = if (AppTheme.isGlass) AppTheme.CardSurface else AppTheme.SurfaceWhite,
            modifier = Modifier.fillMaxWidth().padding(AppTheme.Space16)
        ) {
            Column(modifier = Modifier.padding(AppTheme.Space12)) {
                Text(
                    text = satisfactionLabel(satisfaction),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppTheme.TextDark
                )
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                    StatChip("Sociale", breakdown.social.toInt(), Modifier.weight(1f))
                    StatChip("Didattica", breakdown.didactic.toInt(), Modifier.weight(1f))
                    StatChip("Disciplina", breakdown.discipline.toInt(), Modifier.weight(1f))
                }
                Text(
                    text = when {
                        selectedDeskForMove != null -> "Banco selezionato: tocca la presa del banco di destinazione."
                        selectedSeat == null -> "Tocca un nome per selezionarlo, poi un secondo per scambiarli."
                        else -> "Ora tocca un secondo nome per completare lo scambio."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Normal,
                    color = AppTheme.TextMuted,
                    modifier = Modifier.padding(top = AppTheme.Space8)
                )
                Text(
                    // Drag & drop dei banchi interi: pressione prolungata sulla presa su touch;
                    // un tocco breve sulla presa seleziona il banco per il movimento a due tocchi.
                    text = "Tieni premuta la presa ⠿ e trascina il banco; su computer puoi " +
                        "trascinarlo subito. Sul telefono puoi anche toccare la presa del banco " +
                        "e poi quella di destinazione.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Normal,
                    color = AppTheme.TextMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                AilaSecondaryButton(
                    text = "Ripristina Proposta Algoritmo",
                    onClick = {
                        selectedSeat = null
                        onRestore()
                    },
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                AilaPrimaryButton(
                    text = "Pubblica questa disposizione",
                    onClick = onPublish,
                    fillMaxWidth = true,
                    enabled = !isPublishing
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(deskColumns),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12),
            contentPadding = PaddingValues(
                start = AppTheme.Space16,
                end = AppTheme.Space16,
                bottom = AppTheme.Space32
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            items(count = deskCells.size) { cellIndex ->
                val cell = deskCells[cellIndex]
                if (cell == null) {
                    Spacer(modifier = Modifier)
                    return@items
                }
                val deskIndex = cell.index
                val desk = cell.value
                // Banco senza occupanti (v1.1.6): slot "ghost" — visivamente distinto e trascinabile
                // come un banco pieno, cosi' lo spazio vuoto della griglia si puo' spostare a mano.
                val isVacant = desk.isVacantDesk
                val isDragged = draggingDeskIndex == deskIndex
                val isHovered = hoveredDeskIndex == deskIndex && draggingDeskIndex != null
                val sA = desk.studentAId?.let { studentsMap[it] }
                val sB = desk.studentBId?.let { studentsMap[it] }
                val sC = desk.studentCId?.let { studentsMap[it] }
                // Qualunque coppia vietata (-2/-2) all'interno del banco lo segnala, non solo A-B:
                // un trio ha fino a tre coppie possibili (A-B, A-C, B-C).
                val occupantIds = listOfNotNull(desk.studentAId, desk.studentBId, desk.studentCId)
                val isForbidden = occupantIds.indices.any { i ->
                    ((i + 1) until occupantIds.size).any { j ->
                        SeatMapOptimizer.isForbiddenPair(occupantIds[i], occupantIds[j], socialPreferences)
                    }
                }

                val deskShape = RoundedCornerShape(if (AppTheme.isGlass) AppTheme.SmallElementRadius + 2.dp else 16.dp)
                val glass = AppTheme.isGlass
                // Layout observer: aggiorna il bordo del banco (serve sopra, per bersaglio/dito).
                val boundsModifier = Modifier.onGloballyPositioned { coords ->
                    deskBounds[deskIndex] = DeskBounds(coords.positionInRoot(), coords.size)
                }
                val dragVisualModifier = when {
                    // Card "sollevata" (v1.1.6): sopra le altre card (zIndex) e con ombra, oltre
                    // a scala/trasparenza come prima (pattern di RankingPollsScreen).
                    isDragged -> Modifier.zIndex(1f).graphicsLayer {
                        alpha = 0.55f
                        scaleX = 1.03f; scaleY = 1.03f
                        shadowElevation = 8.dp.toPx()
                        shape = deskShape
                        clip = true
                    }
                    isHovered -> Modifier.border(2.dp, AppTheme.PrimaryBlue, deskShape)
                    else -> Modifier
                }

                if (isVacant) {
                    // Banco vuoto: slot ghost (bordo sottile, niente nomi), trascinabile come un
                    // banco pieno; l'evidenziazione del bersaglio e' interna (bordo acceso). La
                    // presa sta SOPRA lo slot (VacantDeskSlot resta com'e', sta in SeatMapGrid):
                    // un Box esterno porta bounds/drag/feedback senza cambiarne il layout.
                    Box(modifier = boundsModifier.then(dragVisualModifier)) {
                        VacantDeskSlot(
                            desk = desk,
                            isDragged = isDragged,
                            isHoverTarget = isHovered
                        )
                        DeskGripHandle(
                            modifier = Modifier.align(Alignment.TopEnd),
                            dimmed = draggingDeskIndex != null,
                            onStart = { startGripDrag(deskIndex) },
                            onDrag = { pointerInRoot -> dragGripFrom(deskIndex, pointerInRoot) },
                            onEnd = { endGripDrag(deskIndex) },
                            onTap = { tapDeskGrip(deskIndex) },
                            selected = selectedDeskForMove == deskIndex
                        )
                    }
                    return@items
                }

                // Liquid Glass: banco di vetro; se la coppia e' vietata, vetro rosso col bordo pieno.
                Card(
                    shape = deskShape,
                    // Material: card tonale piena; coppia vietata = "error container".
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            glass -> androidx.compose.ui.graphics.Color.Transparent
                            isForbidden -> AppTheme.TintRed
                            else -> AppTheme.DeskFloor
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = AppTheme.CardElevation),
                    modifier = (if (glass) Modifier
                        .ailaGlassSurface(deskShape, tint = if (isForbidden) AppTheme.TintRed.copy(alpha = 0.6f) else null)
                        .then(if (isForbidden) Modifier.border(2.dp, AppTheme.PollDarkRed, deskShape) else Modifier)
                    else Modifier)
                        .then(boundsModifier)
                        .then(dragVisualModifier)
                ) {
                    Column(
                        modifier = Modifier.padding(AppTheme.Space8),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Testata: label del banco a sinistra, presa di trascinamento a destra.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Banco F${desk.row + 1}C${desk.column + 1}",
                                style = MaterialTheme.typography.ailaDeskLabel,
                                color = AppTheme.TextMuted
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            DeskGripHandle(
                                dimmed = draggingDeskIndex != null,
                                onStart = { startGripDrag(deskIndex) },
                                onDrag = { pointerInRoot -> dragGripFrom(deskIndex, pointerInRoot) },
                                onEnd = { endGripDrag(deskIndex) },
                                onTap = { tapDeskGrip(deskIndex) },
                                selected = selectedDeskForMove == deskIndex
                            )
                        }
                        if (isForbidden) {
                            Text(
                                text = "Coppia vietata",
                                style = MaterialTheme.typography.ailaDeskBadge,
                                color = AppTheme.PollDarkRed
                            )
                        }
                        Spacer(modifier = Modifier.height(AppTheme.Space4))
                        SeatSlotLabel(
                            name = sA?.firstName,
                            isSelected = selectedSeat == EditableSeatRef(deskIndex, 0),
                            onClick = {
                                if (draggingDeskIndex == null) {
                                    selectedSeat = handleSeatTap(
                                        tapped = EditableSeatRef(deskIndex, 0),
                                        current = selectedSeat,
                                        onSwap = onSwapSeats
                                    )
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = AppTheme.Hairline)
                        SeatSlotLabel(
                            name = sB?.firstName,
                            isSelected = selectedSeat == EditableSeatRef(deskIndex, 1),
                            onClick = {
                                if (draggingDeskIndex == null) {
                                    selectedSeat = handleSeatTap(
                                        tapped = EditableSeatRef(deskIndex, 1),
                                        current = selectedSeat,
                                        onSwap = onSwapSeats
                                    )
                                }
                            }
                        )
                        if (desk.hasThirdSeat) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = AppTheme.Hairline)
                            SeatSlotLabel(
                                name = sC?.firstName,
                                isSelected = selectedSeat == EditableSeatRef(deskIndex, 2),
                                onClick = {
                                    if (draggingDeskIndex == null) {
                                        selectedSeat = handleSeatTap(
                                            tapped = EditableSeatRef(deskIndex, 2),
                                            current = selectedSeat,
                                            onSwap = onSwapSeats
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Gestisce il tap su un posto: se non c'era nulla di selezionato, seleziona; se il tap ricade
 * sullo stesso posto già selezionato, deseleziona; altrimenti scambia i due posti e azzera la
 * selezione.
 */
private fun handleSeatTap(
    tapped: EditableSeatRef,
    current: EditableSeatRef?,
    onSwap: (Int, Int, Int, Int) -> Unit
): EditableSeatRef? {
    return when {
        current == null -> tapped
        current == tapped -> null
        else -> {
            onSwap(current.deskIndex, current.seatIndex, tapped.deskIndex, tapped.seatIndex)
            null
        }
    }
}

/**
 * Banco bersaglio di un trascinamento in corso: fra i banchi ATTUALMENTE VISIBILI (i loro indici
 * di cella arrivano da gridState.layoutInfo.visibleItemsInfo), quello il cui centro registrato in
 * coordinate schermo e' piu' vicino al puntatore — pattern "cella piu' vicina al pointer": non
 * serve calcolare a mano i bounds di LazyVerticalGrid (col padding il calcolo e' fragile) e la
 * dito che sfiora il bordo fra due banchi ottiene comunque un bersaglio stabile.
 * Banchi fuori schermo (bordi stanti o ignoti) non sono bersagli.
 */
private fun deskIndexUnderPointer(
    pointerInRoot: Offset,
    deskBounds: Map<Int, DeskBounds>,
    visibleCellIndexes: List<Int>,
    cells: List<DeskCell>
): Int? {
    var best: Pair<Float, Int>? = null
    for (cellIndex in visibleCellIndexes) {
        val deskIndex = cells.getOrNull(cellIndex)?.index ?: continue
        val bounds = deskBounds[deskIndex] ?: continue
        val distance = (bounds.center - pointerInRoot).getDistanceSquared()
        if (best == null || distance < best.first) best = distance to deskIndex
    }
    return best?.second
}

@Composable
private fun SeatSlotLabel(name: String?, isSelected: Boolean, onClick: () -> Unit) {
    Text(
        text = name ?: "Vuoto",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        color = if (isSelected) AppTheme.PrimaryBlue else AppTheme.TextDark,
        modifier = Modifier
            .clickable(onClick = onClick)
            // L'altezza di prima (28dp), che veniva dall'interlinea di 24sp ereditata da Material:
            // nel banco non c'e' posto per 44, ma sotto i 28 il nome diventava difficile da toccare.
            .heightIn(min = 28.dp)
            .wrapContentHeight()
            .then(
                if (isSelected) Modifier.border(1.dp, AppTheme.PrimaryBlue, RoundedCornerShape(4.dp)) else Modifier
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

/**
 * Presa di trascinamento di un banco: la griglietta di punti in alto a destra della card.
 * Su touch si attiva con una pressione prolungata, evitando che la griglia interpreti il gesto
 * come scroll; con mouse parte subito dopo la touch slop. Un tocco breve seleziona il banco,
 * così si può spostare anche con la sequenza di due tocchi sul telefono. Il bersaglio
 * si calcola nello stesso spazio dei bounds dei banchi: la posizione del dito e' quella della
 * presa in coordinate root + offset locale dell'evento, con lo stesso helper del long-press
 * (deskIndexUnderPointer, "centro piu' vicino": il bersaglio resta comunque il BANCO).
 * La presa e' soppressa (spenta) mentre un altro trascinamento e' in corso.
 */
@Composable
private fun DeskGripHandle(
    modifier: Modifier = Modifier,
    dimmed: Boolean,
    onStart: () -> Unit,
    onDrag: (pointerInRoot: Offset) -> Unit,
    onEnd: () -> Unit,
    onTap: () -> Unit,
    selected: Boolean
) {
    // Posizione della presa in coordinate root: registra il "dove" per calcolare il bersaglio.
    var originInRoot by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .onGloballyPositioned { originInRoot = it.positionInRoot() }
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AppTheme.PrimaryBlue.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var started = false
                    if (down.type == PointerType.Mouse) {
                        while (!started) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: return@awaitEachGesture
                            if (!change.pressed) {
                                onTap()
                                return@awaitEachGesture
                            }
                            if ((change.position - down.position).getDistance() >= viewConfiguration.touchSlop) {
                                change.consume()
                                onStart()
                                onDrag(originInRoot + change.position)
                                started = true
                            }
                        }
                    } else {
                        var releasedBeforeLongPress = false
                        var movedBeforeLongPress = false
                        val longPressRecognized = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    releasedBeforeLongPress = true
                                    return@withTimeoutOrNull false
                                }
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                                    movedBeforeLongPress = true
                                    return@withTimeoutOrNull false
                                }
                            }
                            @Suppress("UNREACHABLE_CODE")
                            false
                        } == null
                        if (!longPressRecognized) {
                            if (releasedBeforeLongPress && !movedBeforeLongPress) onTap()
                            return@awaitEachGesture
                        }
                        onStart()
                        started = true
                    }

                    while (started) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            if (change != null) {
                                change.consume()
                                onDrag(originInRoot + change.position)
                            }
                            started = false
                        } else {
                            change.consume()
                            onDrag(originInRoot + change.position)
                        }
                    }
                    onEnd()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AppIcons.Grip(
            modifier = Modifier.size(16.dp),
            color = when {
                selected -> AppTheme.PrimaryBlue
                dimmed -> AppTheme.TextMuted.copy(alpha = 0.3f)
                else -> AppTheme.TextMuted
            }
        )
    }
}
