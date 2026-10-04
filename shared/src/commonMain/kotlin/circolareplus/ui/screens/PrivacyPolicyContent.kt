package circolareplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import circolareplus.PrivacyPolicy
import circolareplus.design.AppTheme

/** Testo dell'informativa, uguale in Impostazioni e nella registrazione. */
@Composable
fun PrivacyPolicyContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)
    ) {
        PrivacyPolicy.sections.forEach { (title, text) ->
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space4)) {
                Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = AppTheme.TextDark)
                Text(text = text, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
            }
        }
    }
}
