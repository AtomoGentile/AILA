package circolareplus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import circolareplus.design.AnimatedFilterChip
import circolareplus.design.AppTheme
import circolareplus.domain.model.CalendarEventCategory

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventCategorySelector(
    selectedCategory: CalendarEventCategory,
    onCategorySelected: (CalendarEventCategory) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
        for (cat in CalendarEventCategory.entries) {
            AnimatedFilterChip(
                label = cat.name,
                isSelected = selectedCategory == cat,
                onClick = { onCategorySelected(cat) }
            )
        }
    }
}
