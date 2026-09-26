package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaCard
import circolareplus.design.AilaIconTile
import circolareplus.design.AilaListRow
import circolareplus.design.AilaSectionTitle
import circolareplus.design.ailaAppear
import circolareplus.design.ailaGlassOverlay
import circolareplus.design.ailaGlassPressable
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.util.nowMinutesOfDay
import circolareplus.util.parseIsoDate
import circolareplus.util.parseTimeToMinutes
import circolareplus.util.today
import circolareplus.domain.model.CalendarEvent
import circolareplus.domain.model.Circular

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
    onNavigateToSeatMap: () -> Unit = {},
    onNavigateToBoard: () -> Unit = {},
    onNavigateToPolls: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToCirculars: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    hasUnreadNotifications: Boolean = false
) {
    val scrollState = rememberScrollState()

    // Circolare più recente (numero più alto): mostrata in evidenza, se ce n'è almeno una.
    val latestCircular = circulars.maxByOrNull { it.number }
    // Prime 3 scadenze **future**. Prima si prendevano le prime tre della lista e basta, poi si
    // confrontavano le date come stringhe: un "2026-9-5" scritto senza zeri (arriva dagli eventi
    // generati dall'AI) risultava "maggiore" di "2026-09-20" e una verifica di settembre restava
    // per mesi fra i prossimi eventi. Ora la data si legge davvero, e un evento di oggi la cui ora
    // e' gia' passata non e' piu' "prossimo".
    val nextEvents = remember(calendarEvents) { upcomingEvents(calendarEvents, limit = 3) }

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
        HomeHeroPanel(
            studentFirstName = studentFirstName,
            hasUnreadNotifications = hasUnreadNotifications,
            onNavigateToNotifications = onNavigateToNotifications,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToCirculars = onNavigateToCirculars,
            onNavigateToCalendar = onNavigateToCalendar,
            onNavigateToBoard = onNavigateToBoard,
            onNavigateToSeatMap = onNavigateToSeatMap
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.Space16)
                .padding(top = AppTheme.Space20, bottom = AppTheme.Space24)
        ) {
            LatestCircularCard(
                latestCircular = latestCircular,
                onClick = openLatestCircular,
                modifier = Modifier.ailaAppear(0)
            )
            Spacer(modifier = Modifier.height(AppTheme.Space24))
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
                modifier = Modifier.ailaAppear(2)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.Space16)
                .padding(bottom = AppTheme.Space24)
        ) {
            AilaSectionTitle(text = "Scorciatoie", modifier = Modifier.ailaAppear(3))

            Spacer(modifier = Modifier.height(AppTheme.Space12))

            AilaCard(modifier = Modifier.ailaAppear(4)) {
                AilaListRow(
                    title = "Sondaggi",
                    subtitle = "Date delle interrogazioni e opzioni da mettere in ordine",
                    tint = AppTheme.TintAmber,
                    onClick = onNavigateToPolls,
                    icon = {
                        AppIcons.Calendar(modifier = Modifier.size(21.dp), color = AppTheme.TintAmberInk)
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
    }
}

/** Card della circolare più recente messa in evidenza in cima alla Home. */
@Composable
private fun LatestCircularCard(
    latestCircular: Circular?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    AilaCard(onClick = onClick, modifier = modifier) {
        AilaListRow(
            title = if (latestCircular != null) "Circolare n. ${latestCircular.number}" else "Nuova circolare",
            subtitle = latestCircular?.title ?: "Nessuna circolare disponibile al momento",
            tint = AppTheme.TintBlue,
            showChevron = latestCircular != null,
            icon = {
                AppIcons.Document(modifier = Modifier.size(21.dp), color = AppTheme.TintBlueInk)
            }
        )
    }
}

/** Card "Prossimi eventi", o lo stato vuoto quando non c'è nessuna scadenza futura. */
@Composable
private fun NextEventsCard(
    nextEvents: List<CalendarEvent>,
    onNavigateToCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (nextEvents.isEmpty()) {
        AilaCard(modifier = modifier) {
            AilaListRow(
                title = "Nessuna scadenza",
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
                EventRow(event = event, onClick = onNavigateToCalendar)
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
    hasUnreadNotifications: Boolean,
    onNavigateToNotifications: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToCirculars: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToBoard: () -> Unit,
    onNavigateToSeatMap: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(AppTheme.HeroGradient)
            .padding(horizontal = AppTheme.Space20)
            .padding(top = AppTheme.Space24, bottom = AppTheme.Space24)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Il marchio a sinistra del saluto, come nelle intestazioni delle altre schermate.
            circolareplus.design.AilaBrandMark(size = 48.dp)
            Spacer(modifier = Modifier.width(AppTheme.Space12))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ciao, $studentFirstName",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.OnHeroPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tutto ciò che conta, in un unico posto.",
                    fontSize = 13.sp,
                    color = AppTheme.OnHeroSecondary
                )
            }

            // Lente e campanella: la ricerca globale prima non aveva alcun punto d'ingresso.
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                HeroIconButton(onClick = onNavigateToSearch) {
                    AppIcons.Search(modifier = Modifier.size(21.dp), color = AppTheme.OnHeroPrimary)
                }
                HeroIconButton(onClick = onNavigateToNotifications) {
                    AppIcons.Bell(
                        modifier = Modifier.size(21.dp),
                        color = AppTheme.OnHeroPrimary,
                        hasBadge = hasUnreadNotifications
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.Space24))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)
        ) {
            HomeQuickIcon(label = "Circolari", modifier = Modifier.weight(1f), onClick = onNavigateToCirculars) {
                AppIcons.Document(modifier = Modifier.size(22.dp), color = AppTheme.OnHeroPrimary)
            }
            HomeQuickIcon(label = "Calendario", modifier = Modifier.weight(1f), onClick = onNavigateToCalendar) {
                AppIcons.Calendar(modifier = Modifier.size(22.dp), color = AppTheme.OnHeroPrimary)
            }
            HomeQuickIcon(label = "Bacheca", modifier = Modifier.weight(1f), onClick = onNavigateToBoard) {
                AppIcons.ChatBubble(modifier = Modifier.size(22.dp), color = AppTheme.OnHeroPrimary)
            }
            HomeQuickIcon(label = "Mappa posti", modifier = Modifier.weight(1f), onClick = onNavigateToSeatMap) {
                AppIcons.Chair(modifier = Modifier.size(22.dp), color = AppTheme.OnHeroPrimary)
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = day, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppTheme.TintBlueInk)
                Text(text = month, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AppTheme.TintBlueInk)
            }
        }
        Spacer(modifier = Modifier.width(AppTheme.Space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = eventSubtitle(event),
                fontSize = 13.sp,
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

/**
 * Riempimento dei riquadri translucidi del pannello (tasti e icone rapide): non un bianco piatto
 * al 18%, che sul gradiente sembra una toppa grigia, ma un velo che scivola dall'alto al basso.
 */
private val heroTileFill: Brush
    get() = Brush.verticalGradient(listOf(Color(0x3DFFFFFF), Color(0x1FFFFFFF)))

/** Tasto quadrato translucido nel pannello a gradiente (ricerca, notifiche). */
@Composable
private fun HeroIconButton(onClick: () -> Unit, icon: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(heroTileFill)
            .border(1.dp, AppTheme.OnHeroBorder, shape)
            .ailaGlassPressable(tint = AppTheme.PrimaryBlue) { onClick() },
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
    icon: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null
        ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(shape)
                .background(heroTileFill)
                .border(1.dp, AppTheme.OnHeroBorder, shape)
                .ailaGlassOverlay(interactionSource, tint = AppTheme.PrimaryBlue),
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = AppTheme.OnHeroSecondary,
            maxLines = 1,
            softWrap = false
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
