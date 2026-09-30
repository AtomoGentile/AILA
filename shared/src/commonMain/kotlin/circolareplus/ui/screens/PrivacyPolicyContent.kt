package circolareplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppTheme.TextDark)
                Text(text = text, fontSize = 12.sp, lineHeight = 17.sp, color = AppTheme.TextMuted)
            }
        }
    }
}
