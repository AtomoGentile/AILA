package circolareplus.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import circolareplus.util.plusDays
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
    removingEventIds: Set<String> = emptySet(),
    classLabel: String? = null,
    subjectColorsRevision: Int = 0
) {
    val todayDate = remember { today() }
    val morph = rememberSaveable(saver = CalendarMorphState.saver) { CalendarMorphState() }
    val morphScope = rememberCoroutineScope()
    // Reduced motion keeps direct gesture feedback on the handle but avoids spatial morphing.
    val progress = if (AppTheme.reduceMotion) { if (morph.timetable) 1f else 0f } else morph.progress
    circolareplus.platform.PlatformBackHandler(enabled = morph.progress > 0f || morph.timetable) {
        morph.settle(morphScope, false, AppTheme.reduceMotion)
    }

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
        morph.settle(morphScope, false, AppTheme.reduceMotion)
        onFocusConsumed()
    }
    val selectedDateIso = remember(visibleYear, visibleMonth, selectedDay) {
        CivilDate(visibleYear, visibleMonth, selectedDay).toIso()
    }
    // Nei giorni gia' passati non si creano eventi: niente "+ Aggiungi" (le date sono "AAAA-MM-GG",
    // quindi il confronto fra stringhe e' quello fra date).
    val isPastDay = selectedDateIso < todayDate.toIso()

    val selectedDate = CivilDate(visibleYear, visibleMonth, selectedDay)
    val changeDate: (CivilDate) -> Unit = { date ->
        visibleYear = date.year
        visibleMonth = date.month
        selectedDay = date.day
    }
    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
    ) {
        AilaScreenHeader(
            title = if (morph.timetable) "Orario" else "Calendario",
            // Niente più tasto AI separato in header: "AILA Assistant" è già la prima scelta
            // dentro il foglio che si apre da "Aggiungi", un tasto in più qui era ridondante.
            action = {
                AilaIconButton(contentDescription = "Aggiungi evento", onClick = onAddEventClick, primary = true, opensPage = true) { tint ->
                    AppIcons.Plus(modifier = Modifier.size(18.dp), color = tint)
                }
            }
        )

        val monthSection: @Composable () -> Unit = {
            // Same surface throughout: it opens into the page as its corner and tint recede.
            CalendarMorphSurface(progress) {
                Column(modifier = Modifier.padding(AppTheme.Space12)) {
                    MonthNavigator(
                        year = visibleYear,
                        month = visibleMonth,
                        date = selectedDate,
                        progress = progress,
                        onToday = { changeDate(today()) },
                        onPrevious = {
                            if (morph.progress == 0f || morph.progress == 1f) {
                                if (morph.timetable) changeDate(selectedDate.plusDays(-1)) else {
                                    val (y, m) = previousMonth(visibleYear, visibleMonth)
                                    visibleYear = y
                                    visibleMonth = m
                                    selectedDay = selectedDay.coerceAtMost(daysInMonth(y, m))
                                }
                            }
                        },
                        onNext = {
                            if (morph.progress == 0f || morph.progress == 1f) {
                                if (morph.timetable) changeDate(selectedDate.plusDays(1)) else {
                                    val (y, m) = nextMonth(visibleYear, visibleMonth)
                                    visibleYear = y
                                    visibleMonth = m
                                    selectedDay = selectedDay.coerceAtMost(daysInMonth(y, m))
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(AppTheme.Space12))

                    Row(modifier = Modifier.fillMaxWidth()
                        .graphicsLayer { alpha = (1f - progress * 5f).coerceAtLeast(0f) }
                        .morphCollapse(1f - ((progress - 0.2f) / 0.8f).coerceIn(0f, 1f))) {
                        ITALIAN_WEEKDAY_INITIALS.forEach { initial ->
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.labelSmall,
                                color = AppTheme.TextFaint,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AppTheme.Space8))

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
                                    circolareplus.design.ailaFadeSpec(220, delayMillis = 60)
                                ) + androidx.compose.animation.slideInHorizontally(
                                    circolareplus.design.ailaNavigationSpring()
                                ) { w -> if (forward) w / 4 else -w / 4 },
                                initialContentExit = androidx.compose.animation.fadeOut(
                                    circolareplus.design.ailaFadeSpec(120)
                                ) + androidx.compose.animation.slideOutHorizontally(
                                    circolareplus.design.ailaNavigationSpring()
                                ) { w -> if (forward) -w / 4 else w / 4 },
                                sizeTransform = androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                                    circolareplus.design.ailaNavigationSpring()
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
                            onSelectDay = { if (morph.progress == 0f) selectedDay = it },
                            progress = progress,
                            monthLabel = ITALIAN_MONTHS.getOrElse(gridMonth) { "" }
                        )
                    }
                }
                CalendarMorphHandle(morph)
            }
        }
        val filtersSection: @Composable () -> Unit = {
            // Mostra tutti i tag effettivamente usati nel calendario, non solo tre categorie.
            val categoryFilterOptions = remember {
                listOf<CalendarEventCategory?>(null) + CalendarEventCategory.entries
            }
            circolareplus.design.AilaSlidingChipRow(
                selectedIndex = categoryFilterOptions.indexOf(selectedCategoryFilter),
                itemCount = categoryFilterOptions.size,
                modifier = Modifier.fillMaxWidth().ailaAppear(1)
            ) { chipModifier ->
                categoryFilterOptions.forEachIndexed { index, category ->
                    val label = when (category) {
                        null -> "Tutte"
                        CalendarEventCategory.VERIFICA -> "Verifiche"
                        CalendarEventCategory.INTERROGAZIONE -> "Interrogazioni"
                        CalendarEventCategory.PAGAMENTO -> "Pagamenti"
                        CalendarEventCategory.USCITA_DIDATTICA -> "Uscite"
                        CalendarEventCategory.ENTRATA -> "Entrate"
                        CalendarEventCategory.AVVISO -> "Avvisi"
                        CalendarEventCategory.ALTRO -> "Altro"
                    }
                    AnimatedFilterChip(
                        label = label,
                        isSelected = selectedCategoryFilter == category,
                        onClick = { selectedCategoryFilter = category },
                        drawSelectionBackground = false,
                        modifier = chipModifier(index)
                    )
                }
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
                    circolareplus.design.ailaNavigationSpring()
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
                                    highlight.animateTo(0f, circolareplus.design.ailaFadeSpec(1500))
                                }
                            }
                            val highlightColor = AppTheme.PrimaryBlue
                            val highlightRadius = AppTheme.CardCornerRadius
                            androidx.compose.animation.AnimatedVisibility(
                                visibleState = visibleState,
                                exit = androidx.compose.animation.fadeOut(circolareplus.design.ailaFadeSpec(180)) +
                                    androidx.compose.animation.shrinkVertically(
                                        circolareplus.design.ailaNavigationSpring()
                                    ) +
                                    androidx.compose.animation.scaleOut(
                                        circolareplus.design.ailaMoveSpec(220),
                                        targetScale = 0.92f
                                    ),
                                enter = androidx.compose.animation.fadeIn(circolareplus.design.ailaFadeSpec(220)) +
                                    androidx.compose.animation.expandVertically(
                                        circolareplus.design.ailaBouncySpring()
                                    ) +
                                    androidx.compose.animation.scaleIn(
                                        circolareplus.design.ailaBouncySpring(),
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
            CalendarMorphWideLayout(progress, Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .then(listPadding)
                ) {
                    monthSection()
                    Column(Modifier.fillMaxWidth().morphCollapse(progress)) {
                        TimetableTimeline(classLabel, selectedDate, progress, subjectColorsRevision)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .graphicsLayer { alpha = 1f - progress }
                        .morphInactive(progress > 0f)
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
                Column(Modifier.fillMaxWidth().morphCollapse(progress)) {
                    TimetableTimeline(classLabel, selectedDate, progress, subjectColorsRevision)
                }
                Column(Modifier.fillMaxWidth().morphCollapse(1f - progress)) {
                    Spacer(modifier = Modifier.height(AppTheme.Space20))
                    filtersSection()
                    Spacer(modifier = Modifier.height(AppTheme.Space20))
                    daySection()
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
    date: CivilDate,
    progress: Float,
    onToday: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        MonthArrow(contentDescription = if (progress == 1f) "Giorno precedente" else "Mese precedente", onClick = onPrevious) {
            AppIcons.ChevronLeft(modifier = Modifier.size(18.dp), color = AppTheme.TextDark)
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedContent(
            targetState = year * 100 + month,
            modifier = Modifier.graphicsLayer { alpha = (1f - progress * 2f).coerceAtLeast(0f) }
                .then(if (progress > 0f) Modifier.clearAndSetSemantics { } else Modifier),
            transitionSpec = {
                androidx.compose.animation.ContentTransform(
                    targetContentEnter = androidx.compose.animation.fadeIn(
                        circolareplus.design.ailaFadeSpec(200, delayMillis = 60)
                    ),
                    initialContentExit = androidx.compose.animation.fadeOut(
                        circolareplus.design.ailaFadeSpec(100)
                    )
                )
            },
            label = "monthTitle"
        ) { key ->
            Text(
                text = "${ITALIAN_MONTHS.getOrElse(key % 100) { "" }} ${key / 100}",
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = AppTheme.TextDark
            )
        }
        Text(circolareplus.util.ITALIAN_WEEKDAYS[circolareplus.util.weekdayOf(date)].replaceFirstChar { it.uppercase() } + " · ${date.year}",
            style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = ((progress - 0.5f) * 2f).coerceAtLeast(0f) }
                .then(if (progress < 1f) Modifier.clearAndSetSemantics { } else Modifier))
        }
        androidx.compose.material3.TextButton(
            onClick = onToday,
            modifier = Modifier.height(44.dp)
                .graphicsLayer { alpha = ((progress - 0.7f) / 0.3f).coerceIn(0f, 1f) }
                .morphInactive(progress < 1f).clipToBounds().layout { measurable, constraints ->
                    val child = measurable.measure(constraints.copy(minWidth = 0))
                    layout((child.width * progress).toInt(), child.height) { child.place(0, 0) }
                }.width(64.dp)
        ) { Text("Oggi", maxLines = 1, color = AppTheme.AccentCyan) }
        MonthArrow(contentDescription = if (progress == 1f) "Giorno successivo" else "Mese successivo", onClick = onNext) {
            AppIcons.ChevronRight(modifier = Modifier.size(18.dp), color = AppTheme.TextDark)
        }
    }
}

/**
 * Freccia del mese: era un riquadro da 36dp senza nome (il lettore di schermo diceva solo
 * "pulsante"). Ora e' l'AilaIconButton di sistema: 44dp di tocco, nome parlato e lo stesso
 * aspetto del "+" nell'intestazione della stessa schermata.
 */
@Composable
private fun MonthArrow(contentDescription: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    AilaIconButton(contentDescription = contentDescription, onClick = onClick) { _ ->
        icon()
    }
}

/** The selected cell itself becomes the full-width date heading. No duplicate hero. */
@Composable
private fun MonthGrid(
    daysCount: Int,
    leadingBlanks: Int,
    selectedDay: Int,
    today: CivilDate?,
    daysWithEvents: Set<Int>,
    onSelectDay: (Int) -> Unit,
    progress: Float = 0f,
    monthLabel: String = ""
) {
    Layout(content = {
        for (day in 1..daysCount) {
            val selected = day == selectedDay
            DayCell(day = day, isSelected = selected, isToday = today?.day == day,
                hasEvents = day in daysWithEvents, onClick = { onSelectDay(day) },
                progress = if (selected) progress else 0f, monthLabel = monthLabel,
                modifier = Modifier.graphicsLayer {
                    alpha = if (selected) 1f else (1f - progress * 1.8f).coerceAtLeast(0f)
                }.then(if (!selected && progress > 0f) Modifier.clearAndSetSemantics { } else Modifier))
        }
    }, modifier = Modifier.fillMaxWidth().clipToBounds()) { measurables, constraints ->
        val width = constraints.maxWidth
        val cellWidth = width / 7
        val children = measurables.mapIndexed { index, measurable ->
            val selected = index + 1 == selectedDay
            val w = if (selected) (cellWidth + (width - cellWidth) * progress).toInt() else cellWidth
            measurable.measure(constraints.copy(minWidth = w, maxWidth = w, minHeight = 0))
        }
        val rowHeight = children.maxOfOrNull { it.height } ?: 0
        val rows = (leadingBlanks + daysCount + 6) / 7
        val fullHeight = rows * rowHeight + (rows - 1) * 2.dp.roundToPx()
        val height = (fullHeight + (rowHeight - fullHeight) * progress).toInt()
        layout(width, height) {
            // Place the selected child last so it remains above fading neighbours.
            children.indices.sortedBy { if (it + 1 == selectedDay) 1 else 0 }.forEach { index ->
                val cell = leadingBlanks + index
                val x = (cell % 7 * cellWidth * (1f - progress)).toInt()
                val y = (cell / 7 * (rowHeight + 2.dp.roundToPx()) * (1f - progress)).toInt()
                children[index].placeRelative(x, y)
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
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    monthLabel: String = ""
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
                .widthIn(max = if (progress == 0f) 40.dp else androidx.compose.ui.unit.Dp.Infinity)
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                .then(
                    when {
                        isSelected -> if (progress == 0f) Modifier.background(AppTheme.PrimaryGradient)
                            else Modifier.background(AppTheme.PrimaryBlue.copy(alpha = 1f - progress))
                        isToday -> Modifier.border(
                            1.5.dp,
                            AppTheme.PrimaryBlue,
                            RoundedCornerShape(AppTheme.SmallElementRadius)
                        )
                        else -> Modifier
                    }
                )
                .then(if (progress == 0f) Modifier.clickable { onClick() } else Modifier)
                .padding(top = 7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                text = "$day",
                fontSize = (14f + 6f * progress).sp,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isSelected -> androidx.compose.ui.graphics.lerp(Color.White, AppTheme.AccentCyan, progress)
                    isToday -> AppTheme.PrimaryBlue
                    else -> AppTheme.TextDark
                }
            )
            if (progress > 0f) Text(" $monthLabel", style = MaterialTheme.typography.titleMedium,
                color = AppTheme.TextDark, maxLines = 1, softWrap = false,
                modifier = Modifier.graphicsLayer { alpha = ((progress - 0.33f) / 0.5f).coerceIn(0f, 1f) }.clipToBounds().layout { measurable, constraints ->
                    val child = measurable.measure(constraints.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
                    layout((child.width * (progress * 3f).coerceAtMost(1f)).toInt(), child.height) { child.place(0, 0) }
                })
            }
            Spacer(modifier = Modifier.height(AppTheme.Space4))
            AilaDot(
                color = when {
                    !hasEvents || progress > 0f -> Color.Transparent
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
                    style = MaterialTheme.typography.titleSmall,
                    color = AppTheme.TextDark,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(2.dp))
                val dayLabel = if (showDate) {
                    parseIsoDate(event.date.take(10).trim())?.let { "${it.day} ${ITALIAN_MONTHS.getOrElse(it.month) { "" }}" }
                } else null
                Text(
                    text = listOfNotNull(dayLabel, event.time ?: "Tutto il giorno").joinToString(" \u00B7 "),
                    style = MaterialTheme.typography.bodyMedium,
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
                    Text(text = "Tutti", style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted)
                }
            }
        }
    }
}
