package circolareplus.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design System AILA: palette, raggi e spaziatura dal brand kit.
 *
 * **Perché i colori sono proprietà calcolate e non costanti.** Serviva un tema chiaro/scuro
 * commutabile dalle impostazioni. L'alternativa canonica in Compose è un CompositionLocal, ma
 * avrebbe richiesto di riscrivere ogni riferimento `AppTheme.X` in una ventina di schermate. Qui
 * invece [isDarkMode] è stato di Compose (`mutableStateOf`) e ogni colore è un `get()`: leggere
 * `AppTheme.SurfaceWhite` dentro un composable registra la lettura nello snapshot, quindi
 * cambiando l'interruttore tutta l'interfaccia si ridisegna da sola senza che nessuna schermata
 * debba saperlo.
 *
 * **Non esiste più uno "stile iOS".** C'era un secondo linguaggio grafico attivabile da
 * Impostazioni — card in vetro sfocato, barra di navigazione traslucida, selettori data/ora a
 * rotellina — costruito con componenti Material: restava un'imitazione di Cupertino ed è stato
 * rimosso. Material 3 vale ora su tutte le piattaforme.
 *
 * I nomi restano quelli originali anche quando in tema scuro il valore non è più "bianco":
 * rinominarli avrebbe voluto dire toccare ogni schermata per un guadagno solo estetico.
 */
object AppTheme {

    /** Tema scuro attivo. Lo imposta la schermata Impostazioni; persiste in LocalSettingsManager. */
    var isDarkMode by mutableStateOf(false)

    // --- Dimensioni e raggi ------------------------------------------------------------------
    val CardCornerRadius = 20.dp
    val ButtonCornerRadius = 14.dp
    val SmallElementRadius = 12.dp

    val CardElevation = 3.dp
    val CardElevationPressed = 8.dp

    // Griglia modulare a base 4dp
    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space20 = 20.dp
    val Space24 = 24.dp
    val Space32 = 32.dp
    val Space48 = 48.dp

    // --- Colori brand (uguali nei due temi: sono l'identità) ---------------------------------
    val PrimaryBlue get() = if (isDarkMode) Color(0xFF5A9BFF) else Color(0xFF3B82F6)
    val SecondaryIndigo get() = if (isDarkMode) Color(0xFFA78BFA) else Color(0xFF8B5CF6)
    val AccentCyan = Color(0xFF06B6D4)

    // --- Superfici e testo -------------------------------------------------------------------
    val BackgroundLight get() = if (isDarkMode) Color(0xFF0B1020) else Color(0xFFF6F8FE)
    val SurfaceWhite get() = if (isDarkMode) Color(0xFF161D31) else Color(0xFFFFFFFF)
    val TextDark get() = if (isDarkMode) Color(0xFFFAFBFC) else Color(0xFF0F172A)
    val TextMuted get() = if (isDarkMode) Color(0xFFBFCAD9) else Color(0xFF64748B)
    val TextFaint get() = if (isDarkMode) Color(0xFF9AABBD) else Color(0xFF94A3B8)
    val Hairline get() = if (isDarkMode) Color(0xFF243049) else Color(0xFFE8EDF5)

    // --- Gradienti ----------------------------------------------------------------------------
    val HeroGradientTop get() = if (isDarkMode) Color(0xFF141C3A) else Color(0xFF1B2E7A)
    val HeroGradientMid get() = if (isDarkMode) Color(0xFF23408F) else Color(0xFF2F5BD8)
    val HeroGradientBottom get() = if (isDarkMode) Color(0xFF4B3AA8) else Color(0xFF7B4FE3)
    val HeroGradient
        get() = Brush.linearGradient(listOf(HeroGradientTop, HeroGradientMid, HeroGradientBottom))

    /** Variante più profonda, per splash e onboarding. */
    val HeroGradientDeep
        get() = Brush.linearGradient(
            listOf(Color(0xFF0B1330), Color(0xFF1B2E7A), Color(0xFF4B3AA8))
        )

    /** Riempimento dei pulsanti primari: nel mockup non sono blu piatto ma sfumati. */
    val PrimaryGradient
        get() = if (isDarkMode) {
            Brush.horizontalGradient(listOf(Color(0xFF3E7BE0), Color(0xFF6B57D6)))
        } else {
            Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF6D5CE7)))
        }

    // Testo/superfici sopra HeroGradient (contrasto chiaro su sfondo scuro): uguali nei due temi,
    // perché il pannello è scuro in entrambi.
    val OnHeroPrimary = Color(0xFFFFFFFF)
    val OnHeroSecondary = Color(0xCCFFFFFF)
    val OnHeroSurface = Color(0x2EFFFFFF)
    val OnHeroBorder = Color(0x33FFFFFF)

    // --- Tinte dei riquadri icona -------------------------------------------------------------
    // In tema scuro lo sfondo tenue diventa una velatura del colore e il segno si schiarisce,
    // altrimenti i riquadri chiarissimi sparerebbero luce in mezzo a una schermata scura.
    val TintBlue get() = if (isDarkMode) Color(0xFF1B2A4A) else Color(0xFFF0F7FF)
    val TintBlueInk get() = if (isDarkMode) Color(0xFF93C5FD) else Color(0xFF1E40AF)
    val TintViolet get() = if (isDarkMode) Color(0xFF261F4A) else Color(0xFFF5F3FF)
    val TintVioletInk get() = if (isDarkMode) Color(0xFFC4B5FD) else Color(0xFF5B21B6)
    val TintAmber get() = if (isDarkMode) Color(0xFF352A16) else Color(0xFFFFFBEB)
    val TintAmberInk get() = if (isDarkMode) Color(0xFFFCD34D) else Color(0xFF92400E)
    val TintGreen get() = if (isDarkMode) Color(0xFF13301F) else Color(0xFFF0FDF4)
    val TintGreenInk get() = if (isDarkMode) Color(0xFF86EFAC) else Color(0xFF166534)
    val TintRed get() = if (isDarkMode) Color(0xFF351A1D) else Color(0xFFFEF2F2)
    val TintRedInk get() = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFF991B1B)
    val TintSlate get() = if (isDarkMode) Color(0xFF222B3F) else Color(0xFFF1F5F9)
    val TintSlateInk get() = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569)

    // --- Badge e indicatori --------------------------------------------------------------------
    val BadgeRelevantGreen = Color(0xFF10B981)     // "Ti riguarda"
    val BadgePotentialYellow = Color(0xFFF59E0B)   // "Potenziale interesse"
    val BadgeNotRelevantGray = Color(0xFF94A3B8)   // "Non sembra riguardarti"

    val PollGreen = Color(0xFF22C55E)
    val PollYellow = Color(0xFFEAB308)
    val PollLightRed = Color(0xFFF87171)
    val PollDarkRed = Color(0xFFDC2626)
}
