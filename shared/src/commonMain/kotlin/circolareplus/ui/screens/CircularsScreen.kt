package circolareplus.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaAssistantBadge
import circolareplus.design.AilaDot
import circolareplus.design.AilaCard
import circolareplus.design.ailaFieldColors
import circolareplus.design.ailaAppear
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.domain.model.Circular
import circolareplus.domain.model.CircularAiClassification
import circolareplus.domain.model.CircularRelevanceBadge

@Composable
fun CircularsScreen(
    circulars: List<Circular>,
    classifications: Map<Int, CircularAiClassification>,
    onSelectCircular: (Circular) -> Unit,
    /** Numeri delle circolari la cui analisi e' in corso adesso (anche se non si e' nel dettaglio). */
    analyzingNumbers: List<Int> = emptyList(),
    /** Circolare aperta accanto alla lista (tablet e iPad): la sua card e' evidenziata. */
    selectedNumber: Int? = null,
    aiEnabled: Boolean = circolareplus.ai.AcaPolicyState.preferences.let { it.aiEnabled && "CIRCULARS" in it.services }
) {
    var selectedFilter by remember { mutableStateOf<CircularRelevanceBadge?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
            .padding(horizontal = AppTheme.Space16)
            .padding(top = AppTheme.Space8)
    ) {
        // Niente titolo ne' riga "Aggiornato da Spaggiari": l'intestazione la da' gia' il selettore
        // Circolari/Bacheca in cima, e lo spazio serve per leggere.

        val relevanceFilterOptions = remember { listOf<CircularRelevanceBadge?>(null, CircularRelevanceBadge.RELEVANT, CircularRelevanceBadge.POTENTIAL, CircularRelevanceBadge.NOT_RELEVANT) }
        if(aiEnabled) circolareplus.design.AilaFilterBar(
            labels=listOf("Tutte","Per te","Possibili","Altre"),
            selectedIndex=relevanceFilterOptions.indexOf(selectedFilter),
            onSelect={selectedFilter=relevanceFilterOptions[it]},
            dots=listOf(null,AppTheme.BadgeRelevantGreen,AppTheme.BadgePotentialYellow,AppTheme.BadgeNotRelevantGray)
        )

        Spacer(modifier = Modifier.height(AppTheme.Space16))

        val filteredCirculars = circulars.filter { circ ->
            val matchesFilter = if (selectedFilter == null) true
                else classifications[circ.number]?.badge == selectedFilter
            !aiEnabled || matchesFilter
        }

        // Cambiando filtro si torna in cima: la lista tiene la posizione per chiave,
        // quindi tornando a "Tutte" restava in alto la prima circolare del filtro precedente
        // invece dell'ultima uscita.
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        LaunchedEffect(selectedFilter) { listState.scrollToItem(0) }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12),
            contentPadding = PaddingValues(bottom = AppTheme.Space24 + circolareplus.design.LocalBottomBarPadding.current),
            modifier = Modifier.fillMaxSize()
        ) {
            val pending = (if (aiEnabled) circulars else emptyList()).filter { circ -> circ.number in analyzingNumbers }
                .sortedWith(compareByDescending<Circular> { it.number in analyzingNumbers }.thenByDescending { it.number })
            val pendingCount = pending.size
            if (pendingCount > 0) item(key = "analysis-disclosure") {
                circolareplus.design.CircularAnalysisDisclosure(pending)
            }
            if (filteredCirculars.isEmpty() && pendingCount == 0) {
                item {
                    AilaEmptyState(
                        title = if (circulars.isEmpty()) "Nessuna circolare" else "Nessun risultato",
                        message = when {
                            circulars.isEmpty() ->
                                "Non è ancora arrivata nessuna circolare dalla scuola."
                            else ->
                                "Nessuna circolare in questa categoria. Prova a togliere il filtro."
                        },
                        icon = { AppIcons.Document(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                    )
                }
            }
            // La chiave e' il numero: quando arriva una circolare nuova le altre restano dove
            // sono invece di essere ridisegnate per posizione (e di perdere lo stato).
            itemsIndexed(filteredCirculars, key = { _, circ -> circ.number }) { index, circ ->
                val classification = if (aiEnabled) classifications[circ.number] else null
                CircularListItem(
                    circular = circ,
                    aiEnabled = aiEnabled,
                    classification = classification,
                    isAnalyzing = circ.number in analyzingNumbers,
                    onClick = { onSelectCircular(circ) },
                    modifier = Modifier.ailaAppear(index).then(
                        if (circ.number == selectedNumber) Modifier.border(
                            2.dp,
                            AppTheme.PrimaryBlue,
                            androidx.compose.foundation.shape.RoundedCornerShape(if (circolareplus.design.isDesktopWorkspace()) 16.dp else AppTheme.CardCornerRadius)
                        ) else Modifier
                    )
                )
            }
        }
    }
}

@Composable
fun CircularListItem(
    circular: Circular,
    classification: CircularAiClassification?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAnalyzing: Boolean = false,
    aiEnabled: Boolean = true
) {
    // Pallino con la tinta, testo con l'inchiostro leggibile (vedi AppTheme.Badge*Ink).
    val (badgeColor, badgeInk, badgeText) = when (classification?.badge) {
        CircularRelevanceBadge.RELEVANT -> Triple(AppTheme.BadgeRelevantGreen, AppTheme.BadgeRelevantInk, "Ti riguarda")
        CircularRelevanceBadge.POTENTIAL -> Triple(AppTheme.BadgePotentialYellow, AppTheme.BadgePotentialInk, "Potenziale interesse")
        CircularRelevanceBadge.NOT_RELEVANT -> Triple(AppTheme.BadgeNotRelevantGray, AppTheme.BadgeNotRelevantInk, "Non sembra riguardarti")
        null -> if (isAnalyzing) {
            Triple(AppTheme.PrimaryBlue, AppTheme.PrimaryBlue, "Analisi in corso\u2026")
        } else {
            Triple(AppTheme.TextFaint, AppTheme.TextMuted, "Da classificare")
        }
    }

    AilaCard(onClick = onClick, modifier = modifier, transformKey = "circular:${circular.number}") {
        Column(modifier = Modifier.padding(AppTheme.Space16)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (aiEnabled) Row(verticalAlignment = Alignment.CenterVertically) {
                    if (classification == null && isAnalyzing) {
                        SmoothAnalysisSpinner(
                            color = badgeColor,
                            modifier = Modifier.size(12.dp)
                        )
                    } else {
                        AilaDot(color = badgeColor)
                    }
                    // Pallino ed etichetta con lo stesso stacco della legenda dei sondaggi.
                    Spacer(modifier = Modifier.width(AppTheme.Space8))
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = badgeInk
                    )
                }
                Text(
                    text = circular.publishDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.TextMuted
                )
            }

            Spacer(modifier = Modifier.height(AppTheme.Space8))

            Text(
                text = "Circolare n. ${circular.number} \u2014 ${circular.title}",
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.TextDark
            )

            if (classification?.personalSummary != null) {
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                val summaryContent: @Composable () -> Unit = {
                    Column(modifier = Modifier.padding(AppTheme.Space12)) {
                        AilaAssistantBadge(text = "In breve · AILA")
                        Spacer(modifier = Modifier.height(AppTheme.Space8))
                        Text(
                            text = classification.personalSummary,
                            maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.TextMuted
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Apri la circolare per leggere tutto",style=MaterialTheme.typography.labelSmall,color=AppTheme.PrimaryBlue)
                    }
                }
                if (AppTheme.isGlass) {
                    // Liquid Glass: niente vetro dentro al vetro (veniva un pannello grigio con
                    // bordi lucidi). Solo un incavo appena piu' scuro/chiaro, senza bordo ne' riflesso.
                    // Velo scritto qui: nessun token ha un incavo nero in scuro e bianco in chiaro.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppTheme.SmallElementRadius + 4.dp))
                            .background(if (AppTheme.isDarkMode) Color(0x1F000000) else Color(0x33FFFFFF))
                    ) { summaryContent() }
                } else {
                    AilaCard(containerColor = AppTheme.TintSlate) { summaryContent() }
                }
            }
        }
    }
}


/**
 * Indicatore compatto a rotazione lineare: evita la cadenza a scatti percepibile
 * con il CircularProgressIndicator indeterminato nelle dimensioni molto piccole.
 */
@Composable
private fun SmoothAnalysisSpinner(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "analysis-spinner")
    val startAngle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "analysis-spinner-angle"
    )

    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        drawArc(
            color = color,
            startAngle = startAngle.value,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}
