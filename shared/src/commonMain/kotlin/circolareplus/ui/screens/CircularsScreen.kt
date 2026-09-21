package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    analyzingNumbers: List<Int> = emptyList()
) {
    var selectedFilter by remember { mutableStateOf<CircularRelevanceBadge?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
            .padding(horizontal = AppTheme.Space16)
            .padding(top = AppTheme.Space16)
    ) {
        // Il titolo grande non c'\u00e8 pi\u00f9: dentro la tab "Classe" lo d\u00e0 gi\u00e0 il selettore
        // Circolari/Bacheca in cima (vedi MainAppShell), ripeterlo qui creava due intestazioni
        // sovrapposte. Resta la sola riga di contesto sulla provenienza dei dati.
        Text(
            text = "Aggiornato da Spaggiari \u2022 Analisi AI locale",
            fontSize = 12.sp,
            color = AppTheme.TextFaint
        )

        Spacer(modifier = Modifier.height(AppTheme.Space12))

        // Ricerca per numero o titolo: mancava del tutto, prevista nel design di riferimento.
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Cerca per numero o titolo\u2026", fontSize = 14.sp) },
            leadingIcon = {
                AppIcons.Search(modifier = Modifier.size(18.dp), color = AppTheme.TextFaint)
            },
            singleLine = true,
            shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp),
            colors = ailaFieldColors()
        )

        Spacer(modifier = Modifier.height(AppTheme.Space12))

        // Filtri per Badge: pill scorrevole condiviso (AilaSlidingChipRow) invece del cross-fade di
        // colore su ogni singola chip.
        val relevanceFilterOptions = remember { listOf<CircularRelevanceBadge?>(null, CircularRelevanceBadge.RELEVANT, CircularRelevanceBadge.POTENTIAL, CircularRelevanceBadge.NOT_RELEVANT) }
        circolareplus.design.AilaSlidingChipRow(
            selectedIndex = relevanceFilterOptions.indexOf(selectedFilter),
            itemCount = relevanceFilterOptions.size,
            modifier = Modifier.fillMaxWidth()
        ) { chipModifier ->
            circolareplus.design.AnimatedFilterChip(
                label = "Tutte",
                isSelected = selectedFilter == null,
                onClick = { selectedFilter = null },
                drawSelectionBackground = false,
                modifier = chipModifier(0)
            )
            circolareplus.design.AnimatedFilterChip(
                label = "Ti riguarda",
                isSelected = selectedFilter == CircularRelevanceBadge.RELEVANT,
                onClick = { selectedFilter = CircularRelevanceBadge.RELEVANT },
                icon = { AilaDot(color = AppTheme.BadgeRelevantGreen, size = 8.dp) },
                drawSelectionBackground = false,
                modifier = chipModifier(1)
            )
            circolareplus.design.AnimatedFilterChip(
                label = "Potenziale",
                isSelected = selectedFilter == CircularRelevanceBadge.POTENTIAL,
                onClick = { selectedFilter = CircularRelevanceBadge.POTENTIAL },
                icon = { AilaDot(color = AppTheme.BadgePotentialYellow, size = 8.dp) },
                drawSelectionBackground = false,
                modifier = chipModifier(2)
            )
            circolareplus.design.AnimatedFilterChip(
                label = "Non rilevanti",
                isSelected = selectedFilter == CircularRelevanceBadge.NOT_RELEVANT,
                onClick = { selectedFilter = CircularRelevanceBadge.NOT_RELEVANT },
                icon = { AilaDot(color = AppTheme.BadgeNotRelevantGray, size = 8.dp) },
                drawSelectionBackground = false,
                modifier = chipModifier(3)
            )
        }

        Spacer(modifier = Modifier.height(AppTheme.Space16))

        val filteredCirculars = circulars.filter { circ ->
            val matchesFilter = if (selectedFilter == null) true
                else classifications[circ.number]?.badge == selectedFilter
            val matchesSearch = searchQuery.isBlank() ||
                circ.title.contains(searchQuery, ignoreCase = true) ||
                circ.number.toString().contains(searchQuery.trim())
            matchesFilter && matchesSearch
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12),
            contentPadding = PaddingValues(bottom = AppTheme.Space24),
            modifier = Modifier.fillMaxSize()
        ) {
            if (filteredCirculars.isEmpty()) {
                item {
                    AilaEmptyState(
                        title = if (circulars.isEmpty()) "Nessuna circolare" else "Nessun risultato",
                        message = when {
                            circulars.isEmpty() ->
                                "Non è ancora arrivata nessuna circolare dalla scuola."
                            searchQuery.isNotBlank() ->
                                "Nessuna circolare corrisponde a \"$searchQuery\"."
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
                val classification = classifications[circ.number]
                CircularListItem(
                    circular = circ,
                    classification = classification,
                    isAnalyzing = circ.number in analyzingNumbers,
                    onClick = { onSelectCircular(circ) },
                    modifier = Modifier.ailaAppear(index)
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
    isAnalyzing: Boolean = false
) {
    val (badgeColor, badgeText) = when (classification?.badge) {
        CircularRelevanceBadge.RELEVANT -> Pair(AppTheme.BadgeRelevantGreen, "Ti riguarda")
        CircularRelevanceBadge.POTENTIAL -> Pair(AppTheme.BadgePotentialYellow, "Potenziale interesse")
        CircularRelevanceBadge.NOT_RELEVANT -> Pair(AppTheme.BadgeNotRelevantGray, "Non sembra riguardarti")
        null -> if (isAnalyzing) {
            Pair(AppTheme.PrimaryBlue, "Analisi in corso\u2026")
        } else {
            Pair(AppTheme.TextFaint, "Da classificare")
        }
    }

    AilaCard(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.padding(AppTheme.Space16)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (classification == null && isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = badgeColor
                        )
                    } else {
                        AilaDot(color = badgeColor)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badgeText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                }
                Text(
                    text = circular.publishDate,
                    fontSize = 12.sp,
                    color = AppTheme.TextMuted
                )
            }

            Spacer(modifier = Modifier.height(AppTheme.Space8))

            Text(
                text = "Circolare n. ${circular.number} \u2014 ${circular.title}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark
            )

            if (classification?.personalSummary != null) {
                Spacer(modifier = Modifier.height(AppTheme.Space8))
                AilaCard(containerColor = AppTheme.TintSlate) {
                    Column(modifier = Modifier.padding(AppTheme.Space12)) {
                        AilaAssistantBadge(text = "Analisi AILA Assistant")
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = classification.personalSummary,
                            fontSize = 13.sp,
                            color = AppTheme.TextMuted,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}
