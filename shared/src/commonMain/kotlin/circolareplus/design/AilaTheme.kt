package circolareplus.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Tema Material 3 dell'app.
 *
 * Perché serviva: finora l'app non impostava nessuno schema di colori, quindi tutti i componenti
 * standard di Material (interruttori, slider, campi di testo, indicatori di caricamento, pulsanti
 * dei dialoghi) uscivano nel **viola di default di Material 3** — si vede negli screenshot:
 * l'interruttore delle notifiche e lo slider dei pesi della mappa posti erano viola, mentre tutto
 * il resto dell'app è blu AILA. Le schermate erano state colorate a mano una per una, ma i
 * componenti di sistema no, perché il loro colore non lo decide la schermata: lo decide il tema.
 *
 * Da qui in avanti si imposta una volta sola qui e vale ovunque.
 */
private fun ailaColorScheme() = if (AppTheme.isDarkMode) darkColorScheme(
    primary = AppTheme.PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = AppTheme.TintBlue,
    onPrimaryContainer = AppTheme.TintBlueInk,
    secondary = AppTheme.SecondaryIndigo,
    onSecondary = Color.White,
    secondaryContainer = AppTheme.TintViolet,
    onSecondaryContainer = AppTheme.TintVioletInk,
    tertiary = AppTheme.AccentCyan,
    onTertiary = Color.White,
    background = AppTheme.BackgroundLight,
    onBackground = AppTheme.TextDark,
    surface = AppTheme.SurfaceWhite,
    onSurface = AppTheme.TextDark,
    surfaceVariant = AppTheme.TintSlate,
    onSurfaceVariant = AppTheme.TextMuted,
    outline = AppTheme.Hairline,
    outlineVariant = AppTheme.Hairline,
    error = AppTheme.TintRedInk,
    onError = Color.White,
    errorContainer = AppTheme.TintRed,
    onErrorContainer = AppTheme.TintRedInk,
    surfaceTint = Color.Transparent
) else lightColorScheme(
    primary = AppTheme.PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = AppTheme.TintBlue,
    onPrimaryContainer = AppTheme.TintBlueInk,

    secondary = AppTheme.SecondaryIndigo,
    onSecondary = Color.White,
    secondaryContainer = AppTheme.TintViolet,
    onSecondaryContainer = AppTheme.TintVioletInk,

    tertiary = AppTheme.AccentCyan,
    onTertiary = Color.White,

    background = AppTheme.BackgroundLight,
    onBackground = AppTheme.TextDark,
    surface = AppTheme.SurfaceWhite,
    onSurface = AppTheme.TextDark,
    surfaceVariant = AppTheme.TintSlate,
    onSurfaceVariant = AppTheme.TextMuted,

    outline = AppTheme.Hairline,
    outlineVariant = AppTheme.Hairline,

    error = AppTheme.TintRedInk,
    onError = Color.White,
    errorContainer = AppTheme.TintRed,
    onErrorContainer = AppTheme.TintRedInk,

    // Material tinge di blu le superfici in base all'elevazione. Le card AILA sono bianche con
    // un'ombra morbida, non bianche-azzurrate: qui l'effetto viene disattivato.
    surfaceTint = Color.Transparent
)

private fun ailaShapes() = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(AppTheme.SmallElementRadius),
    medium = RoundedCornerShape(AppTheme.ButtonCornerRadius),
    large = RoundedCornerShape(AppTheme.CardCornerRadius),
    extraLarge = RoundedCornerShape(AppTheme.CardCornerRadius + 8.dp)
)

/**
 * Avvolge l'intera app. Va applicato nei punti d'ingresso di piattaforma (MainActivity su
 * Android, MainViewController su iOS) e non dentro le singole schermate, così vale anche per
 * login, caricamento e onboarding.
 */
@Composable
fun AilaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        // Ricalcolati a ogni ricomposizione: dipendono da AppTheme.isDarkMode, che è stato di
        // Compose e cambia dalle Impostazioni.
        colorScheme = ailaColorScheme(),
        shapes = ailaShapes(),
        content = content
    )
}

/**
 * Colori condivisi dei campi di testo: sfondo bianco pieno e bordo appena percettibile, come nel
 * mockup. Senza questo ogni campo usava lo stile di default di Material (bordo grigio scuro,
 * sfondo trasparente), che stonava con le card intorno.
 */
@Composable
fun ailaFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = AppTheme.SurfaceWhite,
    unfocusedContainerColor = AppTheme.SurfaceWhite,
    disabledContainerColor = AppTheme.TintSlate,
    focusedBorderColor = AppTheme.PrimaryBlue,
    unfocusedBorderColor = AppTheme.Hairline,
    focusedLabelColor = AppTheme.PrimaryBlue,
    unfocusedLabelColor = AppTheme.TextMuted,
    cursorColor = AppTheme.PrimaryBlue,
    focusedPlaceholderColor = AppTheme.TextFaint,
    unfocusedPlaceholderColor = AppTheme.TextFaint
)
