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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
    // Mentre il foglio e' aperto l'app dietro si sfoca (vedi AilaSheetBackdrop): e' questo, non
    // il fondo del foglio, a tenere leggibile il testo con il vetro trasparente come il resto.
    androidx.compose.runtime.DisposableEffect(Unit) {
        AilaSheetBackdrop.openSheets++
        onDispose { AilaSheetBackdrop.openSheets-- }
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
        // Velo leggero: la sfocatura fa gia' il grosso, il velo stacca un po' il foglio.
        scrimColor = if (AppTheme.isDarkMode) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.2f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = inset, end = inset, bottom = inset)
                // Stessa trasparenza del resto del vetro (richiesta di Simone).
                .ailaGlassSurface(shape),
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

/**
 * Quanti menu dal basso sono aperti in questo momento. La shell (MainAppShell) sfoca l'app dietro
 * finche' ce n'e' almeno uno, come iOS dietro ai fogli: il foglio e' in una finestra sua, quindi
 * la sfocatura non puo' farla lui.
 */
object AilaSheetBackdrop {
    var openSheets by androidx.compose.runtime.mutableIntStateOf(0)
}
