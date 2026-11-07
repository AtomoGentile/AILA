package circolareplus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
    loading: Boolean = false, error: String? = null, onRefresh: () -> Unit = {}, modifier: Modifier = Modifier
) {
    val relevant = items
    val visible = relevant.take(3)
    val dark = AppTheme.isDarkMode
    val glass = AppTheme.isGlass
    val primaryText = AppTheme.TextDark
    val soft = AppTheme.TextMuted
    val panelShape = RoundedCornerShape(32.dp)
    val panelColors = if(dark) listOf(Color(0xFF10172C), AppTheme.AccentDeep, Color(0xFF17203B))
        else listOf(Color(0xFFF9FAFF), Color(0xFFE8EDF7), Color(0xFFF3F5FC))
    val panelSurface = if(glass) Modifier.ailaGlassSurface(
        panelShape,
        tint=if(dark) Color(0xFF1A2543).copy(alpha=.88f) else Color.White.copy(alpha=.54f)
    ) else Modifier.clip(panelShape)
        .background(Brush.linearGradient(panelColors))
        .border(1.dp, if(dark) Color.White.copy(alpha=.12f) else Color(0xFF27324B).copy(alpha=.12f), panelShape)
    val itemFill = if(dark) Color.White.copy(alpha=.075f) else Color(0xFF17213A).copy(alpha=.045f)
    val itemBorder = if(dark) Color.White.copy(alpha=.12f) else Color(0xFF17213A).copy(alpha=.10f)
    Box(modifier.fillMaxWidth().then(panelSurface)) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width * .96f, size.height * .04f)
            listOf(.33f, .57f, .82f).forEachIndexed { i, scale ->
                drawCircle(soft.copy(alpha = .12f - i * .025f), size.width * scale, center, style = Stroke(1.dp.toPx()))
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("AILA Brief", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = primaryText)
                    Text("Quello che conta per te, adesso.",
                        style = MaterialTheme.typography.bodyMedium, color = soft)
                }
                AilaAssistantMark(modifier = Modifier.size(34.dp))
            }
            if(loading && relevant.isEmpty()) Text("Preparo il tuo riepilogo…", color = soft, style = MaterialTheme.typography.bodyMedium)
            if(error != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(error, Modifier.weight(1f), color = soft, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRefresh, colors = ButtonDefaults.textButtonColors(contentColor = primaryText)) { Text("Riprova") }
            }
            if(relevant.isEmpty() && !loading && error == null) {
                Text("Sei in pari. Qui troverai novità, risposte e cose da fare per te.", color = soft, style = MaterialTheme.typography.bodyMedium)
            }
            visible.forEach { item ->
                val tint = when(item.kind) {
                    AilaBriefKind.EVENT -> if(dark) Color(0xFF9EDFD8) else Color(0xFF0F766E)
                    AilaBriefKind.CIRCULAR -> if(dark) Color(0xFFF2D398) else Color(0xFF9A5B00)
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
                }
                if(item.kind == AilaBriefKind.CIRCULAR) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(itemFill).border(1.dp,itemBorder,RoundedCornerShape(24.dp))
                        .clickable(role=Role.Button) { onOpen(item) }.padding(16.dp),
                        verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("Circolare n. ${item.sourceId}",color=tint,style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
                        Text(item.title,color=primaryText,style=MaterialTheme.typography.titleMedium)
                    }
                } else Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(itemFill).border(1.dp, itemBorder, RoundedCornerShape(24.dp))
                    .clickable(role=Role.Button) { onOpen(item) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).background(tint.copy(alpha = if(dark) .14f else .10f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            when(item.kind) {
                                AilaBriefKind.SEATMAP -> AppIcons.Chair(Modifier.size(20.dp), tint)
                                AilaBriefKind.EVENT -> AppIcons.Calendar(Modifier.size(20.dp), tint)
                                AilaBriefKind.CIRCULAR -> AppIcons.Document(Modifier.size(20.dp), tint)
                                AilaBriefKind.POLL, AilaBriefKind.RANKING_POLL -> AppIcons.Poll(Modifier.size(20.dp), tint)
                                AilaBriefKind.FORUM, AilaBriefKind.PROPOSAL -> AppIcons.ChatBubble(Modifier.size(20.dp), tint)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, color = tint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(item.title, color = primaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if(item.kind != AilaBriefKind.EVENT) Text(item.message, color = soft, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = { onDismiss(item) }, modifier = Modifier.size(32.dp).semantics { contentDescription = "Non ora: ${item.title}" }) {
                        AppIcons.Close(Modifier.size(17.dp), soft)
                    }
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
