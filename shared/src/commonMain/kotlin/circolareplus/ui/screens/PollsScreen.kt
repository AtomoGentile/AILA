package circolareplus.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.algorithms.InterrogationVoteType
import circolareplus.design.AilaCard
import circolareplus.design.AilaDot
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaIconTile
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.ailaAppear
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.util.ITALIAN_MONTHS
import circolareplus.util.parseIsoDate
import circolareplus.util.weekdayName

data class SlotUiItem(
    val slotId: String,
    val dateLabel: String,
    val subject: String,
    val capacity: Int,
    val isMandatory: Boolean = false,
    val currentVote: InterrogationVoteType? = null
)

/**
 * Unico limite rimasto: i rifiuti assoluti. "Ci sto" e "meglio di no" non hanno più un tetto —
 * con date numerose si finivano le opzioni prima delle date, e restavi bloccato senza poter dire
 * niente sulle ultime.
 */
private const val MAX_DARK_RED = 2

/**
 * Sondaggio interrogazioni.
 *
 * Cambiato dopo il primo giro di prove:
 * - le quattro scelte sono cerchi e non riquadri, e la card di una data occupa molto meno spazio;
 * - i punteggi (+50 / -300) non si vedono più: erano rumore, e sapere quanto "vale" un voto porta
 *   a giocare col punteggio invece che a dire quando si può davvero;
 * - resta un limite solo sui rifiuti assoluti;
 * - la data ISO accanto al riquadro giorno/mese è sostituita dal giorno della settimana, che è
 *   l'informazione che serve davvero per capire quando cade;
 * - in cima c'è la barra di avanzamento della compilazione, che resta visibile anche scorrendo;
 * - in fondo il tasto per inviare le proprie scelte.
 */
@Composable
fun PollsScreen(
    subjectName: String,
    slots: List<SlotUiItem>,
    sacrificeBonus: Int,
    onCastVote: (String, InterrogationVoteType) -> Unit,
    isSubmitted: Boolean = false,
    // Avanzamento della classe, non solo il proprio: l'invio ora arriva al server, quindi si
    // può sapere quanti compagni hanno finito. Prima "ho inviato" era un flag sul telefono e
    // nessuno poteva vedere a che punto fosse la classe.
    submittedCount: Int = 0,
    totalStudents: Int = 0,
    /** Scadenza già formattata per la lettura, es. "12 marzo". Null se non è stata impostata. */
    closesAtLabel: String? = null,
    isExpired: Boolean = false,
    isSubmitting: Boolean = false,
    onSubmit: () -> Unit = {},
    onReopen: () -> Unit = {},
    // Il Rappresentante può far partire l'algoritmo a mano: il calcolo automatico all'ultimo
    // invio non è affidabile, quindi qui c'è sempre una via manuale che non dipende da quello.
    isRepresentative: Boolean = false,
    isCalculating: Boolean = false,
    onRunAssignments: () -> Unit = {}
) {
    val darkRedCount = slots.count { it.currentVote == InterrogationVoteType.DARK_RED }
    val votedCount = slots.count { it.currentVote != null }
    val progress = if (slots.isEmpty()) 0f else votedCount.toFloat() / slots.size

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.BackgroundLight)) {
        // Barra di avanzamento fissa: non scorre con la lista, così sai sempre quante date
        // mancano senza dover tornare in cima.
        PollProgressHeader(
            subjectName = subjectName,
            votedCount = votedCount,
            total = slots.size,
            progress = progress,
            isSubmitted = isSubmitted,
            submittedCount = submittedCount,
            totalStudents = totalStudents,
            closesAtLabel = closesAtLabel,
            isExpired = isExpired
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(
                start = AppTheme.Space16,
                end = AppTheme.Space16,
                top = AppTheme.Space12,
                bottom = AppTheme.Space16
            ),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)
        ) {
            item {
                AilaCard(modifier = Modifier.ailaAppear(0)) {
                    Column(modifier = Modifier.padding(AppTheme.Space16)) {
                        Text(
                            text = "Come funziona",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.TextDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Per ogni data dici quanto ti va bene. Chi accetta le date che " +
                                "gli altri rifiutano guadagna un bonus per i sondaggi futuri. " +
                                "I \"meglio di no\" contano pieni fino a un terzo delle date: " +
                                "se ne metti di più ognuno pesa meno, e vale meno bonus.",
                            fontSize = 13.sp,
                            color = AppTheme.TextMuted,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(AppTheme.Space12))
                        VoteLegend(darkRedLeft = MAX_DARK_RED - darkRedCount)
                    }
                }
            }

            if (isRepresentative && slots.isNotEmpty()) {
                item {
                    AilaCard(modifier = Modifier.ailaAppear(1)) {
                        Column(modifier = Modifier.padding(AppTheme.Space16)) {
                            Text(
                                text = "Calcolo risultati",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.TextDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Chiude il sondaggio e calcola subito l'assegnazione con l'algoritmo, " +
                                    "anche se non hanno ancora inviato tutti.",
                                fontSize = 13.sp,
                                color = AppTheme.TextMuted,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(AppTheme.Space12))
                            AilaPrimaryButton(
                                text = if (isCalculating) "Calcolo…" else "Calcola risultati",
                                onClick = onRunAssignments,
                                enabled = !isCalculating,
                                fillMaxWidth = true
                            )
                        }
                    }
                }
            }

            if (sacrificeBonus > 0) {
                item {
                    AilaCard(containerColor = AppTheme.TintAmber, modifier = Modifier.ailaAppear(1)) {
                        Row(
                            modifier = Modifier.padding(AppTheme.Space16),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AilaIconTile(tint = Color(0x33FFFFFF)) {
                                AppIcons.Star(modifier = Modifier.size(20.dp), color = AppTheme.TintAmberInk)
                            }
                            Spacer(modifier = Modifier.width(AppTheme.Space12))
                            Column {
                                Text(
                                    text = "Bonus sacrificio attivo",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.TintAmberInk
                                )
                                Text(
                                    text = "Hai accettato date che gli altri rifiutavano: il tuo \"ci sto\" pesa di più.",
                                    fontSize = 12.sp,
                                    color = AppTheme.TintAmberInk,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            if (slots.isEmpty()) {
                item {
                    AilaEmptyState(
                        title = "Nessuna data proposta",
                        message = "Il sondaggio è aperto ma non ci sono ancora date su cui votare.",
                        icon = { AppIcons.Calendar(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                    )
                }
            }

            itemsIndexed(slots, key = { _, slot -> slot.slotId }) { index, slot ->
                SlotRowItem(
                    slot = slot,
                    darkRedLeft = MAX_DARK_RED - darkRedCount,
                    enabled = !isSubmitted,
                    onSelectVote = { voteType -> onCastVote(slot.slotId, voteType) },
                    modifier = Modifier.ailaAppear(index + 2)
                )
            }
        }

        PollSubmitBar(
            isSubmitted = isSubmitted,
            allVoted = slots.isNotEmpty() && votedCount == slots.size,
            missing = slots.size - votedCount,
            isExpired = isExpired,
            isSubmitting = isSubmitting,
            onSubmit = onSubmit,
            onReopen = onReopen
        )
    }
}

/** Intestazione fissa: materia, quante date hai votato e la barra che si riempie. */
@Composable
private fun PollProgressHeader(
    subjectName: String,
    votedCount: Int,
    total: Int,
    progress: Float,
    isSubmitted: Boolean,
    submittedCount: Int = 0,
    totalStudents: Int = 0,
    closesAtLabel: String? = null,
    isExpired: Boolean = false
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 420),
        label = "pollProgress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.SurfaceWhite)
            .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = subjectName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isSubmitted) "Scelte inviate" else "$votedCount di $total",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSubmitted) AppTheme.TintGreenInk else AppTheme.TextMuted
            )
        }

        Spacer(modifier = Modifier.height(AppTheme.Space8))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(CircleShape)
                .background(AppTheme.TintSlate)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(if (isSubmitted) AppTheme.PollGreen else AppTheme.PrimaryBlue)
            )
        }

        // Seconda riga: a che punto è la classe e quanto tempo resta. Serve a capire perché
        // l'algoritmo non è ancora partito senza doverlo chiedere al Rappresentante.
        if (totalStudents > 0 || closesAtLabel != null || isExpired) {
            Spacer(modifier = Modifier.height(AppTheme.Space8))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (totalStudents > 0) {
                    val everyoneDone = submittedCount >= totalStudents
                    Text(
                        text = if (everyoneDone) {
                            "Hanno inviato tutti"
                        } else {
                            "$submittedCount di $totalStudents compagni hanno inviato"
                        },
                        fontSize = 11.sp,
                        fontWeight = if (everyoneDone) FontWeight.Bold else FontWeight.Normal,
                        color = if (everyoneDone) AppTheme.TintGreenInk else AppTheme.TextFaint,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (isExpired) {
                    Text(
                        text = "Tempo scaduto",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TintRedInk,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppTheme.TintRed)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                } else if (closesAtLabel != null) {
                    Text(
                        text = "Entro il $closesAtLabel",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TintAmberInk,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppTheme.TintAmber)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
    HorizontalDivider(color = AppTheme.Hairline)
}

/** Legenda dei quattro colori: senza, i cerchi da soli non direbbero nulla. */
@Composable
private fun VoteLegend(darkRedLeft: Int) {
    Column {
        LegendRow(color = AppTheme.PollGreen, label = "Ci sto", note = "senza limiti")
        LegendRow(color = AppTheme.PollYellow, label = "Indifferente", note = "senza limiti")
        LegendRow(color = AppTheme.PollLightRed, label = "Meglio di no", note = "senza limiti")
        LegendRow(
            color = AppTheme.PollDarkRed,
            label = "Impossibile",
            note = if (darkRedLeft > 0) "te ne restano $darkRedLeft" else "esauriti"
        )
    }
}

@Composable
private fun LegendRow(color: Color, label: String, note: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AilaDot(color = color)
        Spacer(modifier = Modifier.width(AppTheme.Space8))
        Text(text = label, fontSize = 12.sp, color = AppTheme.TextDark, modifier = Modifier.weight(1f))
        Text(text = note, fontSize = 11.sp, color = AppTheme.TextFaint)
    }
}

/** Barra in fondo per inviare le proprie scelte. */
@Composable
private fun PollSubmitBar(
    isSubmitted: Boolean,
    allVoted: Boolean,
    missing: Int,
    isExpired: Boolean = false,
    isSubmitting: Boolean = false,
    onSubmit: () -> Unit,
    onReopen: () -> Unit
) {
    HorizontalDivider(color = AppTheme.Hairline)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.SurfaceWhite)
            .padding(AppTheme.Space16),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            // A tempo scaduto non si invia e non si modifica più: si potrebbero altrimenti
            // cambiare le proprie scelte dopo aver visto il calendario che ne è uscito.
            isExpired -> {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    AppIcons.Lock(modifier = Modifier.size(17.dp), color = AppTheme.TextFaint)
                    Spacer(modifier = Modifier.width(AppTheme.Space8))
                    Text(
                        text = if (isSubmitted) "Scelte inviate. Il sondaggio è chiuso."
                        else "Il sondaggio è chiuso.",
                        fontSize = 12.sp,
                        color = AppTheme.TextMuted
                    )
                }
            }

            isSubmitted -> {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    AppIcons.Check(modifier = Modifier.size(18.dp), color = AppTheme.TintGreenInk)
                    Spacer(modifier = Modifier.width(AppTheme.Space8))
                    Text(
                        text = "Scelte inviate",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TintGreenInk
                    )
                }
                AilaSecondaryButton(text = "Modifica", onClick = onReopen)
            }

            else -> {
                Text(
                    text = if (allVoted) "Hai votato tutte le date"
                    else if (missing == 1) "Manca 1 data"
                    else "Mancano $missing date",
                    fontSize = 12.sp,
                    color = AppTheme.TextMuted,
                    modifier = Modifier.weight(1f)
                )
                AilaPrimaryButton(
                    text = if (isSubmitting) "Invio…" else "Invia le mie scelte",
                    onClick = onSubmit,
                    enabled = allVoted && !isSubmitting
                )
            }
        }
    }
}

@Composable
fun SlotRowItem(
    slot: SlotUiItem,
    darkRedLeft: Int,
    enabled: Boolean,
    onSelectVote: (InterrogationVoteType) -> Unit,
    modifier: Modifier = Modifier
) {
    AilaCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppTheme.Space12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val parsed = parseIsoDate(slot.dateLabel)
            AilaIconTile(tint = AppTheme.TintBlue, size = 44.dp) {
                if (parsed != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${parsed.day}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.TintBlueInk
                        )
                        Text(
                            text = ITALIAN_MONTHS.getOrElse(parsed.month) { "" }.take(3).uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.TintBlueInk
                        )
                    }
                } else {
                    AppIcons.Calendar(modifier = Modifier.size(20.dp), color = AppTheme.TintBlueInk)
                }
            }

            Spacer(modifier = Modifier.width(AppTheme.Space12))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // Il giorno della settimana al posto della data ISO: il numero e il mese
                        // sono già nel riquadro qui a sinistra, ripeterli non aggiungeva nulla.
                        text = weekdayName(slot.dateLabel).ifBlank { slot.dateLabel },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextDark
                    )
                    if (slot.isMandatory) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(AppTheme.TintAmber)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Fissata",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.TintAmberInk
                            )
                        }
                    }
                }
                Text(
                    text = if (slot.capacity == 1) "1 posto" else "${slot.capacity} posti",
                    fontSize = 12.sp,
                    color = AppTheme.TextMuted
                )
            }

            Spacer(modifier = Modifier.width(AppTheme.Space8))

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                val selected = slot.currentVote
                VoteCircle(
                    color = AppTheme.PollGreen,
                    isSelected = selected == InterrogationVoteType.GREEN,
                    enabled = enabled,
                    onClick = { onSelectVote(InterrogationVoteType.GREEN) }
                )
                VoteCircle(
                    color = AppTheme.PollYellow,
                    isSelected = selected == InterrogationVoteType.YELLOW,
                    enabled = enabled,
                    onClick = { onSelectVote(InterrogationVoteType.YELLOW) }
                )
                VoteCircle(
                    color = AppTheme.PollLightRed,
                    isSelected = selected == InterrogationVoteType.LIGHT_RED,
                    enabled = enabled,
                    onClick = { onSelectVote(InterrogationVoteType.LIGHT_RED) }
                )
                VoteCircle(
                    color = AppTheme.PollDarkRed,
                    isSelected = selected == InterrogationVoteType.DARK_RED,
                    enabled = enabled && (selected == InterrogationVoteType.DARK_RED || darkRedLeft > 0),
                    onClick = { onSelectVote(InterrogationVoteType.DARK_RED) }
                )
            }
        }
    }
}

/**
 * Un cerchio di voto. Selezionato: pieno col segno di spunta. Non selezionato: contorno del
 * proprio colore. Disabilitato: grigio.
 *
 * L'animazione è volutamente semplice — solo colore e scala del cerchio, senza rimbalzi
 * incrociati: la versione precedente animava quattro riquadri con molle a bassa rigidità e,
 * cambiando scelta due o tre volte di fila, le animazioni si accavallavano e sembrava che la
 * selezione "balbettasse" saltando da un'opzione all'altra.
 */
@Composable
private fun VoteCircle(
    color: Color,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val fill by animateColorAsState(
        targetValue = when {
            isSelected -> color
            !enabled -> AppTheme.TintSlate
            else -> color.copy(alpha = 0.14f)
        },
        animationSpec = tween(durationMillis = 160),
        label = "voteCircleFill"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "voteCircleScale"
    )

    Box(
        modifier = Modifier
            .size(34.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (isSelected) 0.dp else 1.5.dp,
                color = if (enabled) color.copy(alpha = 0.55f) else AppTheme.Hairline,
                shape = CircleShape
            )
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            AppIcons.Check(modifier = Modifier.size(17.dp), color = Color.White)
        }
    }
}
