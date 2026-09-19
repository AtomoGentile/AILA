package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.algorithms.DeskAssignment
import circolareplus.algorithms.OptimizerWeights
import circolareplus.algorithms.SeatMapOptimizer
import circolareplus.design.AilaCard
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaScreenHeader
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.domain.model.User

@Composable
fun SeatMapScreen(
    currentUserId: String,
    isRepresentative: Boolean,
    assignments: List<DeskAssignment>,
    studentsMap: Map<String, User>,
    isPreferencesOpen: Boolean,
    onTogglePreferencesWindow: (Boolean) -> Unit = {},
    onGenerateProposals: (OptimizerWeights, seatsPerDesk: Int) -> Unit = { _, _ -> },
    isExportingPdf: Boolean = false,
    onExportPdf: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var focusedStudentId by remember { mutableStateOf<String?>(null) }

    // Pesi slider per Rappresentante (0.5x - 1.5x)
    var wSocial by remember { mutableStateOf(1.0f) }
    var wDiscipline by remember { mutableStateOf(1.0f) }
    var wDidactic by remember { mutableStateOf(1.0f) }
    // Banchi da coppia (2) o da trio (3): stesso algoritmo, vedi SeatMapOptimizer.optimize.
    var seatsPerDesk by remember { mutableStateOf(SeatMapOptimizer.SEATS_PER_DESK_PAIR) }

    // Una disposizione pubblicata usa banchi da trio se ANCHE UN SOLO banco ha un terzo
    // occupante: da qui la griglia sa se disegnare due o tre righe per banco, senza bisogno di
    // tenere lo stato "modalità" separato dai dati (che sopravviverebbe male a un riavvio app).
    val hasTrioDesks = remember(assignments) { assignments.any { it.studentCId != null } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
    ) {
        // Intestazione chiara comune (design AILA), con l'azione rapida a destra.
        AilaScreenHeader(
            title = "Mappa Posti Aula",
            subtitle = "Layout 2D orientato rispetto alla Cattedra",
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                    // Esporta la disposizione pubblicata in PDF (disegno del layout, non solo
                    // testo): utile a chiunque veda la mappa, non solo al Rappresentante.
                    if (assignments.isNotEmpty()) {
                        AilaPrimaryButton(
                            text = if (isExportingPdf) "Genero…" else "Esporta PDF",
                            onClick = onExportPdf,
                            compact = true,
                            enabled = !isExportingPdf
                        )
                    }
                    // Tasto Rapido Studente: "Dov'è il mio posto?"
                    AilaPrimaryButton(
                        text = "Il mio posto",
                        onClick = { focusedStudentId = currentUserId },
                        compact = true
                    )
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
            modifier = Modifier.fillMaxSize()
        ) {
        fullRow {

        // Ricerca compagno per localizzarlo
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cerca compagno...", fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(AppTheme.CardCornerRadius),
            modifier = Modifier.fillMaxWidth()
        )
        }

        // Pannello Admin per il Rappresentante (Slider & Finestra Votazione)
        if (isRepresentative) {
        fullRow {
            AilaCard(containerColor = AppTheme.TintSlate) {
                Column(modifier = Modifier.padding(AppTheme.Space12)) {
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
                        Text(
                            text = if (isPreferencesOpen) "Finestra Preferenze: APERTA" else "Finestra Preferenze: CHIUSA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPreferencesOpen) AppTheme.PollGreen else AppTheme.PollDarkRed
                        )
                        AilaPrimaryButton(
                            text = if (isPreferencesOpen) "Chiudi votazione" else "Apri votazione",
                            onClick = { onTogglePreferencesWindow(!isPreferencesOpen) },
                            compact = true
                        )
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
                    title = "Nessuna disposizione pubblicata",
                    message = if (isRepresentative)
                        "Apri la votazione delle preferenze, poi calcola e pubblica una delle tre proposte."
                    else
                        "Il Rappresentante non ha ancora pubblicato la disposizione dei banchi.",
                    icon = { AppIcons.Chair(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                )
            }
        }

            items(assignments) { desk ->
                val sA = desk.studentAId?.let { studentsMap[it] }
                val sB = desk.studentBId?.let { studentsMap[it] }
                val sC = desk.studentCId?.let { studentsMap[it] }

                val isMeAtDesk = desk.studentAId == focusedStudentId ||
                    desk.studentBId == focusedStudentId ||
                    desk.studentCId == focusedStudentId

                Card(
                    shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isMeAtDesk) AppTheme.TintAmber else AppTheme.SurfaceWhite
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = AppTheme.CardElevation),
                    modifier = Modifier
                        .border(
                            width = if (isMeAtDesk) 2.dp else 1.dp,
                            color = if (isMeAtDesk) AppTheme.TintAmberInk else AppTheme.Hairline,
                            shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Banco F${desk.row + 1}C${desk.column + 1}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.TextMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = sA?.firstName ?: "Vuoto",
                            fontSize = 12.sp,
                            fontWeight = if (desk.studentAId == focusedStudentId) FontWeight.Bold else FontWeight.Normal,
                            color = AppTheme.TextDark
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = AppTheme.Hairline
                        )
                        Text(
                            text = sB?.firstName ?: "Vuoto",
                            fontSize = 12.sp,
                            fontWeight = if (desk.studentBId == focusedStudentId) FontWeight.Bold else FontWeight.Normal,
                            color = AppTheme.TextDark
                        )
                        // Terzo posto (banchi da trio): mostrato solo se la disposizione
                        // corrente li usa, così i banchi da coppia restano a due righe come sempre.
                        if (hasTrioDesks) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = AppTheme.Hairline
                            )
                            Text(
                                text = sC?.firstName ?: "Vuoto",
                                fontSize = 12.sp,
                                fontWeight = if (desk.studentCId == focusedStudentId) FontWeight.Bold else FontWeight.Normal,
                                color = AppTheme.TextDark
                            )
                        }
                    }
                }
            }
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
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(if (isSelected) AppTheme.PrimaryBlue else AppTheme.SurfaceWhite)
            .border(
                1.dp,
                if (isSelected) AppTheme.PrimaryBlue else AppTheme.Hairline,
                RoundedCornerShape(AppTheme.SmallElementRadius)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else AppTheme.TextDark
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
