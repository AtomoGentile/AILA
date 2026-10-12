package circolareplus.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import circolareplus.data.ClassSchedule

/**
 * Scelta delle ore di lezione al giorno della classe: "5 ore (8-13)" o "6 ore (8-14)". [selected]
 * e' `null` finche' non si sceglie (alla registrazione la scelta e' obbligatoria: nessun valore
 * preselezionato che il Rappresentante potrebbe non guardare).
 */
@Composable
fun LessonHoursPicker(
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)
    ) {
        listOf(ClassSchedule.SHORT_DAY, ClassSchedule.LONG_DAY).forEach { hours ->
            val isSelected = selected == hours
            val shape = RoundedCornerShape(AppTheme.SmallElementRadius)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (isSelected) AppTheme.TintBlue else AppTheme.SurfaceWhite)
                    .border(
                        BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) AppTheme.PrimaryBlue else AppTheme.FieldOutline),
                        shape
                    )
                    .clickable(enabled = enabled) { onSelect(hours) }
                    .padding(vertical = AppTheme.Space12),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ClassSchedule.label(hours),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) AppTheme.TintBlueInk else AppTheme.TextDark
                )
            }
        }
    }
}
