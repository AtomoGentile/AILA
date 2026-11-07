package circolareplus.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Equal-width filters, measured with the current font size and arranged without overflow. */
@Composable
fun AilaFilterBar(
    labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier, dots: List<Color?> = emptyList()
) {
    if(labels.isEmpty()) return
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    val style=MaterialTheme.typography.labelLarge.copy(fontWeight=FontWeight.Bold)
    val textWidth=labels.maxOf { measurer.measure(it,style=style).size.width }
    val minimum=with(density) { textWidth.toDp() } + 32.dp + if(dots.any { it!=null }) 20.dp else 0.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns=when {
            minimum*labels.size + 8.dp*(labels.size-1) <= maxWidth -> labels.size
            labels.size%2==0 && minimum*2 + 8.dp <= maxWidth -> 2
            else -> 1
        }
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            labels.indices.toList().chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach { index ->
                        val glyph: (@Composable (Color)->Unit)? = dots.getOrNull(index)?.let { dot ->
                            { _ -> AilaDot(color=dot,size=8.dp) }
                        }
                        AnimatedFilterChip(
                            label=labels[index],isSelected=selectedIndex==index,onClick={onSelect(index)},
                            modifier=Modifier.weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                                .background(if(selectedIndex==index) AppTheme.SelectionFill else SolidColor(AppTheme.TrackFill))
                                .then(if(AppTheme.isGlass) Modifier.border(1.dp, AppTheme.GlassEdge, RoundedCornerShape(AppTheme.SmallElementRadius)) else Modifier)
                                .semantics {selected=selectedIndex==index},
                            drawSelectionBackground=false,icon=glyph
                        )
                    }
                }
            }
        }
    }
}
