package circolareplus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaCard
import circolareplus.design.AilaDot
import circolareplus.design.AilaIconTile
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.domain.model.NotificationLogEntry

/**
 * Storico locale delle notifiche ricevute (mai sul server, si autoelimina dopo qualche giorno
 * — vedi [circolareplus.data.local.LocalSettingsManager]). Prima d'ora la campanella in Home
 * portava semplicemente al Profilo: non esisteva alcuna schermata che mostrasse le notifiche
 * effettivamente ricevute.
 */
@Composable
fun NotificationsScreen(
    notifications: List<NotificationLogEntry> = emptyList(),
    onNotificationClick: (NotificationLogEntry) -> Unit = {}
) {
    // Le notifiche con una categoria riconosciuta (circolari, bacheca, mappa posti, sondaggi...)
    // portano dritte alla schermata giusta tramite onNotificationClick. Il dialog di dettaglio
    // resta solo per quelle senza categoria (es. una push generica): lì non c'è una destinazione
    // certa, quindi si mostra almeno il testo per intero.
    var selectedForDetail by remember { mutableStateOf<NotificationLogEntry?>(null) }
    selectedForDetail?.let { entry ->
        NotificationDetailDialog(entry = entry, onDismiss = { selectedForDetail = null })
    }

    if (notifications.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            AilaEmptyState(
                title = "Nessuna notifica recente",
                message = "Le notifiche vengono eliminate automaticamente dopo qualche giorno.",
                icon = { AppIcons.Bell(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AppTheme.Space16, vertical = AppTheme.Space12),
        verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)
    ) {
        items(notifications, key = { it.id }) { entry ->
            NotificationCard(
                entry = entry,
                onClick = {
                    if (entry.category.isBlank()) selectedForDetail = entry
                    else onNotificationClick(entry)
                }
            )
        }
    }
}

/** Tinta, colore icona e disegno della categoria, per riconoscere il tipo di notifica a colpo d'occhio. */
private data class CategoryVisual(val tint: Color, val ink: Color, val icon: @Composable (Color) -> Unit)

@Composable
private fun categoryVisual(category: String): CategoryVisual = when (category) {
    NotificationKind.CIRCULARS.key ->
        CategoryVisual(AppTheme.TintBlue, AppTheme.TintBlueInk) { AppIcons.Document(modifier = Modifier.size(19.dp), color = it) }
    NotificationKind.BOARD.key ->
        CategoryVisual(AppTheme.TintViolet, AppTheme.TintVioletInk) { AppIcons.Bulb(modifier = Modifier.size(19.dp), color = it) }
    NotificationKind.SEATMAP.key, NOTIFICATION_CATEGORY_SEATMAP_PREFERENCES ->
        CategoryVisual(AppTheme.TintGreen, AppTheme.TintGreenInk) { AppIcons.Chair(modifier = Modifier.size(19.dp), color = it) }
    NotificationKind.POLLS.key, NOTIFICATION_CATEGORY_RANKING_POLLS ->
        CategoryVisual(AppTheme.TintAmber, AppTheme.TintAmberInk) { AppIcons.Pencil(modifier = Modifier.size(19.dp), color = it) }
    NotificationKind.CALENDAR.key ->
        CategoryVisual(AppTheme.TintBlue, AppTheme.TintBlueInk) { AppIcons.Calendar(modifier = Modifier.size(19.dp), color = it) }
    else ->
        CategoryVisual(AppTheme.TintSlate, AppTheme.TintSlateInk) { AppIcons.Bell(modifier = Modifier.size(19.dp), color = it) }
}

@Composable
private fun NotificationCard(entry: NotificationLogEntry, onClick: () -> Unit) {
    // Prima il titolo usava un colore scuro fisso (0xFF0F172A): in tema scuro si confondeva quasi
    // del tutto con lo sfondo della card, rendendolo illeggibile. Ora tutti i testi seguono
    // AppTheme, che si adatta al tema, e la card resta sempre bianca — il non letto si vede dal
    // pallino in alto a destra invece che da uno sfondo blu acceso su tutta la riga.
    val visual = categoryVisual(entry.category)

    AilaCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppTheme.Space12),
            verticalAlignment = Alignment.Top
        ) {
            AilaIconTile(tint = visual.tint, size = 40.dp) { visual.icon(visual.ink) }
            Spacer(modifier = Modifier.width(AppTheme.Space12))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    fontSize = 14.sp,
                    fontWeight = if (entry.read) FontWeight.SemiBold else FontWeight.Bold,
                    color = AppTheme.TextDark,
                    maxLines = 2
                )
                if (entry.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = entry.body,
                        fontSize = 13.sp,
                        color = AppTheme.TextMuted,
                        maxLines = 2
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = relativeTimeLabel(entry.receivedAtMillis), fontSize = 11.sp, color = AppTheme.TextFaint)
            }
            if (!entry.read) {
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                AilaDot(color = AppTheme.PrimaryBlue, size = 8.dp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/**
 * Dettaglio di una notifica senza categoria riconosciuta: titolo, testo per intero e quando è
 * arrivata. Quelle con categoria portano invece direttamente alla schermata giusta.
 */
@Composable
private fun NotificationDetailDialog(entry: NotificationLogEntry, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Chiudi", fontWeight = FontWeight.Bold, color = AppTheme.PrimaryBlue)
            }
        },
        icon = {
            AilaIconTile(tint = AppTheme.TintBlue) {
                AppIcons.Bell(modifier = Modifier.size(21.dp), color = AppTheme.TintBlueInk)
            }
        },
        title = {
            Text(
                text = entry.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark
            )
        },
        text = {
            Column {
                if (entry.body.isNotBlank()) {
                    Text(
                        text = entry.body,
                        fontSize = 14.sp,
                        color = AppTheme.TextMuted,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space12))
                }
                Text(
                    text = "Ricevuta ${relativeTimeLabel(entry.receivedAtMillis).lowercase()}",
                    fontSize = 12.sp,
                    color = AppTheme.TextFaint
                )
            }
        },
        containerColor = AppTheme.SurfaceWhite,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppTheme.CardCornerRadius)
    )
}

/** Etichetta relativa semplice ("adesso", "X min fa", "X h fa", "X g fa") senza dipendenze da librerie data/ora esterne. */
private fun relativeTimeLabel(receivedAtMillis: Long): String {
    val diffMillis = (circolareplus.platform.currentTimeMillis() - receivedAtMillis).coerceAtLeast(0)
    val minutes = diffMillis / (60 * 1000)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Adesso"
        minutes < 60 -> "$minutes min fa"
        hours < 24 -> "$hours h fa"
        else -> "$days g fa"
    }
}
