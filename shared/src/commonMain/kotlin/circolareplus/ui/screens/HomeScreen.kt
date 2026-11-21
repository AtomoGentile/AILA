package circolareplus.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaCard
import circolareplus.design.AilaIconTile
import circolareplus.design.AilaListRow
import circolareplus.design.AilaProfileEntry
import circolareplus.design.LocalProfileEntry
import circolareplus.design.AilaSectionTitle
import circolareplus.design.ailaAppear
import circolareplus.design.ailaGlassOverlay
import circolareplus.design.ailaGlassSurface
import circolareplus.design.ailaGlassPressable
import circolareplus.design.ailaMorphClip
import circolareplus.design.ailaMoveSpec
import circolareplus.design.ailaNavigationSpring
import circolareplus.design.ailaSpatialSpring
import circolareplus.design.ailaTransformOrigin
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.util.nowMinutesOfDay
import circolareplus.util.parseIsoDate
import circolareplus.util.parseTimeToMinutes
import circolareplus.util.today
import circolareplus.util.plusDays
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.Circular
import kotlin.math.PI
import kotlin.math.sin

/**
 * Home in stile AILA: pannello superiore a gradiente con il saluto e le quattro icone di accesso
 * rapido, poi la circolare in evidenza, i prossimi eventi e le scorciatoie su fondo chiaro.
 *
 * Qui c'era anche un secondo ramo "stile iOS" con il pannello esteso sotto le prime card, il
 * vetro sfocato (libreria Haze) e la barra di navigazione traslucida. È stato rimosso insieme a
 * tutto lo stile iOS: l'app usa Material 3 su tutte le piattaforme.
 */
@Composable
fun HomeScreen(
    studentFirstName: String = "Simone",
    circulars: List<Circular> = emptyList(),
    calendarEvents: List<CalendarEvent> = emptyList(),
    openProposalsCount: Int = 0,
    onNavigateToCircularDetail: (Int) -> Unit = {},
    onNavigateToBoard: () -> Unit = {},
    onNavigateToPolls: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    /** Evento toccato in "Prossimi eventi": apre il calendario su quel giorno. */
    onEventClick: (CalendarEvent) -> Unit = { onNavigateToCalendar() },
    onNavigateToCirculars: () -> Unit = {},
    onNavigateToGita: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    // Primo caricamento in corso: le card vuote dicono "carico" invece di "non c'e' niente".
    isCircularsLoading: Boolean = false,
    experience: circolareplus.data.remote.dto.AcaExperienceDto = circolareplus.data.remote.dto.AcaExperienceDto(),
    onOpenAca: () -> Unit = {},
    briefItems: List<circolareplus.data.remote.dto.AilaBriefItemDto> = emptyList(),
    briefLoading: Boolean = false,
    briefError: String? = null,
    onRefreshBrief: () -> Unit = {},
    onDismissBrief: (circolareplus.data.remote.dto.AilaBriefItemDto) -> Unit = {},
    onOpenBrief: (circolareplus.data.remote.dto.AilaBriefItemDto) -> Unit = {},
    onPlanStudy: (String) -> Unit = {},
    aiEnabled: Boolean = true,
    isEventsLoading: Boolean = false,
    subjectColorsRevision: Int = 0,
    subjectColorsEnabled: Boolean = false,
    subjectColorOverrides: Map<String, String> = emptyMap(),
    calendarUnavailable: Boolean = false,
    homeVisible: Boolean = true,
    onNavigateToBureaucracy: () -> Unit = {},
    onNavigateToClassRoster: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    unreadMenus: Set<String> = emptySet(),
    profileEntry: AilaProfileEntry? = LocalProfileEntry.current,
    isCurrentTab: Boolean = true
) {
    val scrollState = rememberScrollState()
    val homeGutter = if(AppTheme.isNothing) circolareplus.design.NothingSpacing.Edge else AppTheme.Space16
    val sectionGap = if(AppTheme.isNothing) circolareplus.design.NothingSpacing.Section else AppTheme.Space20

    // Circolare più recente (numero più alto): mostrata in evidenza, se ce n'è almeno una.
    val latestCircular = circulars.maxByOrNull { it.number } ?: circulars.maxByOrNull { it.number }
    // Prime 3 scadenze **future**. Prima si prendevano le prime tre della lista e basta, poi si
    // confrontavano le date come stringhe: un "2026-9-5" scritto senza zeri (arriva dagli eventi
    // generati dall'AI) risultava "maggiore" di "2026-09-20" e una verifica di settembre restava
    // per mesi fra i prossimi eventi. Ora la data si legge davvero, e un evento di oggi la cui ora
    // e' gia' passata non e' piu' "prossimo".
    // Tablet e iPad larghi: c'e' spazio per piu' eventi e per le ultime circolari, invece di
    // lasciare meta' schermo vuoto sotto le tre righe del telefono.
    val todayDate = today()
    val lastBriefDate = todayDate.plusDays(7).toIso()
    val allConflicts = remember(calendarEvents) { calendarConflicts(calendarEvents) }
    val briefConflicts = remember(allConflicts, todayDate) {
        allConflicts.filter { it.date in todayDate.toIso()..lastBriefDate }
    }
    val wide = circolareplus.design.LocalWideLayout.current
    val nextEvents = remember(calendarEvents, wide, briefItems) {
        val events = calendarPreviewWithoutBriefDuplicates(calendarEvents, briefItems)
        upcomingEvents(events, limit = if (wide) 6 else 3)
    }
    val recentCirculars = remember(circulars, wide, aiEnabled) {
        if (wide) circulars.sortedByDescending { it.number }.take(5) else emptyList()
    }

    val shortcuts = HomeShortcuts(
        onNavigateToClassRoster, onNavigateToBureaucracy, onNavigateToGita,
        onNavigateToNotifications, unreadMenus
    )
    if(AppTheme.isNothing) {
        NothingHomeScreen(studentFirstName,nextEvents,latestCircular,
            circolareplus.ai.AcaPolicyState.preferences.let {it.aiEnabled && it.acaEnabled && it.nudgesEnabled && "NOW_NUDGE" in it.services} ||
                briefItems.any { it.kind == circolareplus.data.remote.dto.AilaBriefKind.BUREAUCRACY },
            briefItems,briefLoading,briefError,onRefreshBrief,onOpenBrief,onDismissBrief,onPlanStudy,
            subjectColorsEnabled,subjectColorOverrides,NothingHomeActions(onNavigateToSearch, onNavigateToCalendar,
                onEventClick, onNavigateToCircularDetail, shortcuts),
            isEventsLoading,isCircularsLoading, briefConflicts, calendarUnavailable, homeVisible, glyphConflicts = allConflicts, profileEntry = profileEntry)
        return
    }

    // Circolare più recente in evidenza. Tipo esplicito sulla callback: un lambda scritto dentro
    // un "if" come argomento nullable è ambiguo da leggere e da inferire.
    val openLatestCircular: (() -> Unit)? =
        if (latestCircular != null) {
            { onNavigateToCircularDetail(latestCircular.number) }
        } else {
            null
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
            .verticalScroll(scrollState)
    ) {
        val briefAllowed = circolareplus.ai.AcaPolicyState.preferences.let {
            it.aiEnabled && it.acaEnabled && it.nudgesEnabled && "NOW_NUDGE" in it.services
        }
        HomeHeroPanel(
            studentFirstName = studentFirstName,
            isCurrentTab = isCurrentTab,
            onNavigateToSearch = onNavigateToSearch,
            shortcuts = shortcuts,
            profileEntry = profileEntry
        )

        if (briefAllowed || briefItems.any { it.kind == circolareplus.data.remote.dto.AilaBriefKind.BUREAUCRACY }) {
            AilaBriefCard(briefItems, onDismissBrief, onOpenBrief, onPlanStudy, briefLoading, briefError, onRefreshBrief,
                Modifier.padding(horizontal = homeGutter).padding(top = if(AppTheme.isNothing) circolareplus.design.NothingSpacing.Section else 18.dp, bottom = if(AppTheme.isNothing) 0.dp else 4.dp),
                subjectColorsRevision = subjectColorsRevision,
                subjectColorsEnabled = subjectColorsEnabled,
                subjectColorOverrides = subjectColorOverrides,
                conflicts = briefConflicts, onOpenConflict = { onEventClick(it.first) })
        }

        val latestSection: @Composable () -> Unit = {
            LatestCircularCard(
                latestCircular = latestCircular,
                onClick = openLatestCircular,
                isLoading = isCircularsLoading,
                modifier = Modifier.ailaAppear(0)
            )
        }
        val eventsSection: @Composable () -> Unit = {
            AilaSectionTitle(
                text = "Prossimi eventi",
                actionText = "Vedi tutti",
                onActionClick = onNavigateToCalendar,
                modifier = Modifier.ailaAppear(1)
            )
            Spacer(modifier = Modifier.height(AppTheme.Space12))
            NextEventsCard(
                nextEvents = nextEvents,
                onNavigateToCalendar = onNavigateToCalendar,
                onEventClick = onEventClick,
                isLoading = isEventsLoading,
                modifier = Modifier.ailaAppear(2)
            )
        }
        val shortcutsSection: @Composable () -> Unit = {
            AilaSectionTitle(text = "Scorciatoie", modifier = Modifier.ailaAppear(3))

            Spacer(modifier = Modifier.height(AppTheme.Space12))

            AilaCard(modifier = Modifier.ailaAppear(4)) {
                AilaListRow(
                    title = "Sondaggi",
                    subtitle = "Date delle interrogazioni e opzioni da mettere in ordine",
                    tint = AppTheme.TintAmber,
                    onClick = onNavigateToPolls,
                    icon = {
                        AppIcons.Poll(modifier = Modifier.size(21.dp), color = AppTheme.TintAmberInk)
                    }
                )
                HorizontalDivider(color = AppTheme.Hairline, modifier = Modifier.padding(start = 72.dp))
                AilaListRow(
                    title = "Bacheca della classe",
                    subtitle = if (openProposalsCount > 0)
                        "$openProposalsCount proposte da leggere e votare"
                    else
                        "Proponi un'idea e falla votare ai compagni",
                    tint = AppTheme.TintViolet,
                    onClick = onNavigateToBoard,
                    icon = {
                        AppIcons.ChatBubble(modifier = Modifier.size(21.dp), color = AppTheme.TintVioletInk)
                    }
                )
            }
        }
        val bottomPadding = AppTheme.Space24 + circolareplus.design.LocalBottomBarPadding.current
        if (wide) {
            // Tablet e iPad larghi: ultime circolari a sinistra e prossimi eventi a destra, poi le
            // scorciatoie affiancate su tutta la larghezza.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = homeGutter)
                    .padding(top = sectionGap, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(AppTheme.Space24)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space20)) {
                    Column(modifier = Modifier.weight(1f)) {
                        AilaSectionTitle(
                            text = "Ultime circolari",
                            actionText = "Vedi tutte",
                            onActionClick = onNavigateToCirculars,
                            modifier = Modifier.ailaAppear(0)
                        )
                        Spacer(modifier = Modifier.height(AppTheme.Space12))
                        RecentCircularsCard(
                            circulars = recentCirculars,
                            onCircularClick = onNavigateToCircularDetail,
                            isLoading = isCircularsLoading,
                            modifier = Modifier.ailaAppear(1)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        eventsSection()
                    }
                }
                Column {
                    AilaSectionTitle(text = "Scorciatoie", modifier = Modifier.ailaAppear(3))
                    Spacer(modifier = Modifier.height(AppTheme.Space12))
                    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space20)) {
                        AilaCard(modifier = Modifier.weight(1f).ailaAppear(4)) {
                            AilaListRow(
                                title = "Sondaggi",
                                subtitle = "Date delle interrogazioni e opzioni da mettere in ordine",
                                tint = AppTheme.TintAmber,
                                onClick = onNavigateToPolls,
                                icon = {
                                    AppIcons.Poll(modifier = Modifier.size(21.dp), color = AppTheme.TintAmberInk)
                                }
                            )
                        }
                        AilaCard(modifier = Modifier.weight(1f).ailaAppear(5)) {
                            AilaListRow(
                                title = "Bacheca della classe",
                                subtitle = if (openProposalsCount > 0)
                                    "$openProposalsCount proposte da leggere e votare"
                                else
                                    "Proponi un'idea e falla votare ai compagni",
                                tint = AppTheme.TintViolet,
                                onClick = onNavigateToBoard,
                                icon = {
                                    AppIcons.ChatBubble(modifier = Modifier.size(21.dp), color = AppTheme.TintVioletInk)
                                }
                            )
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = homeGutter)
                    .padding(top = sectionGap, bottom = AppTheme.Space24)
            ) {
                latestSection()
                Spacer(modifier = Modifier.height(AppTheme.Space24))
                eventsSection()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = homeGutter)
                    .padding(bottom = bottomPadding)
            ) {
                shortcutsSection()
            }
        }
    }
}

/** Card della circolare più recente messa in evidenza in cima alla Home. */
@Composable
private fun LatestCircularCard(
    latestCircular: Circular?,
    onClick: (() -> Unit)?,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    AilaCard(onClick = onClick, modifier = modifier, transformKey = latestCircular?.let { "circular:${it.number}" }) {
        AilaListRow(
            title = if (latestCircular != null) "Circolare n. ${latestCircular.number}" else "Nuova circolare",
            subtitle = latestCircular?.title
                ?: if (isLoading) "Carico le circolari\u2026" else "Nessuna circolare disponibile al momento",
            tint = AppTheme.TintBlue,
            showChevron = latestCircular != null,
            icon = {
                AppIcons.Document(modifier = Modifier.size(21.dp), color = AppTheme.TintBlueInk)
            }
        )
    }
}

/** Le ultime circolari arrivate, una per riga (Home su schermi larghi). */
@Composable
private fun RecentCircularsCard(
    circulars: List<Circular>,
    onCircularClick: (Int) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (circulars.isEmpty()) {
        LatestCircularCard(latestCircular = null, onClick = null, isLoading = isLoading, modifier = modifier)
        return
    }
    AilaCard(modifier = modifier) {
        circulars.forEachIndexed { index, circular ->
            AilaListRow(
                title = "Circolare n. ${circular.number}",
                subtitle = circular.title,
                tint = AppTheme.TintBlue,
                onClick = { onCircularClick(circular.number) },
                icon = {
                    AppIcons.Document(modifier = Modifier.size(21.dp), color = AppTheme.TintBlueInk)
                }
            )
            if (index != circulars.lastIndex) {
                HorizontalDivider(color = AppTheme.Hairline, modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

/** Card "Prossimi eventi", o lo stato vuoto quando non c'è nessuna scadenza futura. */
@Composable
private fun NextEventsCard(
    nextEvents: List<CalendarEvent>,
    onNavigateToCalendar: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (nextEvents.isEmpty()) {
        AilaCard(modifier = modifier) {
            AilaListRow(
                title = if (isLoading) "Carico il calendario\u2026" else "Nessuna scadenza",
                subtitle = "Le verifiche e le scadenze della classe compaiono qui.",
                tint = AppTheme.TintSlate,
                onClick = onNavigateToCalendar,
                icon = {
                    AppIcons.Calendar(modifier = Modifier.size(21.dp), color = AppTheme.TintSlateInk)
                }
            )
        }
    } else {
        AilaCard(modifier = modifier) {
            nextEvents.forEachIndexed { index, event ->
                EventRow(event = event, onClick = { onEventClick(event) })
                if (index != nextEvents.lastIndex) {
                    HorizontalDivider(
                        color = AppTheme.Hairline,
                        modifier = Modifier.padding(start = 72.dp)
                    )
                }
            }
        }
    }
}

/** Pannello superiore a gradiente: saluto, campanella e le quattro icone di accesso rapido. */
@Composable
private fun HomeHeroPanel(
    studentFirstName: String,
    isCurrentTab: Boolean,
    onNavigateToSearch: () -> Unit,
    shortcuts: HomeShortcuts,
    profileEntry: AilaProfileEntry?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Liquid Glass: pannello di vetro vero (lo stesso delle card: velo chiaro, bagliore in
            // alto, bordo speculare), che lascia vedere lo sfondo a macchie invece di coprirlo con
            // un colore suo. Il gradiente colorato di prima sembrava un giocattolo.
            .then(
                // In alto gli angoli seguono la curva dello schermo del telefono: squadrati, il
                // bordo di luce e il riflesso in alto a sinistra finivano tagliati dalla curva.
                if (AppTheme.isGlass) {
                    val top = circolareplus.platform.displayCornerRadius()
                    Modifier.ailaGlassSurface(
                        RoundedCornerShape(topStart = top, topEnd = top, bottomStart = 32.dp, bottomEnd = 32.dp)
                    )
                }
                else Modifier
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(AppTheme.HeroGradient)
            )
            .homeHeroReflection(isCurrentTab)
            // Il pannello passa sotto la barra di stato (ora, Wi-Fi): prima sopra restava una
            // striscia del colore di fondo, staccata dal pannello.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(horizontal = AppTheme.Space20)
            .padding(top = AppTheme.Space24, bottom = AppTheme.Space24)
    ) {
        // Saluto a sinistra, azioni a destra (ricerca, notifiche, avatar). Il logo qui non
        // c'e': sul pannello della Home era di troppo (richiesta di Simone).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Ciao, $studentFirstName",
                style = MaterialTheme.typography.headlineMedium,
                color = AppTheme.OnHeroPrimary,
                // Fino a due righe: sugli iPhone piccoli, con tre pulsanti accanto, il nome veniva tagliato.
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(AppTheme.Space12))
            // Lente e campanella: la ricerca globale prima non aveva alcun punto d'ingresso.
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                HeroIconButton(contentDescription = "Cerca", onClick = onNavigateToSearch) {
                    AppIcons.Search(modifier = Modifier.size(21.dp), color = AppTheme.OnHeroPrimary)
                }
                profileEntry?.let { entry ->
                    circolareplus.design.AilaProfileButton(entry, onHero = true)
                }
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.Space24))

        HomeShortcutRow(shortcuts)
    }
}

internal data class HomeShortcuts(
    val openClass: () -> Unit = {},
    val openBureaucracy: () -> Unit = {},
    val openTrip: () -> Unit = {},
    val openNotifications: () -> Unit = {},
    val unreadMenus: Set<String> = emptySet()
)

@Composable
internal fun HomeShortcutRow(shortcuts: HomeShortcuts) {
    val tablet = circolareplus.design.isTabletWidth()
    val nothing = AppTheme.isNothing
    val iconColor = if (nothing) AppTheme.TextDark else AppTheme.OnHeroPrimary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (tablet) AppTheme.Space16 else AppTheme.Space8)
    ) {
        val tile = if (tablet) Modifier.width(96.dp) else Modifier.weight(1f)
        HomeQuickIcon("Classe", tile, shortcuts.openClass, "class" in shortcuts.unreadMenus) {
            AppIcons.People(Modifier.size(22.dp), iconColor)
        }
        HomeQuickIcon("Burocrazia", tile, shortcuts.openBureaucracy, "bureaucracy" in shortcuts.unreadMenus) {
            AppIcons.Document(Modifier.size(22.dp), iconColor)
        }
        HomeQuickIcon("Gita", tile, shortcuts.openTrip, "gita" in shortcuts.unreadMenus) {
            AppIcons.Bus(Modifier.size(22.dp), iconColor)
        }
        HomeQuickIcon("Notifiche", tile, shortcuts.openNotifications, "notifications" in shortcuts.unreadMenus) {
            AppIcons.Bell(Modifier.size(22.dp), iconColor)
        }
    }
}

/** Un riflesso attraversa il pannello quando la Home diventa la scheda corrente. */
@Composable
private fun Modifier.homeHeroReflection(isCurrentTab: Boolean): Modifier {
    if (!isCurrentTab || AppTheme.reduceMotion) return this

    val progress = remember { Animatable(0f) }
    val duration = if (AppTheme.isGlass) 900 else 720
    LaunchedEffect(duration) {
        progress.snapTo(0f)
        progress.animateTo(1f, ailaMoveSpec(duration))
    }

    val tint = when {
        !AppTheme.isGlass -> Color.White.copy(alpha = 0.20f)
        AppTheme.isDarkMode -> AppTheme.PrimaryBlue.copy(alpha = 0.24f)
        else -> AppTheme.PrimaryBlue.copy(alpha = 0.15f)
    }
    return drawWithCache {
        val radius = minOf(size.width * 0.58f, size.height * 1.18f)
        if (radius <= 0f) return@drawWithCache onDrawBehind {}
        val glow = Brush.radialGradient(
            0f to tint,
            0.55f to tint.copy(alpha = tint.alpha * 0.42f),
            1f to tint.copy(alpha = 0f),
            center = Offset.Zero,
            radius = radius
        )
        onDrawBehind {
            val fraction = progress.value
            if (fraction > 0f && fraction < 1f) {
                val position = 0.16f + 0.82f * fraction
                val horizontalFraction = if (layoutDirection == LayoutDirection.Ltr) position else 1f - position
                translate(left = size.width * horizontalFraction, top = size.height * 0.10f) {
                    drawCircle(
                        brush = glow,
                        radius = radius,
                        center = Offset.Zero,
                        alpha = sin(PI * fraction).toFloat()
                    )
                }
            }
        }
    }
}

/**
 * Riga di un evento dentro la card "Prossimi eventi": al posto del riquadro icona colorato c'è
 * il riquadro con giorno e mese abbreviato (stile mockup "12 MAG"), il resto è identico alle
 * altre righe di lista.
 */
@Composable
private fun EventRow(event: CalendarEvent, onClick: () -> Unit) {
    val (day, month) = parseDayMonth(event.date)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AilaIconTile(tint = AppTheme.TintBlue) {
            // Nel "biscotto" di Material i lobi in alto mangiano spazio: giorno e mese scendono di
            // un filo per stare al centro della forma.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = if (AppTheme.isGlass || AppTheme.isNothing) 0.dp else 3.dp)
            ) {
                Text(text = day, style = MaterialTheme.typography.titleSmall, color = AppTheme.TintBlueInk)
                // Material: il mese sale un poco (verso il giorno), il giorno resta dov'e'.
                Text(
                    text = month,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppTheme.TintBlueInk,
                    // -2 e non piu' -5.5: con la scala tipografica le due righe sono gia' piu'
                    // vicine (prima ciascuna era alta 24sp per l'interlinea ereditata).
                    modifier = Modifier.offset(y = if (AppTheme.isGlass || AppTheme.isNothing) 0.dp else (-2).dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(AppTheme.Space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.TextDark,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = eventSubtitle(event),
                style = MaterialTheme.typography.bodyMedium,
                color = AppTheme.TextMuted,
                maxLines = 1
            )
        }
    }
}

/**
 * Sottotitolo leggibile di un evento. Prima veniva stampato il nome grezzo della categoria
 * dell'enum (es. "USCITA DIDATTICA" tutto maiuscolo): nel mockup è una riga discorsiva.
 */
private fun eventSubtitle(event: CalendarEvent): String {
    val category = event.category.name
        .lowercase()
        .replace("_", " ")
        .replaceFirstChar { it.uppercase() }
    return event.time?.let { "$category • $it" } ?: "$category • Tutto il giorno"
}

/** Da "AAAA-MM-GG" a (giorno, mese abbreviato in italiano) per il riquadro data, es. ("12", "MAG").
 * Se il formato non è quello atteso, ripiega sulla stringa originale senza far crashare la Home. */
private fun parseDayMonth(isoDate: String): Pair<String, String> {
    val parts = isoDate.split("-")
    if (parts.size != 3) return isoDate to ""
    val monthNames = listOf("GEN", "FEB", "MAR", "APR", "MAG", "GIU", "LUG", "AGO", "SET", "OTT", "NOV", "DIC")
    val monthIndex = parts[1].toIntOrNull()?.minus(1)
    val month = monthIndex?.takeIf { it in monthNames.indices }?.let { monthNames[it] } ?: parts[1]
    val day = parts[2].toIntOrNull()?.toString() ?: parts[2]
    return day to month
}

/** Material: riempimento tonale dei pulsanti sul pannello, un tono piu' scuro del pannello. */
private val heroTonalFill: Color
    get() = AppTheme.AccentDeep

/** Tasto quadrato translucido nel pannello a gradiente (ricerca, notifiche). */
@Composable
private fun HeroIconButton(contentDescription: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    // Solo icona: senza un nome il lettore di schermo annunciava un pulsante muto.
    val a11y = Modifier.semantics {
        this.contentDescription = contentDescription
        role = Role.Button
    }
    if (!AppTheme.isGlass) {
        // Material Expressive: pulsante tonale pieno (un tono piu' scuro del pannello), tondo,
        // che alla pressione si schiaccia in un quadrato arrotondato. Niente vetro ne' bordo.
        val interactionSource = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(44.dp)
                // Toccato, la pagina (ricerca, notifiche) nasce da qui e ci rientra, come in Meteo.
                .ailaTransformOrigin(22.dp, buttonColor = heroTonalFill)
                .ailaMorphClip(interactionSource, pressedPercent = 28)
                .background(heroTonalFill)
                .clickable(interactionSource = interactionSource, indication = null) { onClick() }
                .then(a11y),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        return
    }
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(44.dp)
            .ailaGlassSurface(shape)
            .ailaGlassPressable(tint = AppTheme.PrimaryBlue) { onClick() }
            .then(a11y),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

/** Singola icona della riga di accesso rapido: riquadro translucido su gradiente + etichetta. */
@Composable
private fun HomeQuickIcon(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    hasUnread: Boolean = false,
    icon: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val glass = AppTheme.isGlass
    val nothing = AppTheme.isNothing
    val reduceMotion = AppTheme.reduceMotion
    // Material: riquadro tonale pieno che alla pressione stringe gli angoli (forma che si
    // deforma, come i pulsanti di M3 Expressive). Glass: vetro chiaro con filo di luce.
    val pressed by interactionSource.collectIsPressedAsState()
    val pressDepth = animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 1f else 0f,
        animationSpec = if (pressed) ailaNavigationSpring() else ailaSpatialSpring(),
        label = "quickIconPress"
    )
    val shape = RoundedCornerShape(
        if (glass) 18.dp else if (reduceMotion) 22.dp else (22f - 12f * pressDepth.value).dp
    )
    Column(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onClick
        ).then(if (hasUnread) Modifier.semantics { stateDescription = "Da leggere" } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .then(
                    if (glass) Modifier
                        .ailaGlassSurface(shape)
                        .ailaGlassOverlay(interactionSource, tint = AppTheme.PrimaryBlue)
                    else Modifier
                        .background(if (nothing) AppTheme.TrackFill else heroTonalFill)
                        .then(if (nothing) Modifier else Modifier
                        .ailaGlassOverlay(interactionSource, tint = Color.White.copy(alpha = 0.22f)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.graphicsLayer {
                val depth = if (reduceMotion) 0f else pressDepth.value
                translationY = 2.dp.toPx() * depth
                scaleX = 1f - 0.12f * depth
                scaleY = scaleX
            }) {
                icon()
            }
            if (hasUnread) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp)
                    .background(AppTheme.PrimaryBlue, CircleShape))
            }
        }
        Spacer(modifier = Modifier.height(AppTheme.Space8))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (nothing) AppTheme.TextDark else AppTheme.OnHeroSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2
        )
    }
}

/**
 * Gli eventi che non sono ancora iniziati, dal piu' vicino, al massimo [limit].
 *
 * Un evento senza data leggibile si scarta: meglio non mostrarlo che metterlo fra i prossimi a
 * caso. Uno di oggi senza ora resta finche' dura la giornata, uno con ora sparisce quando l'ora
 * e' passata.
 */
internal fun upcomingEvents(events: List<CalendarEvent>, limit: Int): List<CalendarEvent> {
    val todayDate = today()
    val todayKey = todayDate.year * 10_000 + todayDate.month * 100 + todayDate.day
    val nowMinutes = nowMinutesOfDay()

    return events
        .filter{it.isRelevantToMe!=false}
        .mapNotNull { event ->
            val date = parseIsoDate(event.date.take(10).trim()) ?: return@mapNotNull null
            val dayKey = date.year * 10_000 + date.month * 100 + date.day
            val minutes = event.time?.let { parseTimeToMinutes(it) }
            val isUpcoming = when {
                dayKey > todayKey -> true
                dayKey < todayKey -> false
                minutes == null -> true
                else -> minutes >= nowMinutes
            }
            if (isUpcoming) Triple(event, dayKey, minutes ?: -1) else null
        }
        .sortedWith(compareBy({ it.second }, { it.third }))
        .take(limit)
        .map { it.first }
}
