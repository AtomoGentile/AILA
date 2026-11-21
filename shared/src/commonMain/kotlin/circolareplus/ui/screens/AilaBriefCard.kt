package circolareplus.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import circolareplus.data.remote.dto.AilaBriefItemDto
import circolareplus.data.remote.dto.AilaBriefKind
import androidx.compose.ui.text.style.TextOverflow
import circolareplus.design.*
import circolareplus.util.today

@Composable
internal fun AilaBriefCard(
    items: List<AilaBriefItemDto>, onDismiss: (AilaBriefItemDto) -> Unit,
    onOpen: (AilaBriefItemDto) -> Unit, onStudy: (String) -> Unit,
    loading: Boolean = false, error: String? = null, onRefresh: () -> Unit = {}, modifier: Modifier = Modifier,
    subjectColorsRevision: Int = 0,
    subjectColorsEnabled: Boolean = false,
    subjectColorOverrides: Map<String, String> = emptyMap(),
    conflicts: List<CalendarConflict> = emptyList(),
    onOpenConflict: (CalendarConflict) -> Unit = {}
) {
    if (AppTheme.isNothing) {
        NothingBriefCard(items, onDismiss, onOpen, onStudy, loading, error, onRefresh,
            modifier, subjectColorsEnabled, subjectColorOverrides, conflicts, onOpenConflict)
        return
    }
    val relevant = items
    var expanded by rememberSaveable { mutableStateOf(false) }
    val canExpand = relevant.drop(3).any { it.kind == AilaBriefKind.BUREAUCRACY }
    val visible = if (canExpand && expanded) relevant else relevant.take(3)
    val dark = AppTheme.isDarkMode
    val glass = AppTheme.isGlass
    val primaryText = AppTheme.TextDark
    val soft = AppTheme.TextMuted
    val panelShape = RoundedCornerShape(32.dp)
    val panelColors = if (AppTheme.isNothing) listOf(AppTheme.SurfaceWhite, AppTheme.SurfaceWhite) else if(dark) listOf(Color(0xFF10172C), AppTheme.AccentDeep, Color(0xFF17203B))
        else listOf(Color(0xFFF9FAFF), Color(0xFFE8EDF7), Color(0xFFF3F5FC))
    val panelSurface = if(glass) Modifier.ailaGlassSurface(
        panelShape
    ) else Modifier.clip(panelShape)
        .background(Brush.linearGradient(panelColors))
        .border(1.dp, if(dark) Color.White.copy(alpha=.12f) else Color(0xFF27324B).copy(alpha=.12f), panelShape)
    Box(
        modifier.fillMaxWidth()
            .then(panelSurface)
            .animateContentSize(animationSpec = ailaMoveSpec(durationMillis = 280))
    ) {
        if (!AppTheme.isNothing) Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width * .96f, size.height * .04f)
            listOf(.33f, .57f, .82f).forEachIndexed { i, scale ->
                drawCircle(soft.copy(alpha = .12f - i * .025f), size.width * scale, center, style = Stroke(1.dp.toPx()))
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("AILA Brief", style = if (AppTheme.isNothing) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall, fontWeight = if (AppTheme.isNothing) FontWeight.Normal else FontWeight.SemiBold, color = primaryText)
                    Text("Quello che conta per te, adesso.",
                        style = MaterialTheme.typography.bodyMedium, color = soft)
                }
                AilaAssistantMark(modifier = Modifier.size(34.dp))
            }
            if(loading && relevant.isEmpty()) Text("Preparo il tuo riepilogo…", color = soft, style = MaterialTheme.typography.bodyMedium)
            if(error != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(error, Modifier.weight(1f), color = soft, style = MaterialTheme.typography.bodySmall)
                circolareplus.design.AilaTextButton(onClick = onRefresh, colors = ButtonDefaults.textButtonColors(contentColor = primaryText)) { Text("Riprova") }
            }
            conflicts.forEach { conflict ->
                CalendarConflictNotice(conflict, includeDate = true, onClick = { onOpenConflict(conflict) })
            }
            if(conflicts.isEmpty() && relevant.isEmpty() && !loading && error == null) {
                Text("Sei in pari. Qui troverai novità, risposte e cose da fare per te.", color = soft, style = MaterialTheme.typography.bodyMedium)
            }
            visible.forEach { item ->
                val subjectColor = if (subjectColorsEnabled && item.kind == AilaBriefKind.EVENT) {
                    SubjectColors.choiceForSubject(item.title, subjectColorOverrides)
                } else null
                val tint = if (item.kind == AilaBriefKind.CIRCULAR && !item.isActive) AppTheme.TextMuted else subjectColor?.let {
                    if (it.key == "white") if (dark) Color.White else Color(0xFF667085) else it.color
                } ?: if (AppTheme.isNothing) AppTheme.TextDark else when(item.kind) {
                    AilaBriefKind.EVENT, AilaBriefKind.CIRCULAR, AilaBriefKind.BUREAUCRACY -> AppTheme.PrimaryBlue
                    AilaBriefKind.POLL, AilaBriefKind.RANKING_POLL -> if(dark) Color(0xFFC9B9FF) else Color(0xFF6D28D9)
                    AilaBriefKind.FORUM -> if(dark) Color(0xFFA0D5FF) else Color(0xFF0369A1)
                    AilaBriefKind.PROPOSAL -> if(dark) Color(0xFFFFBDD5) else Color(0xFFBE185D)
                    AilaBriefKind.SEATMAP -> if(dark) Color(0xFFA8D8B9) else Color(0xFF15803D)
                }
                val label = when(item.kind) {
                    AilaBriefKind.EVENT -> if(item.date == today().toIso()) "OGGI" else item.date?.let(::briefDate) ?: "CALENDARIO"
                    AilaBriefKind.CIRCULAR -> "CIRCOLARE N. ${item.sourceId}"
                    AilaBriefKind.POLL, AilaBriefKind.RANKING_POLL -> if(item.isResult) "RISULTATI" else "SONDAGGIO APERTO"
                    AilaBriefKind.FORUM -> "REVISIONE TRA PERE"
                    AilaBriefKind.PROPOSAL -> "BACHECA"
                    AilaBriefKind.SEATMAP -> "POSTI"
                    AilaBriefKind.BUREAUCRACY -> item.date?.let { "BUROCRAZIA · ${briefDate(it)}" } ?: "BUROCRAZIA"
                }
                val itemShape = RoundedCornerShape(24.dp)
                val fillAlpha = if (dark) .17f else .10f
                val itemFillBrush = if (AppTheme.isNothing) Brush.horizontalGradient(listOf(AppTheme.CardSurface, AppTheme.CardSurface)) else if (subjectColor?.key == "rainbow") {
                    Brush.horizontalGradient(subjectColor.colors.map { it.copy(alpha = fillAlpha) })
                } else if (subjectColor?.key == "white") {
                    Brush.horizontalGradient(listOf(Color.White.copy(alpha = if (dark) .18f else .72f), Color.White.copy(alpha = if (dark) .10f else .50f)))
                } else {
                    Brush.horizontalGradient(listOf(tint.copy(alpha = fillAlpha), tint.copy(alpha = fillAlpha * .55f)))
                }
                val iconFillBrush = if (AppTheme.isNothing) Brush.horizontalGradient(listOf(AppTheme.TrackFill, AppTheme.TrackFill)) else if (subjectColor?.key == "rainbow") {
                    Brush.horizontalGradient(subjectColor.colors.map { it.copy(alpha = if (dark) .34f else .23f) })
                } else if (subjectColor?.key == "white") {
                    Brush.horizontalGradient(listOf(Color.White.copy(alpha = if (dark) .32f else .95f), Color.White.copy(alpha = if (dark) .22f else .82f)))
                } else {
                    Brush.horizontalGradient(listOf(tint.copy(alpha = if (dark) .34f else .20f), tint.copy(alpha = if (dark) .22f else .14f)))
                }
                val itemBorderColor = if (subjectColor?.key == "white") AppTheme.TextMuted.copy(alpha = .62f)
                    else tint.copy(alpha = if (dark) .42f else .28f)
                val itemBorderBrush = if (AppTheme.isNothing) Brush.horizontalGradient(listOf(AppTheme.Hairline, AppTheme.Hairline)) else if (subjectColor?.key == "rainbow") {
                    Brush.horizontalGradient(subjectColor.colors.map { it.copy(alpha = if (dark) .68f else .52f) })
                } else Brush.horizontalGradient(listOf(itemBorderColor, itemBorderColor))
                if (AppTheme.isNothing) {
                    Column(Modifier.fillMaxWidth().clip(itemShape)
                        .background(itemFillBrush, itemShape).border(1.dp, itemBorderBrush, itemShape)
                        .clickable(role = Role.Button) { onOpen(item) }.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (subjectColor != null) {
                                Box(Modifier.width(3.dp).height(22.dp).background(subjectColor.color, RoundedCornerShape(2.dp)))
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(label, Modifier.weight(1f), color = AppTheme.TextMuted,
                                style = MaterialTheme.typography.labelMedium)
                            circolareplus.design.AilaIconControl(onClick = { onDismiss(item) },
                                modifier = Modifier.size(44.dp).semantics { contentDescription = "Non ora: ${item.title}" }) {
                                AppIcons.Close(Modifier.size(17.dp), soft)
                            }
                        }
                        Text(item.title, color = if (item.kind == AilaBriefKind.CIRCULAR && !item.isActive) soft else primaryText,
                            style = MaterialTheme.typography.titleMedium)
                        if (item.kind != AilaBriefKind.EVENT) Text(item.message, color = soft,
                            style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                Row(Modifier.fillMaxWidth().clip(itemShape)
                    .background(itemFillBrush, itemShape)
                    .border(1.dp, itemBorderBrush, itemShape)
                    .clickable(role=Role.Button) { onOpen(item) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (AppTheme.isNothing && subjectColor != null) {
                            Box(Modifier.width(3.dp).height(36.dp).background(subjectColor.color, RoundedCornerShape(2.dp)))
                            Spacer(Modifier.width(10.dp))
                        }
                        Box(
                            Modifier.size(36.dp).background(iconFillBrush, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            when(item.kind) {
                                AilaBriefKind.SEATMAP -> AppIcons.Chair(Modifier.size(20.dp), if (AppTheme.isNothing || subjectColor?.key == "white") AppTheme.TextDark else tint)
                                AilaBriefKind.EVENT -> AppIcons.Calendar(Modifier.size(20.dp), if (AppTheme.isNothing || subjectColor?.key == "white") AppTheme.TextDark else tint)
                                AilaBriefKind.CIRCULAR, AilaBriefKind.BUREAUCRACY -> AppIcons.Document(Modifier.size(20.dp), if (AppTheme.isNothing) AppTheme.TextDark else tint)
                                AilaBriefKind.POLL, AilaBriefKind.RANKING_POLL -> AppIcons.Poll(Modifier.size(20.dp), if (AppTheme.isNothing) AppTheme.TextDark else tint)
                                AilaBriefKind.FORUM, AilaBriefKind.PROPOSAL -> AppIcons.ChatBubble(Modifier.size(20.dp), if (AppTheme.isNothing) AppTheme.TextDark else tint)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, color = if (AppTheme.isNothing) AppTheme.TextMuted else tint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(item.title, color = if (item.kind == AilaBriefKind.CIRCULAR && !item.isActive) soft else primaryText, style = MaterialTheme.typography.titleMedium, maxLines = if (AppTheme.isNothing) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if(item.kind != AilaBriefKind.EVENT) Text(item.message, color = soft, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(4.dp))
                    circolareplus.design.AilaIconControl(onClick = { onDismiss(item) }, modifier = Modifier.size(if (AppTheme.isNothing) 44.dp else 32.dp).semantics { contentDescription = "Non ora: ${item.title}" }) {
                        AppIcons.Close(Modifier.size(17.dp), soft)
                    }
                }
                }
            }
            if (canExpand) {
                circolareplus.design.AilaTextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Mostra meno" else "Mostra tutte le scadenze")
                }
            }
        }
    }
}

private fun briefDate(date: String): String {
    val parts = date.split('-')
    val months = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")
    val month = parts.getOrNull(1)?.toIntOrNull()?.minus(1)
    return if(parts.size == 3 && month != null && month in months.indices) "${parts[2].toIntOrNull() ?: parts[2]} ${months[month]}" else date
}
