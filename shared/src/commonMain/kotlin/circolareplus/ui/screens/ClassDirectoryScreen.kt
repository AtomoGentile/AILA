package circolareplus.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import circolareplus.design.AilaCard
import circolareplus.design.AilaEmptyState
import circolareplus.design.AilaListRow
import circolareplus.design.AppTheme
import circolareplus.design.NothingSpacing
import circolareplus.domain.model.User

@Composable
internal fun ClassDirectoryScreen(members: List<User>) {
    val ordered = remember(members) {
        members.sortedWith(compareBy({ it.lastName.lowercase() }, { it.firstName.lowercase() }, { it.id }))
    }
    val gutter = if (AppTheme.isNothing) NothingSpacing.Edge else AppTheme.Space16
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (ordered.isEmpty()) {
            AilaEmptyState(title = "Nessun nome nell’elenco")
        } else {
            LazyColumn(
                Modifier.widthIn(max = 660.dp).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = gutter, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ordered, key = { it.id }) { member ->
                    AilaCard {
                        AilaListRow(
                            title = listOf(member.lastName, member.firstName).filter { it.isNotBlank() }.joinToString(" ")
                        )
                    }
                }
            }
        }
    }
}
