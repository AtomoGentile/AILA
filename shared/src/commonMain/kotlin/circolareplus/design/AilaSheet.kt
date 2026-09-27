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
        // Il foglio e' molto trasparente: a tenere leggibili le scritte e' il velo sulla
        // schermata dietro, scuro in scuro e chiaro in chiaro (con un velo nero, in chiaro il
        // testo scuro finirebbe su fondo scuro).
        scrimColor = if (AppTheme.isDarkMode) Color.Black.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.6f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = inset, end = inset, bottom = inset)
                .ailaGlassSurface(
                    shape,
                    // Cinque volte piu' trasparente di prima (0xC7 -> 0x28): la leggibilita' la da'
                    // il velo sulla schermata dietro (scrimColor), non il fondo del foglio.
                    tint = if (AppTheme.isDarkMode) Color(0x28141418) else Color(0x28F4F5FA)
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
