package circolareplus.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Elenco di card: una colonna sul telefono, due colonne "a mattoncini" su tablet e iPad larghi
 * ([LocalWideLayout]), dove ogni card e' alta quanto serve. [header] (avvisi, riepiloghi) occupa
 * sempre tutta la larghezza, sopra alle card.
 */
@Composable
fun <T> AilaAdaptiveCardList(
    items: List<T>,
    key: ((T) -> Any)? = null,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    spacing: Dp = AppTheme.Space12,
    header: (@Composable () -> Unit)? = null,
    itemContent: @Composable (index: Int, item: T) -> Unit
) {
    if (LocalWideLayout.current) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            verticalItemSpacing = spacing,
            horizontalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = contentPadding,
            modifier = modifier
        ) {
            if (header != null) item(key = "adaptive-header", span = StaggeredGridItemSpan.FullLine) { header() }
            itemsIndexed(items, key = key?.let { k -> { _: Int, item: T -> k(item) } }) { index, item -> itemContent(index, item) }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(spacing),
            contentPadding = contentPadding,
            modifier = modifier
        ) {
            if (header != null) item(key = "adaptive-header") { header() }
            itemsIndexed(items, key = key?.let { k -> { _: Int, item: T -> k(item) } }) { index, item -> itemContent(index, item) }
        }
    }
}
