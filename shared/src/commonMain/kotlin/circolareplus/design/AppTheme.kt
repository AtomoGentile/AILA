package circolareplus.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * I due linguaggi grafici dell'app, scelti dalle Impostazioni.
 *
 * - [GLASS] "Liquid Glass" (iOS 26): superfici chiare e traslucide con un filo di luce sul bordo,
 *   capsule, selezioni bianche "in rilievo", molle morbide, push di navigazione da destra.
 * - [EXPRESSIVE] "Material 3 Expressive" (Android 16): superfici piene a toni (surface
 *   container), niente ombre, colore primario pieno invece del gradiente, forme che si
 *   deformano alla pressione, molle piu' rimbalzanti, transizioni "shared axis" e "fade through".
 * - [AUTO] quello della piattaforma: Glass su iPhone/iPad, Expressive su Android.
 */
enum class UiStyle(val key: String, val label: String) {
    AUTO("auto", "Automatico"),
    GLASS("glass", "Liquid Glass"),
    EXPRESSIVE("expressive", "Material");

    companion object {
        fun fromKey(key: String?): UiStyle = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

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
 * **Due stili, [UiStyle].** Oltre a chiaro/scuro c'e' lo stile grafico: Liquid Glass o Material
 * Expressive. Funziona come il tema scuro: [uiStyle] e' stato di Compose e i valori che cambiano
 * fra i due stili sono `get()`, quindi le schermate non devono sapere quale e' attivo. I
 * componenti condivisi (card, pulsanti, barra, selettori) leggono [isGlass] dove anche la forma o
 * il movimento cambiano, non solo il colore.
 *
 * I nomi restano quelli originali anche quando in tema scuro il valore non è più "bianco":
 * rinominarli avrebbe voluto dire toccare ogni schermata per un guadagno solo estetico.
 */
object AppTheme {

    /** Tema scuro attivo. Lo imposta la schermata Impostazioni; persiste in LocalSettingsManager. */
    var isDarkMode by mutableStateOf(false)

    /** Stile grafico scelto (Impostazioni); persiste in LocalSettingsManager. */
    var uiStyle by mutableStateOf(UiStyle.AUTO)

    /** true = Liquid Glass, false = Material Expressive (con AUTO decide la piattaforma). */
    val isGlass: Boolean
        get() = when (uiStyle) {
            UiStyle.GLASS -> true
            UiStyle.EXPRESSIVE -> false
            UiStyle.AUTO -> circolareplus.platform.isIos()
        }

    /** Sceglie fra quattro varianti: Glass chiaro/scuro, Expressive chiaro/scuro. */
    private fun <T> style(glassLight: T, glassDark: T, expLight: T, expDark: T): T =
        if (isGlass) { if (isDarkMode) glassDark else glassLight }
        else { if (isDarkMode) expDark else expLight }

    // --- Dimensioni e raggi ------------------------------------------------------------------
    // Glass: angoli ampi e "continui" come le card di iOS. Expressive: la scala di forme di M3E,
    // card "extra large" (28) ed elementi piccoli a 16.
    val CardCornerRadius: Dp get() = if (isGlass) 24.dp else 28.dp
    /** Capsula in entrambi gli stili (in Expressive si deforma alla pressione, vedi i pulsanti). */
    val ButtonCornerRadius: Dp get() = 100.dp
    val SmallElementRadius: Dp get() = if (isGlass) 14.dp else 16.dp

    /**
     * Nessuna ombra Material in entrambi gli stili: in Glass sotto una superficie traslucida si
     * vedrebbe attraverso, in Expressive la profondita' la danno i toni delle superfici.
     */
    val CardElevation: Dp get() = 0.dp
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

    // --- Colori brand -------------------------------------------------------------------------
    // Expressive usa il "tono 40" del blu AILA (piu' profondo, come i primari di Material) in
    // chiaro; in scuro resta il blu luminoso, che regge il testo bianco sopra.
    val PrimaryBlue get() = style(Color(0xFF3B82F6), Color(0xFF5A9BFF), Color(0xFF2F5BD3), Color(0xFF5A8CFF))
    val SecondaryIndigo get() = if (isDarkMode) Color(0xFFA78BFA) else Color(0xFF8B5CF6)
    val AccentCyan = Color(0xFF06B6D4)

    // --- Superfici e testo -------------------------------------------------------------------
    // Glass: sfondo grigio-azzurro chiaro (nero quasi puro in scuro, come iOS), testi dai toni di
    // "label" di Apple. Expressive: i ruoli "surface" di Material 3 generati dal blu AILA.
    // Glass: trasparente — lo sfondo vero e' ailaGlassBackdrop (macchie di colore), dipinto una
    // volta sola dietro a tutta l'app; le schermate lo lasciano vedere.
    val BackgroundLight get() = style(Color.Transparent, Color.Transparent, Color(0xFFF9F9FF), Color(0xFF111318))
    /** Superfici opache: intestazioni, campi, dialoghi. */
    val SurfaceWhite get() = style(Color(0xFFFFFFFF), Color(0xFF14171F), Color(0xFFFFFFFF), Color(0xFF1D2026))
    val TextDark get() = style(Color(0xFF0B0D12), Color(0xFFFFFFFF), Color(0xFF1A1C22), Color(0xFFE2E2E9))
    val TextMuted get() = style(Color(0xFF5F6470), Color(0xFFB8BCC8), Color(0xFF44474F), Color(0xFFC4C6D0))
    val TextFaint get() = style(Color(0xFF8E929C), Color(0xFF7C8190), Color(0xFF74777F), Color(0xFF8E9099))
    val Hairline get() = style(Color(0x1F0B1B45), Color(0x1FFFFFFF), Color(0xFFDDE0EA), Color(0xFF3A3D45))
    /** Fondo dei campi di testo: vetro in Glass, pieno in Expressive. */
    val FieldSurface get() = style(Color(0x99FFFFFF), Color(0x1FFFFFFF), Color(0xFFFFFFFF), Color(0xFF1D2026))

    // --- Card -----------------------------------------------------------------------------------
    /**
     * Fondo delle card. Glass: vetro bianco traslucido (in scuro un velo bianco al 10%) sopra lo
     * sfondo. Expressive: "surface container low", pieno e senza ombra.
     */
    val CardSurface get() = style(Color(0x73FFFFFF), Color(0x1AFFFFFF), Color(0xFFF1F2FB), Color(0xFF1D2026))
    /** Filo di luce sul bordo del vetro; in Expressive nessun bordo. */
    val CardBorder get() = style(Color(0xFFFFFFFF), Color(0x24FFFFFF), Color.Transparent, Color.Transparent)

    // --- Selezione (selettori a segmenti, chip, voce attiva della barra) ------------------------
    /**
     * Glass: la selezione e' una capsula bianca "in rilievo" con testo scuro, come i segmented
     * control di iOS. Expressive: il "secondary container" di Material, pieno.
     */
    val SelectionFill: Brush
        get() = SolidColor(style(Color(0xF2FFFFFF), Color(0x47FFFFFF), Color(0xFFD9E2FF), Color(0xFF34457A)))
    val OnSelection get() = style(Color(0xFF0B0D12), Color(0xFFFFFFFF), Color(0xFF0B1B45), Color(0xFFDCE3FF))
    /** Fondo del binario su cui scorre la selezione. */
    val TrackFill get() = style(Color(0x80FFFFFF), Color(0x1FFFFFFF), Color(0xFFE6E8F3), Color(0xFF282B32))

    // --- Gradienti ----------------------------------------------------------------------------
    val HeroGradientTop get() = if (isDarkMode) Color(0xFF141C3A) else Color(0xFF1B2E7A)
    val HeroGradientMid get() = if (isDarkMode) Color(0xFF23408F) else Color(0xFF2F5BD8)
    val HeroGradientBottom get() = if (isDarkMode) Color(0xFF4B3AA8) else Color(0xFF7B4FE3)
    val HeroGradient: Brush
        get() = if (isGlass) {
            // Vetro colorato: lo stesso gradiente, ma lascia intravedere lo sfondo.
            Brush.linearGradient(
                listOf(HeroGradientTop.copy(alpha = 0.82f), HeroGradientMid.copy(alpha = 0.78f), HeroGradientBottom.copy(alpha = 0.74f))
            )
        } else {
            // Material Expressive: il pannello e' il primario "pieno" in due toni vicini, senza la
            // virata al viola del gradiente di Glass.
            if (isDarkMode) Brush.linearGradient(listOf(Color(0xFF1B2F6E), Color(0xFF263F8C)))
            else Brush.linearGradient(listOf(Color(0xFF2A52C4), Color(0xFF3A63D8)))
        }

    /** Variante più profonda, per splash e onboarding. */
    val HeroGradientDeep
        get() = Brush.linearGradient(
            listOf(Color(0xFF0B1330), Color(0xFF1B2E7A), Color(0xFF4B3AA8))
        )

    /** Riempimento dei pulsanti primari: nel mockup non sono blu piatto ma sfumati. */
    val PrimaryGradient: Brush
        get() = when {
            // Material Expressive: il colore primario e' pieno, non sfumato.
            !isGlass -> SolidColor(PrimaryBlue)
            isDarkMode -> Brush.horizontalGradient(listOf(Color(0xFF3E7BE0), Color(0xFF6B57D6)))
            else -> Brush.horizontalGradient(listOf(Color(0xFF3B82F6), Color(0xFF6D5CE7)))
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
    // Expressive: il blu e' il "primary container" di Material (piu' saturo del tenue di Glass).
    val TintBlue get() = style(Color(0xFFE8F0FF), Color(0xFF1B2A4A), Color(0xFFDCE3FF), Color(0xFF223A7A))
    val TintBlueInk get() = style(Color(0xFF1E40AF), Color(0xFF93C5FD), Color(0xFF0B1B45), Color(0xFFDCE3FF))
    val TintViolet get() = if (isDarkMode) Color(0xFF261F4A) else Color(0xFFF5F3FF)
    val TintVioletInk get() = if (isDarkMode) Color(0xFFC4B5FD) else Color(0xFF5B21B6)
    val TintAmber get() = if (isDarkMode) Color(0xFF352A16) else Color(0xFFFFFBEB)
    val TintAmberInk get() = if (isDarkMode) Color(0xFFFCD34D) else Color(0xFF92400E)
    val TintGreen get() = if (isDarkMode) Color(0xFF13301F) else Color(0xFFF0FDF4)
    val TintGreenInk get() = if (isDarkMode) Color(0xFF86EFAC) else Color(0xFF166534)
    val TintRed get() = if (isDarkMode) Color(0xFF351A1D) else Color(0xFFFEF2F2)
    val TintRedInk get() = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFF991B1B)
    // Glass: velo bianco smerigliato (i riempimenti neutri di iOS sopra uno sfondo colorato).
    val TintSlate get() = style(Color(0x80FFFFFF), Color(0x1FFFFFFF), Color(0xFFE6E8F3), Color(0xFF282B32))
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
