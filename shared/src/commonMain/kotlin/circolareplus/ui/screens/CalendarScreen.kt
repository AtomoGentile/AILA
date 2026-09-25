package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaIconButton
import circolareplus.design.AilaAssistantBadge
import circolareplus.design.AilaCard
import circolareplus.design.AilaDot
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaIconTile
import circolareplus.design.AilaScreenHeader
import circolareplus.design.AilaSectionTitle
import circolareplus.design.AnimatedFilterChip
import circolareplus.design.ailaAppear
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.CalendarEventCategory
import circolareplus.util.CivilDate
import circolareplus.util.ITALIAN_MONTHS
import circolareplus.util.ITALIAN_WEEKDAY_INITIALS
import circolareplus.util.daysInMonth
import circolareplus.util.firstWeekdayOfMonth
import circolareplus.util.nextMonth
import circolareplus.util.parseIsoDate
import circolareplus.util.previousMonth
import circolareplus.util.today

/**
 * Calendario di classe con la griglia del mese vera, come nel mockup.
 *
 * Prima al suo posto c'era una striscia orizzontale di numeri da 1 a 15, scollegata dal calendario
 * reale: non sapeva quanti giorni ha il mese, su che giorno cadesse il primo, e il mese era scritto
 * a mano nel sottotitolo ("Settembre 2026"). Ora il mese si sfoglia avanti e indietro, i giorni
 * stanno nella colonna del loro giorno della settimana, quelli con eventi hanno il pallino, oggi è
 * cerchiato e il giorno selezionato mostra sotto i suoi eventi.
 */
@Composable
fun CalendarScreen(
    events: List<CalendarEvent>,
    onAddEventClick: () -> Unit = {},
    onAddEventForDayClick: (String) -> Unit = {},
    onEventClick: (CalendarEvent) -> Unit = {},
    onDeleteEventClick: (CalendarEvent) -> Unit = {}
) {
    val todayDate = remember { today() }

    var visibleYear by rememberSaveable { mutableStateOf(todayDate.year) }
    var visibleMonth by rememberSaveable { mutableStateOf(todayDate.month) }
    var selectedDay by rememberSaveable { mutableStateOf(todayDate.day) }
    var selectedCategoryFilter by rememberSaveable(
        stateSaver = androidx.compose.runtime.saveable.Saver(
            save = { it?.name },
            restore = { CalendarEventCategory.valueOf(it) }
        )
    ) { mutableStateOf<CalendarEventCategory?>(null) }

    // Eventi del mese visibile, raggruppati per giorno: serve sia ai pallini nella griglia sia
    // alla lista sotto, quindi si calcola una volta sola.
    val eventsByDay: Map<Int, List<CalendarEvent>> = remember(events, visibleYear, visibleMonth, selectedCategoryFilter) {
        events
            .filter { selectedCategoryFilter == null || it.category == selectedCategoryFilter }
            .mapNotNull { event -> parseIsoDate(event.date)?.let { it to event } }
            .filter { (date, _) -> date.year == visibleYear && date.month == visibleMonth }
            .groupBy({ it.first.day }, { it.second })
    }

    val daysCount = daysInMonth(visibleYear, visibleMonth)
    val leadingBlanks = firstWeekdayOfMonth(visibleYear, visibleMonth)
    val selectedEvents = eventsByDay[selectedDay].orEmpty()
    val selectedDateIso = remember(visibleYear, visibleMonth, selectedDay) {
        CivilDate(visibleYear, visibleMonth, selectedDay).toIso()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
    ) {
        AilaScreenHeader(
            title = "Calendario",
            subtitle = "Scadenze, verifiche e pagamenti della classe",
            // Niente più tasto AI separato in header: "AILA Assistant" è già la prima scelta
            // dentro il foglio che si apre da "Aggiungi", un tasto in più qui era ridondante.
            action = {
                AilaIconButton(contentDescription = "Aggiungi evento", onClick = onAddEventClick, primary = true) { tint ->
                    AppIcons.Plus(modifier = Modifier.size(18.dp), color = tint)
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.Space16)
                .padding(top = AppTheme.Space16, bottom = AppTheme.Space32)
        ) {
            AilaCard(modifier = Modifier.ailaAppear(0)) {
                Column(modifier = Modifier.padding(AppTheme.Space12)) {
                    MonthNavigator(
                        year = visibleYear,
                        month = visibleMonth,
                        onPrevious = {
                            val (y, m) = previousMonth(visibleYear, visibleMonth)
                            visibleYear = y
                            visibleMonth = m
                            selectedDay = selectedDay.coerceAtMost(daysInMonth(y, m))
                        },
                        onNext = {
                            val (y, m) = nextMonth(visibleYear, visibleMonth)
                            visibleYear = y
                            visibleMonth = m
                            selectedDay = selectedDay.coerceAtMost(daysInMonth(y, m))
                        }
                    )

                    Spacer(modifier = Modifier.height(AppTheme.Space12))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        ITALIAN_WEEKDAY_INITIALS.forEach { initial ->
                            Text(
                                text = initial,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.TextFaint,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    MonthGrid(
                        daysCount = daysCount,
                        leadingBlanks = leadingBlanks,
                        selectedDay = selectedDay,
                        today = todayDate.takeIf { it.year == visibleYear && it.month == visibleMonth },
                        daysWithEvents = eventsByDay.keys,
                        onSelectDay = { selectedDay = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.Space20))

            // Filtri categoria: senza emoji, con l'icona della categoria. Pill scorrevole condiviso
            // (AilaSlidingChipRow) invece del cross-fade di colore su ogni singola chip; scorrevole
            // perché con 4 chip il contenuto non ci sta su schermi stretti.
            val categoryFilterOptions = remember { listOf<CalendarEventCategory?>(null, CalendarEventCategory.VERIFICA, CalendarEventCategory.PAGAMENTO, CalendarEventCategory.AVVISO) }
            circolareplus.design.AilaSlidingChipRow(
                selectedIndex = categoryFilterOptions.indexOf(selectedCategoryFilter),
                itemCount = categoryFilterOptions.size,
                modifier = Modifier.fillMaxWidth().ailaAppear(1)
            ) { chipModifier ->
                AnimatedFilterChip(
                    label = "Tutte",
                    isSelected = selectedCategoryFilter == null,
                    onClick = { selectedCategoryFilter = null },
                    drawSelectionBackground = false,
                    modifier = chipModifier(0)
                )
                AnimatedFilterChip(
                    label = "Verifiche",
                    isSelected = selectedCategoryFilter == CalendarEventCategory.VERIFICA,
                    onClick = { selectedCategoryFilter = CalendarEventCategory.VERIFICA },
                    icon = { tint -> AppIcons.Pencil(modifier = Modifier.size(14.dp), color = tint) },
                    drawSelectionBackground = false,
                    modifier = chipModifier(1)
                )
                AnimatedFilterChip(
                    label = "Pagamenti",
                    isSelected = selectedCategoryFilter == CalendarEventCategory.PAGAMENTO,
                    onClick = { selectedCategoryFilter = CalendarEventCategory.PAGAMENTO },
                    icon = { tint -> AppIcons.Card(modifier = Modifier.size(14.dp), color = tint) },
                    drawSelectionBackground = false,
                    modifier = chipModifier(2)
                )
                AnimatedFilterChip(
                    label = "Avvisi",
                    isSelected = selectedCategoryFilter == CalendarEventCategory.AVVISO,
                    onClick = { selectedCategoryFilter = CalendarEventCategory.AVVISO },
                    icon = { tint -> AppIcons.Bell(modifier = Modifier.size(14.dp), color = tint) },
                    drawSelectionBackground = false,
                    modifier = chipModifier(3)
                )
            }

            Spacer(modifier = Modifier.height(AppTheme.Space20))

            AilaSectionTitle(
                text = "${selectedDay} ${ITALIAN_MONTHS.getOrElse(visibleMonth) { "" }}",
                modifier = Modifier.ailaAppear(2),
                // "+" per creare un evento legato proprio a questo giorno: prima l'unico modo di
                // aggiungere un evento era il tasto "Aggiungi" in alto, che non sapeva quale giorno
                // si stesse guardando e obbligava a riscegliere la data da zero.
                actionText = "+ Aggiungi",
                onActionClick = { onAddEventForDayClick(selectedDateIso) }
            )

            Spacer(modifier = Modifier.height(AppTheme.Space12))

            if (selectedEvents.isEmpty()) {
                AilaCard(modifier = Modifier.ailaAppear(3)) {
                    AilaEmptyState(
                        title = "Nessun evento",
                        message = "Niente in programma per questo giorno.",
                        actionLabel = "Aggiungi evento",
                        onAction = { onAddEventForDayClick(selectedDateIso) },
                        icon = { AppIcons.Calendar(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                    )
                }
            } else {
                selectedEvents.forEachIndexed { index, event ->
                    CalendarEventCard(
                        event = event,
                        onClick = { onEventClick(event) },
                        onDeleteClick = { onDeleteEventClick(event) },
                        modifier = Modifier.ailaAppear(index + 3)
                    )
                    if (index != selectedEvents.lastIndex) {
                        Spacer(modifier = Modifier.height(AppTheme.Space12))
                    }
                }
            }
        }
    }
}

/** Riga "‹ Settembre 2026 ›" sopra la griglia. */
@Composable
private fun MonthNavigator(
    year: Int,
    month: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonthArrow(onClick = onPrevious) {
            AppIcons.ChevronLeft(modifier = Modifier.size(18.dp), color = AppTheme.TextDark)
        }
        Text(
            text = "${ITALIAN_MONTHS.getOrElse(month) { "" }} $year",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextDark
        )
        MonthArrow(onClick = onNext) {
            AppIcons.ChevronRight(modifier = Modifier.size(18.dp), color = AppTheme.TextDark)
        }
    }
}

@Composable
private fun MonthArrow(onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(AppTheme.TintSlate)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

/** Griglia del mese: sei righe da sette celle, con i vuoti iniziali per allineare i giorni. */
@Composable
private fun MonthGrid(
    daysCount: Int,
    leadingBlanks: Int,
    selectedDay: Int,
    today: CivilDate?,
    daysWithEvents: Set<Int>,
    onSelectDay: (Int) -> Unit
) {
    val totalCells = leadingBlanks + daysCount
    val rows = (totalCells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (column in 0 until 7) {
                    val cellIndex = row * 7 + column
                    val day = cellIndex - leadingBlanks + 1
                    if (day in 1..daysCount) {
                        DayCell(
                            day = day,
                            isSelected = day == selectedDay,
                            isToday = today?.day == day,
                            hasEvents = day in daysWithEvents,
                            onClick = { onSelectDay(day) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.height(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .size(width = 40.dp, height = 42.dp)
                .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                .then(
                    when {
                        isSelected -> Modifier.background(AppTheme.PrimaryGradient)
                        isToday -> Modifier.border(
                            1.5.dp,
                            AppTheme.PrimaryBlue,
                            RoundedCornerShape(AppTheme.SmallElementRadius)
                        )
                        else -> Modifier
                    }
                )
                .clickable { onClick() }
                .padding(top = 7.dp)
        ) {
            Text(
                text = "$day",
                fontSize = 14.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isSelected -> Color.White
                    isToday -> AppTheme.PrimaryBlue
                    else -> AppTheme.TextDark
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
            AilaDot(
                color = when {
                    !hasEvents -> Color.Transparent
                    isSelected -> Color.White
                    else -> AppTheme.PrimaryBlue
                },
                size = 5.dp
            )
        }
    }
}

@Composable
fun CalendarEventCard(
    event: CalendarEvent,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AilaCard(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppTheme.Space16),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (tint, ink) = when (event.category) {
                CalendarEventCategory.VERIFICA -> AppTheme.TintBlue to AppTheme.TintBlueInk
                CalendarEventCategory.PAGAMENTO -> AppTheme.TintAmber to AppTheme.TintAmberInk
                CalendarEventCategory.USCITA_DIDATTICA -> AppTheme.TintGreen to AppTheme.TintGreenInk
                CalendarEventCategory.AVVISO -> AppTheme.TintRed to AppTheme.TintRedInk
                else -> AppTheme.TintSlate to AppTheme.TintSlateInk
            }
            AilaIconTile(tint = tint) {
                when (event.category) {
                    CalendarEventCategory.VERIFICA ->
                        AppIcons.Pencil(modifier = Modifier.size(20.dp), color = ink)
                    CalendarEventCategory.PAGAMENTO ->
                        AppIcons.Card(modifier = Modifier.size(20.dp), color = ink)
                    CalendarEventCategory.USCITA_DIDATTICA ->
                        AppIcons.Bus(modifier = Modifier.size(20.dp), color = ink)
                    CalendarEventCategory.AVVISO ->
                        AppIcons.Bell(modifier = Modifier.size(20.dp), color = ink)
                    else ->
                        AppIcons.Calendar(modifier = Modifier.size(20.dp), color = ink)
                }
            }

            Spacer(modifier = Modifier.width(AppTheme.Space12))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TextDark,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = event.time ?: "Tutto il giorno",
                    fontSize = 13.sp,
                    color = AppTheme.TextMuted
                )
                if (event.isAiGenerated) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AilaAssistantBadge(text = "Inserito da AILA Assistant")
                }
            }

            Spacer(modifier = Modifier.width(AppTheme.Space8))

            Box(
                modifier = Modifier
                    .clickable(enabled = true) { onDeleteClick() }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                AppIcons.Trash(modifier = Modifier.size(18.dp), color = AppTheme.TintRed)
            }

            if (event.isForAll) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppTheme.TintSlate)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(text = "Tutti", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppTheme.TextMuted)
                }
            }
        }
    }
}
