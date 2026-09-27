package circolareplus.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Il menu che sale dal basso, uguale in tutta l'app.
 *
 * Liquid Glass: come i fogli di iOS 26, staccato dai bordi dello schermo, con gli angoli che
 * seguono la curva del telefono, di vetro scuro (o chiaro) traslucido col filo di luce sul bordo
 * e la maniglia a capsula. Prima era un pannello pieno e opaco, l'unico pezzo dell'app che non
 * sembrava vetro.
 * Material Expressive: il foglio pieno di Material 3, a tutta larghezza.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AilaBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    if (!AppTheme.isGlass) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = AppTheme.SurfaceWhite,
            content = content
        )
        return
    }
    val inset = 8.dp
    // Angoli concentrici a quelli dello schermo (il foglio sta a [inset] dal bordo).
    val radius = maxOf(circolareplus.platform.displayCornerRadius() - inset, 28.dp)
    val shape = RoundedCornerShape(radius)
    // Il foglio di Material resta trasparente e senza maniglia: il vetro e la maniglia sono
    // disegnati dentro il contenuto, che si muove sempre insieme al foglio quando lo si trascina.
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = androidx.compose.ui.graphics.RectangleShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = if (AppTheme.isDarkMode) 0.4f else 0.22f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = inset, end = inset, bottom = inset)
                .ailaGlassSurface(
                    shape,
                    // Base traslucida: senza sfocatura dietro, un vetro trasparente come le card
                    // renderebbe illeggibile il testo sopra la schermata sotto.
                    tint = if (AppTheme.isDarkMode) Color(0xC7141418) else Color(0xC7F4F5FA)
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Maniglia a capsula, come iOS.
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 14.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AppTheme.TextFaint)
            )
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}
