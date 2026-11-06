package circolareplus.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import circolareplus.data.remote.dto.AcaNudgeDto
import circolareplus.design.*
import circolareplus.util.today

@Composable
internal fun AilaBriefCard(
    nudges: List<AcaNudgeDto>, onDismiss: (String) -> Unit,
    onStudy: (String) -> Unit, modifier: Modifier = Modifier
) {
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
                    Text(if(nudges.isEmpty()) "Un po’ di spazio per respirare." else "I tuoi prossimi passi, con calma.",
                        style = MaterialTheme.typography.bodyMedium, color = soft)
                }
                AilaAssistantMark(modifier = Modifier.size(34.dp))
            }
            if(nudges.isEmpty()) {
                Text("Nessun impegno urgente per te nei prossimi tre giorni.", color = soft, style = MaterialTheme.typography.bodyMedium)
            }
            nudges.forEachIndexed { index, nudge ->
                val tint = listOf(Color(0xFF9EDFD8), Color(0xFFC9B9FF), Color(0xFFF2D398))[index % 3]
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(white.copy(alpha = .065f)).border(1.dp, white.copy(alpha = .09f), RoundedCornerShape(24.dp))
                    .padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(36.dp).background(tint.copy(alpha = .14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            AppIcons.Calendar(Modifier.size(20.dp), tint)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if(nudge.eventDate == today().toString()) "OGGI" else briefDate(nudge.eventDate), color = tint,
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(nudge.title, color = white, style = MaterialTheme.typography.titleMedium)
                        }
                        IconButton(onClick = { onDismiss(nudge.id) }, modifier = Modifier.size(32.dp)) {
                            Box(Modifier.semantics { contentDescription = "Non ora: ${nudge.title}" }) { AppIcons.Close(Modifier.size(17.dp), soft) }
                        }
                    }
                    if(nudge.canPlanStudy) Button(
                        onClick = { onStudy("Prepara un piano di ripasso per questo mio impegno, senza aggiungere eventi: ${nudge.title}, data ${nudge.eventDate}.") },
                        colors = ButtonDefaults.buttonColors(containerColor = white.copy(alpha = .13f), contentColor = white),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Prepara il ripasso"); Spacer(Modifier.width(8.dp)); AppIcons.ChevronRight(Modifier.size(16.dp), white) }
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
