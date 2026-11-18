package circolareplus.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import circolareplus.design.AilaCard
import circolareplus.design.AppTheme
import circolareplus.util.formatItalianDateWithWeekday

@Composable
internal fun CalendarConflictNotice(
    conflict: CalendarConflict,
    modifier: Modifier = Modifier,
    includeDate: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    AilaCard(modifier) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(conflict.kind.label, color = AppTheme.TintAmberInk, style = MaterialTheme.typography.labelLarge)
            if (includeDate) Text(
                formatItalianDateWithWeekday(conflict.date),
                color = AppTheme.TextMuted,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "${conflict.first.title} · ${conflict.second.title}",
                color = AppTheme.TextDark,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
