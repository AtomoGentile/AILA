package circolareplus.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import circolareplus.platform.LocalNothingGlyph
import androidx.compose.ui.unit.dp

/** Six controllable segments documented for Phone (4a). This is explicitly an on-screen preview. */
@Composable
internal fun NothingBarPreview(modifier: Modifier, fraction: Float = 1f, segments: Int = 6) {
    val paper = Color(0xFFF6F5EF)
    Canvas(modifier.semantics { contentDescription = "Anteprima Glyph Bar a $segments segmenti" }) {
        val count = kotlin.math.ceil(fraction.coerceIn(0f,1f)*segments).toInt()
        val gap = size.height*.025f
        val height = (size.height-gap*(segments-1))/segments
        val width = minOf(size.width*.26f,height*1.8f)
        repeat(segments) { i ->
            drawRoundRect(if(i >= segments-count) paper else Color(0xFF414141), Offset((size.width-width)/2, i*(height+gap)),
                Size(width,height),CornerRadius(height*.22f))
        }
    }
}

@Composable
internal fun NothingAlertsWidget(modifier: Modifier = Modifier) {
    var focus by remember { mutableStateOf(NothingExperience.focus.running) }
    var studio by remember { mutableStateOf(false) }
    val paper = Color(0xFFF6F5EF)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NothingSpacing.Inline)) {
        Row(horizontalArrangement = Arrangement.spacedBy(NothingSpacing.Inline)) {
            listOf("Glyph", "Focus").forEachIndexed { index, title ->
                val chosen = focus == (index == 1)
                Box(Modifier.weight(1f).heightIn(min=44.dp).clip(CircleShape)
                    .background(if(chosen) AppTheme.TextDark else AppTheme.TrackFill)
                    .clickable(role=Role.Tab) { focus=index==1 }.semantics { selected=chosen }, contentAlignment=Alignment.Center) {
                    Text(title, color=if(chosen) AppTheme.BackgroundLight else AppTheme.TextDark, style=MaterialTheme.typography.labelMedium)
                }
            }
        }
        if(focus) NothingFocusWidget(Modifier.fillMaxWidth()) else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF181818)).padding(NothingSpacing.Content), horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(NothingSpacing.Item)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                Text("BAR / 06",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=paper)
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF2A2A2A)).clickable(role=Role.Button) {studio=true}
                    .semantics { contentDescription="Personalizza Glyph" },contentAlignment=Alignment.Center) {AppIcons.Sliders(Modifier.size(18.dp),paper)}
            }
            NothingBarPreview(Modifier.fillMaxWidth().aspectRatio(1f))
            Text("Avvisi sul retro",style=MaterialTheme.typography.labelSmall,color=paper)
            Box(Modifier.fillMaxWidth().heightIn(min=44.dp).clip(CircleShape).background(paper)
                .clickable(role=Role.Button) {studio=true}.padding(horizontal=8.dp,vertical=8.dp),contentAlignment=Alignment.Center) {
                Text("Configura avvisi",style=MaterialTheme.typography.labelMedium,color=Color(0xFF181818))
            }
        }
    }
    if(studio) NothingGlyphStudio {studio=false}
}
