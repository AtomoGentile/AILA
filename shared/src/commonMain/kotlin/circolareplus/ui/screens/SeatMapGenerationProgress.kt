package circolareplus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import circolareplus.data.repository.SeatMapRepository
import circolareplus.design.AppTheme

@Composable
internal fun SeatMapGenerationProgress(completed: Int) {
    val total = SeatMapRepository.GENERATION_SEARCH_COUNT
    val count = completed.coerceIn(0, total)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Ricerche completate · $count/$total · ${count * 100 / total}%",
            style = MaterialTheme.typography.labelMedium,
            color = AppTheme.TextDark
        )
        LinearProgressIndicator(
            progress = { count.toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
            color = AppTheme.PrimaryBlue,
            trackColor = AppTheme.TintSlate
        )
        Text(
            text = "Cerco le migliori disposizioni. Il tempo necessario dipende dalla classe e dai vincoli.",
            style = MaterialTheme.typography.bodySmall,
            color = AppTheme.TextMuted
        )
    }
}
