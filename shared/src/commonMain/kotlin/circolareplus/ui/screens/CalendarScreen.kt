package circolareplus.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
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
    /** Giorno da mostrare subito ("AAAA-MM-GG"), es. toccando un evento nella Home. */
    focusDateIso: String? = null,
    onFocusConsumed: () -> Unit = {},
    /** Eventi in corso di eliminazione: la loro card esce con un'animazione prima di sparire. */
    removingEventIds: Set<String> = emptySet()
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

    // Eventi comparsi dopo l'ultima lista (uno appena creato): entrano con un'animazione invece di
    // spuntare di colpo. Alla prima lista niente e' "nuovo", quindi non si anima nulla all'apertura.
    val previousEventIds = remember { arrayOf<Set<String>?>(null) }
    val freshEventIds: Set<String> = remember(events) {
        val now = events.map { it.id }.toSet()
        val previous = previousEventIds[0]
        previousEventIds[0] = now
        // Il primo elenco (o il primo dopo uno vuoto: il caricamento) non ha nulla di "nuovo".
        if (previous.isNullOrEmpty()) emptySet() else now - previous
    }
    // Ogni evento nuovo si anima una volta sola: tornando su quel giorno e' gia' "visto".
    val consumedFreshIds = remember { mutableSetOf<String>() }

    val selectedEvents = eventsByDay[selectedDay].orEmpty()
    // Arrivando da un evento della Home si apre il suo mese con quel giorno selezionato.
    LaunchedEffect(focusDateIso) {
        val focus = focusDateIso?.let { circolareplus.util.parseIsoDate(it) } ?: return@LaunchedEffect
        visibleYear = focus.year
        visibleMonth = focus.month
        selectedDay = focus.day
        onFocusConsumed()
    }
    val selectedDateIso = remember(visibleYear, visibleMonth, selectedDay) {
        CivilDate(visibleYear, visibleMonth, selectedDay).toIso()
    }
    // Nei giorni gia' passati non si creano eventi: niente "+ Aggiungi" (le date sono "AAAA-MM-GG",
    // quindi il confronto fra stringhe e' quello fra date).
    val isPastDay = selectedDateIso < todayDate.toIso()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
    ) {
        AilaScreenHeader(
            title = "Calendario",
            // Niente più tasto AI separato in header: "AILA Assistant" è già la prima scelta
            // dentro il foglio che si apre da "Aggiungi", un tasto in più qui era ridondante.
            action = {
                AilaIconButton(contentDescription = "Aggiungi evento", onClick = onAddEventClick, primary = true, opensPage = true) { tint ->
                    AppIcons.Plus(modifier = Modifier.size(18.dp), color = tint)
                }
            }
        )

        val monthSection: @Composable () -> Unit = {
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

                    // Cambiando mese (frecce, o un evento appena creato in un altro mese) la griglia
                    // scorre di lato e l'altezza segue con una molla: sei righe o cinque, senza
                    // salti. Ogni griglia disegna il suo mese, anche mentre esce.
                    androidx.compose.animation.AnimatedContent(
                        targetState = visibleYear * 100 + visibleMonth,
                        modifier = Modifier.fillMaxWidth().clipToBounds(),
                        transitionSpec = {
                            val forward = targetState > initialState
                            androidx.compose.animation.ContentTransform(
                                targetContentEnter = androidx.compose.animation.fadeIn(
                                    androidx.compose.animation.core.tween(220, delayMillis = 60)
                                ) + androidx.compose.animation.slideInHorizontally(
                                    androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 400f)
                                ) { w -> if (forward) w / 4 else -w / 4 },
                                initialContentExit = androidx.compose.animation.fadeOut(
                                    androidx.compose.animation.core.tween(120)
                                ) + androidx.compose.animation.slideOutHorizontally(
                                    androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 400f)
                                ) { w -> if (forward) -w / 4 else w / 4 },
                                sizeTransform = androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                                    androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 400f)
                                }
                            )
                        },
                        label = "monthGrid"
                    ) { key ->
                        val gridYear = key / 100
                        val gridMonth = key % 100
                        val gridDays = remember(gridYear, gridMonth) { daysInMonth(gridYear, gridMonth) }
                        val gridBlanks = remember(gridYear, gridMonth) { firstWeekdayOfMonth(gridYear, gridMonth) }
                        val gridDotDays = remember(events, gridYear, gridMonth, selectedCategoryFilter) {
                            events
                                .filter { selectedCategoryFilter == null || it.category == selectedCategoryFilter }
                                .mapNotNull { event -> parseIsoDate(event.date) }
                                .filter { it.year == gridYear && it.month == gridMonth }
                                .map { it.day }
                                .toSet()
                        }
                        MonthGrid(
                            daysCount = gridDays,
                            leadingBlanks = gridBlanks,
                            selectedDay = selectedDay,
                            today = todayDate.takeIf { it.year == gridYear && it.month == gridMonth },
                            daysWithEvents = gridDotDays,
                            onSelectDay = { selectedDay = it }
                        )
                    }
                }
            }
        }
        val filtersSection: @Composable () -> Unit = {
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
        }
        val daySection: @Composable () -> Unit = {
            AilaSectionTitle(
                text = "${selectedDay} ${ITALIAN_MONTHS.getOrElse(visibleMonth) { "" }}",
                modifier = Modifier.ailaAppear(2),
                // "+" per creare un evento legato proprio a questo giorno: prima l'unico modo di
                // aggiungere un evento era il tasto "Aggiungi" in alto, che non sapeva quale giorno
                // si stesse guardando e obbligava a riscegliere la data da zero.
                actionText = if (isPastDay) null else "+ Aggiungi",
                onActionClick = if (isPastDay) null else ({ onAddEventForDayClick(selectedDateIso) })
            )

            Spacer(modifier = Modifier.height(AppTheme.Space12))

            // La molla sull'altezza: la card "Nessun evento" lascia il posto al nuovo evento, e le
            // card sotto scendono, invece di saltare.
            Column(
                modifier = Modifier.fillMaxWidth().animateContentSize(
                    androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 380f)
                )
            ) {
                if (selectedEvents.isEmpty()) {
                    AilaCard(modifier = Modifier.ailaAppear(3)) {
                        AilaEmptyState(
                            title = "Nessun evento",
                            message = "Niente in programma per questo giorno.",
                            actionLabel = if (isPastDay) null else "Aggiungi evento",
                            onAction = if (isPastDay) null else ({ onAddEventForDayClick(selectedDateIso) }),
                            icon = { AppIcons.Calendar(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                        )
                    }
                } else {
                    selectedEvents.forEachIndexed { index, event ->
                        key(event.id) {
                            // Un evento appena creato parte nascosto, aspetta che il foglio
                            // "Nuovo evento" finisca di scendere, poi si apre con una molla e
                            // resta un attimo evidenziato da un contorno che sfuma. Gli altri
                            // sono gia' visibili (niente animazione cambiando giorno).
                            val animateIn = remember { event.id in freshEventIds && consumedFreshIds.add(event.id) }
                            val visibleState = remember {
                                androidx.compose.animation.core.MutableTransitionState(!animateIn)
                            }
                            val highlight = remember { androidx.compose.animation.core.Animatable(if (animateIn) 1f else 0f) }
                            // Eliminata (dal foglio o dal cestino): la card esce chiudendosi, poi il
                            // chiamante la toglie davvero. Se l'eliminazione fallisce rientra.
                            val removing = event.id in removingEventIds
                            val wasRemoving = remember { arrayOf(false) }
                            LaunchedEffect(removing) {
                                if (removing) {
                                    wasRemoving[0] = true
                                    visibleState.targetState = false
                                } else if (wasRemoving[0]) {
                                    wasRemoving[0] = false
                                    visibleState.targetState = true
                                }
                            }
                            LaunchedEffect(Unit) {
                                if (animateIn) delay(240)
                                visibleState.targetState = true
                                if (animateIn) {
                                    delay(300)
                                    highlight.animateTo(0f, androidx.compose.animation.core.tween(1500))
                                }
                            }
                            val highlightColor = AppTheme.PrimaryBlue
                            val highlightRadius = AppTheme.CardCornerRadius
                            androidx.compose.animation.AnimatedVisibility(
                                visibleState = visibleState,
                                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)) +
                                    androidx.compose.animation.shrinkVertically(
                                        androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 420f)
                                    ) +
                                    androidx.compose.animation.scaleOut(
                                        androidx.compose.animation.core.tween(220),
                                        targetScale = 0.92f
                                    ),
                                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(220)) +
                                    androidx.compose.animation.expandVertically(
                                        androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 380f)
                                    ) +
                                    androidx.compose.animation.scaleIn(
                                        androidx.compose.animation.core.spring(dampingRatio = 0.75f, stiffness = 420f),
                                        initialScale = 0.9f
                                    )
                            ) {
                                Column {
                                    CalendarEventCard(
                                        event = event,
                                        onClick = { onEventClick(event) },
                                        modifier = Modifier
                                            .ailaAppear(index + 3)
                                            .drawWithContent {
                                                drawContent()
                                                val a = highlight.value
                                                if (a > 0f) {
                                                    val stroke = 2.dp.toPx()
                                                    drawRoundRect(
                                                        color = highlightColor.copy(alpha = 0.7f * a),
                                                        topLeft = Offset(stroke / 2, stroke / 2),
                                                        size = Size(size.width - stroke, size.height - stroke),
                                                        cornerRadius = CornerRadius(highlightRadius.toPx()),
                                                        style = Stroke(width = stroke)
                                                    )
                                                }
                                            }
                                    )
                                    if (index != selectedEvents.lastIndex) {
                                        Spacer(modifier = Modifier.height(AppTheme.Space12))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val listPadding = Modifier
            .padding(horizontal = AppTheme.Space16)
            .padding(top = AppTheme.Space16, bottom = AppTheme.Space32 + circolareplus.design.LocalBottomBarPadding.current)
        if (circolareplus.design.LocalWideLayout.current) {
            // Tablet e iPad larghi: il mese a sinistra, i filtri e gli eventi del giorno scelto a
            // destra, ognuno col suo scorrimento: toccando un giorno i suoi eventi compaiono
            // accanto invece che sotto al mese.
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.Space4)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .then(listPadding)
                ) {
                    monthSection()
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .then(listPadding)
                ) {
                    filtersSection()
                    Spacer(modifier = Modifier.height(AppTheme.Space20))
                    daySection()
                    // Sotto al giorno scelto, i prossimi eventi (con il filtro attivo): la colonna
                    // resta piena e si vede cosa arriva senza toccare un giorno dopo l'altro.
                    val upcoming = remember(events, selectedCategoryFilter, visibleYear, visibleMonth, selectedDay) {
                        upcomingEvents(
                            events.filter { selectedCategoryFilter == null || it.category == selectedCategoryFilter },
                            limit = 12
                        ).filter { event ->
                            val date = parseIsoDate(event.date.take(10).trim())
                            date == null || date.year != visibleYear || date.month != visibleMonth || date.day != selectedDay
                        }.take(6)
                    }
                    if (upcoming.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(AppTheme.Space24))
                        AilaSectionTitle(text = "In arrivo")
                        Spacer(modifier = Modifier.height(AppTheme.Space12))
                        upcoming.forEachIndexed { index, event ->
                            CalendarEventCard(
                                event = event,
                                onClick = {
                                    // Tocco su un evento in arrivo: il mese e il giorno vanno su di lui.
                                    parseIsoDate(event.date)?.let {
                                        visibleYear = it.year
                                        visibleMonth = it.month
                                        selectedDay = it.day
                                    }
                                },
                                showDate = true
                            )
                            if (index != upcoming.lastIndex) {
                                Spacer(modifier = Modifier.height(AppTheme.Space12))
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .then(listPadding)
            ) {
                monthSection()
                Spacer(modifier = Modifier.height(AppTheme.Space20))
                filtersSection()
                Spacer(modifier = Modifier.height(AppTheme.Space20))
                daySection()
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
        androidx.compose.animation.AnimatedContent(
            targetState = year * 100 + month,
            transitionSpec = {
                androidx.compose.animation.ContentTransform(
                    targetContentEnter = androidx.compose.animation.fadeIn(
                        androidx.compose.animation.core.tween(200, delayMillis = 60)
                    ),
                    initialContentExit = androidx.compose.animation.fadeOut(
                        androidx.compose.animation.core.tween(100)
                    )
                )
            },
            label = "monthTitle"
        ) { key ->
            Text(
                text = "${ITALIAN_MONTHS.getOrElse(key % 100) { "" }} ${key / 100}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark
            )
        }
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
        modifier = modifier.heightIn(min = 46.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // Larga al massimo 40dp ma mai piu' della sua colonna: sui telefoni stretti (320-340dp,
            // o schermo con "Dimensioni visualizzazione" grandi) sette celle da 40dp fissi non ci
            // stavano e l'evidenziazione del giorno scelto si sovrapponeva a quelli accanto.
            // Altezza minima e non fissa: col testo di sistema ingrandito il numero non si taglia.
            modifier = Modifier
                .widthIn(max = 40.dp)
                .fillMaxWidth()
                .heightIn(min = 42.dp)
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
    modifier: Modifier = Modifier,
    /** Scrive anche il giorno accanto all'ora (elenco "In arrivo", fuori dal giorno scelto). */
    showDate: Boolean = false
) {
    // transformKey: al ritorno il dettaglio si richiude su questa card, dove sta adesso.
    AilaCard(onClick = onClick, modifier = modifier, transformKey = "event:${event.id}") {
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
                val dayLabel = if (showDate) {
                    parseIsoDate(event.date.take(10).trim())?.let { "${it.day} ${ITALIAN_MONTHS.getOrElse(it.month) { "" }}" }
                } else null
                Text(
                    text = listOfNotNull(dayLabel, event.time ?: "Tutto il giorno").joinToString(" \u00B7 "),
                    fontSize = 13.sp,
                    color = AppTheme.TextMuted
                )
                if (event.isAiGenerated) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AilaAssistantBadge(text = "Inserito da AILA Assistant")
                }
            }

            Spacer(modifier = Modifier.width(AppTheme.Space8))

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
