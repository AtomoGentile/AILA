package circolareplus.ui.screens

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
    var expanded by remember { mutableStateOf(false) }
    val relevant = items.filterNot { it.kind == AilaBriefKind.CIRCULAR }
    val visible = if(expanded) relevant else {
        val first = relevant.firstOrNull()
        listOfNotNull(first, relevant.drop(1).firstOrNull { it.kind != first?.kind } ?: relevant.getOrNull(1))
    }
    val white = Color.White
    val soft = Color(0xFFCFD9F4)
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF10172C), AppTheme.AccentDeep, Color(0xFF17203B))))
        .border(1.dp, white.copy(alpha = .10f), RoundedCornerShape(32.dp))) {
        Canvas(Modifier.matchParentSize()) {
            val center = Offset(size.width * .96f, size.height * .04f)
            listOf(.33f, .57f, .82f).forEachIndexed { i, scale ->
                drawCircle(soft.copy(alpha = .12f - i * .025f), size.width * scale, center, style = Stroke(1.dp.toPx()))
            }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("AILA brief", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = white)
                    Text("Quello che conta per te, adesso.",
                        style = MaterialTheme.typography.bodyMedium, color = soft)
                }
                AilaAssistantMark(modifier = Modifier.size(34.dp))
            }
            if(loading && relevant.isEmpty()) Text("Preparo il tuo riepilogo…", color = soft, style = MaterialTheme.typography.bodyMedium)
            if(error != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(error, Modifier.weight(1f), color = soft, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRefresh, colors = ButtonDefaults.textButtonColors(contentColor = white)) { Text("Riprova") }
            }
            if(relevant.isEmpty() && !loading && error == null) {
                Text("Sei in pari. Qui troverai novità, risposte e cose da fare per te.", color = soft, style = MaterialTheme.typography.bodyMedium)
            }
            visible.forEach { item ->
                val tint = when(item.kind) {
                    AilaBriefKind.EVENT -> Color(0xFF9EDFD8)
                    AilaBriefKind.CIRCULAR -> Color(0xFFF2D398)
                    AilaBriefKind.POLL, AilaBriefKind.RANKING_POLL -> Color(0xFFC9B9FF)
                    AilaBriefKind.FORUM -> Color(0xFFA0D5FF)
                    AilaBriefKind.PROPOSAL -> Color(0xFFFFBDD5)
                }
                val label = when(item.kind) {
                    AilaBriefKind.EVENT -> if(item.date == today().toIso()) "OGGI" else item.date?.let(::briefDate) ?: "CALENDARIO"
                    AilaBriefKind.CIRCULAR -> "CIRCOLARE N. ${item.sourceId}"
                    AilaBriefKind.POLL, AilaBriefKind.RANKING_POLL -> "DA COMPILARE"
                    AilaBriefKind.FORUM -> "EDUCAZIONE TRA PERE"
                    AilaBriefKind.PROPOSAL -> "BACHECA"
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(white.copy(alpha = .065f)).border(1.dp, white.copy(alpha = .09f), RoundedCornerShape(24.dp))
                    .padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(36.dp).background(tint.copy(alpha = .14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            when(item.kind) {
                                AilaBriefKind.EVENT -> AppIcons.Calendar(Modifier.size(20.dp), tint)
                                AilaBriefKind.CIRCULAR -> AppIcons.Document(Modifier.size(20.dp), tint)
                                AilaBriefKind.POLL, AilaBriefKind.RANKING_POLL -> AppIcons.Poll(Modifier.size(20.dp), tint)
                                AilaBriefKind.FORUM, AilaBriefKind.PROPOSAL -> AppIcons.ChatBubble(Modifier.size(20.dp), tint)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, color = tint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(item.title, color = white, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onDismiss(item) }, modifier = Modifier.size(32.dp).semantics { contentDescription = "Non ora: ${item.title}" }) {
                            AppIcons.Close(Modifier.size(17.dp), soft)
                        }
                    }
                    if(item.kind != AilaBriefKind.EVENT) Text(item.message, color = soft, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onOpen(item)
                            },
                            modifier = Modifier.weight(1f, fill = false),
                            colors = ButtonDefaults.buttonColors(containerColor = white.copy(alpha = .13f), contentColor = white),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if(item.kind == AilaBriefKind.EVENT) "Apri calendario" else item.actionLabel, style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(6.dp)); AppIcons.ChevronRight(Modifier.size(16.dp), white)
                        }

                    }
                }
            }
            if(relevant.size > 2) TextButton(onClick={expanded=!expanded},colors=ButtonDefaults.textButtonColors(contentColor=soft),modifier=Modifier.fillMaxWidth()) {
                Text(if(expanded) "Mostra meno" else "Altre ${relevant.size-2} novità")
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
