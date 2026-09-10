package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaLogoTile
import circolareplus.design.ailaBreathe

/**
 * Schermata di caricamento nello stile del mockup AILA (schermo "Caricamento" del kit di
 * design): sfondo blu scuro sfumato con il logo vero (asset "Logo app (scuro)" ora
 * disegnato a vettori invece che caricato dal PNG, che essendo senza canale alpha si portava
 * dietro il proprio fondo) e il testo
 * "Caricamento...". Usata mentre l'app ripristina la sessione salvata all'avvio.
 */
@Composable
fun AilaLoadingScreen(message: String = "Caricamento...") {
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Stesso gradiente dell'onboarding: prima erano due blu notte scritti a mano qui.
            .background(circolareplus.design.AppTheme.HeroGradientDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Respiro lento: fa capire che l'app sta lavorando e non è piantata.
            AilaLogoTile(size = 88.dp, glow = true, modifier = Modifier.ailaBreathe())
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "AILA",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(28.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = Color(0xCCFFFFFF)
            )
        }
    }
}
