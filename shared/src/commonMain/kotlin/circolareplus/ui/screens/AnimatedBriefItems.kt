package circolareplus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import circolareplus.data.remote.dto.AilaBriefItemDto
import circolareplus.design.AppTheme
import circolareplus.design.ailaFadeSpec
import circolareplus.design.ailaMoveSpec
import circolareplus.design.ailaNoTouchWhile

@Composable
internal fun AnimatedBriefItems(
    items: List<AilaBriefItemDto>,
    spacing: Dp = 0.dp,
    content: @Composable (index: Int, item: AilaBriefItemDto) -> Unit
) {
    val current = items.distinctBy { it.id }
    val indices = current.mapIndexed { index, item -> item.id to index }.toMap()
    var retained by remember { mutableStateOf(emptyList<AilaBriefItemDto>()) }
    val displayed = current.toMutableList()
    // Le righe in uscita conservano il loro posto fino alla fine della transizione.
    retained.forEachIndexed { index, item ->
        if (item.id !in indices) displayed.add(index.coerceAtMost(displayed.size), item)
    }
    SideEffect { retained = displayed }

    if (displayed.isEmpty()) return
    val reduceMotion = AppTheme.reduceMotion
    val density = LocalDensity.current
    val entranceOffset = with(density) { 32.dp.roundToPx() }
    val exitOffset = with(density) { 12.dp.roundToPx() }
    // Ogni riga porta con sé lo spazio sottostante, così anche il margine si chiude con la riga.
    Column(Modifier.layout { measurable, constraints ->
        val list = measurable.measure(constraints)
        val height = (list.height - spacing.roundToPx()).coerceAtLeast(constraints.minHeight)
        layout(list.width, height) { list.placeRelative(0, 0) }
    }) {
        displayed.forEachIndexed { displayedIndex, item ->
            key(item.id) {
                val index = indices[item.id]
                val visible = index != null
                val state = remember { MutableTransitionState(false) }
                state.targetState = visible
                LaunchedEffect(visible, state.isIdle) {
                    if (!visible && state.isIdle) retained = retained.filterNot { it.id == item.id }
                }

                val delay = (index ?: displayedIndex).coerceAtMost(2) * 45
                val fadeIn = fadeIn(ailaFadeSpec(240, delay))
                val fadeOut = fadeOut(ailaFadeSpec(if (reduceMotion) 150 else 210))
                AnimatedVisibility(
                    visibleState = state,
                    enter = if (reduceMotion) fadeIn else fadeIn +
                        expandVertically(ailaMoveSpec(320, delay), expandFrom = Alignment.Top) +
                        slideInVertically(ailaMoveSpec(320, delay)) { (it / 4).coerceAtMost(entranceOffset) },
                    exit = if (reduceMotion) fadeOut else fadeOut +
                        shrinkVertically(ailaMoveSpec(300), shrinkTowards = Alignment.Top) +
                        slideOutVertically(ailaMoveSpec(250)) { -exitOffset }
                ) {
                    Column(
                        Modifier
                            .ailaNoTouchWhile(!visible)
                            .then(if (visible) Modifier else Modifier.clearAndSetSemantics {})
                            .padding(bottom = spacing)
                    ) {
                        content(index ?: displayedIndex, item)
                    }
                }
            }
        }
    }
}
