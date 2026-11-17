package circolareplus.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Compact content masthead for sections embedded below the class navigation. */
@Composable
fun NothingContentLead(title: String, description: String, symbol: NothingSymbol,
    actions: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NothingSpacing.Content)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NothingSpacing.Item)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(NothingSpacing.Inline)) {
                Text(title, style = nothingDisplayStyle(28, 38), color = AppTheme.TextDark)
            }
            Box(Modifier.size(52.dp).background(AppTheme.TextDark, CircleShape), contentAlignment = Alignment.Center) {
                NothingDotIcon(symbol, Modifier.size(32.dp), AppTheme.BackgroundLight)
            }
        }
        Text(description, style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
        actions?.invoke()
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.Hairline))
    }
}
