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
/**
 * Colore principale scelto dall'utente (Impostazioni > Aspetto). [BLUE] e' il predefinito, il blu
 * AILA. Per ognuno: primario (chiaro/scuro), "container" tenue e il suo inchiostro, e un tono
 * profondo per i pannelli e i pulsanti tonali. Toni scelti come quelli di Material 3 (tono 40 per
 * il primario in chiaro, 80/30 per i container), cosi' il contrasto del testo regge.
 */
enum class AilaAccent(
    val key: String,
    val label: String,
    val primaryLight: Long, val primaryDark: Long,
    val containerLight: Long, val containerDark: Long,
    val onContainerLight: Long, val onContainerDark: Long,
    val deepLight: Long, val deepDark: Long
) {
    BLUE("blue", "Blu AILA", 0xFF2F5BD3, 0xFF5A8CFF, 0xFFDCE3FF, 0xFF223A7A, 0xFF0B1B45, 0xFFDCE3FF, 0xFF1C3C9A, 0xFF14245A),
    VIOLET("violet", "Viola", 0xFF6B4FD8, 0xFF9A7DFF, 0xFFE9DDFF, 0xFF3E2A7A, 0xFF22005D, 0xFFE9DDFF, 0xFF4A2FA8, 0xFF2A1B5E),
    GREEN("green", "Verde", 0xFF1E8E5A, 0xFF3DBE84, 0xFFC8F2DC, 0xFF1F4D36, 0xFF002111, 0xFFC8F2DC, 0xFF146B43, 0xFF103A27),
    TEAL("teal", "Petrolio", 0xFF00838F, 0xFF33B5C2, 0xFFC7F1F5, 0xFF0E4A50, 0xFF002022, 0xFFC7F1F5, 0xFF00606A, 0xFF073236),
    ORANGE("orange", "Arancio", 0xFFC2590C, 0xFFF08A3E, 0xFFFFDBC8, 0xFF5C2E0F, 0xFF331200, 0xFFFFDBC8, 0xFF8F3F05, 0xFF3F1D05),
    PINK("pink", "Rosa", 0xFFC2185B, 0xFFF0679A, 0xFFFFD9E3, 0xFF5C1734, 0xFF3E001D, 0xFFFFD9E3, 0xFF8E0E43, 0xFF3E0A22);

    companion object {
        val Default = BLUE
        fun fromKey(key: String?): AilaAccent = entries.firstOrNull { it.key == key } ?: Default
    }
}

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

    /** Colore principale scelto (Impostazioni); persiste in LocalSettingsManager. */
    var accent by mutableStateOf(AilaAccent.Default)

    /** Primario, container e toni profondi del colore scelto (vedi [AilaAccent]). */
    val AccentContainer get() = Color(if (isDarkMode) accent.containerDark else accent.containerLight)
    val OnAccentContainer get() = Color(if (isDarkMode) accent.onContainerDark else accent.onContainerLight)
    val AccentDeep get() = Color(if (isDarkMode) accent.deepDark else accent.deepLight)

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
    val PrimaryBlue get() = if (accent == AilaAccent.BLUE) {
        style(Color(0xFF3B82F6), Color(0xFF5A9BFF), Color(0xFF2F5BD3), Color(0xFF5A8CFF))
    } else {
        Color(if (isDarkMode) accent.primaryDark else accent.primaryLight)
    }
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
    // Material: le righe divisorie hanno il colore dello sfondo, cosi' dentro le card tonali
    // diventano "stacchi" fra righe separate, come le liste segmentate di Android 16.
    val Hairline get() = style(Color(0x1F0B1B45), Color(0x1FFFFFFF), Color(0xFFF9F9FF), Color(0xFF111318))
    /** Bordo dei campi di testo (Material: "outline variant", visibile anche dove Hairline non c'e'). */
    val FieldOutline get() = style(Color(0x1F0B1B45), Color(0x1FFFFFFF), Color(0xFFC4C6D0), Color(0xFF44474F))
    /** Fondo dei campi di testo: vetro in Glass, pieno in Expressive. */
    val FieldSurface get() = style(Color(0x66FFFFFF), Color(0x1AFFFFFF), Color(0xFFFFFFFF), Color(0xFF1D2026))

    // --- Card -----------------------------------------------------------------------------------
    /**
     * Fondo delle card. Glass: vetro bianco traslucido (in scuro un velo bianco al 10%) sopra lo
     * sfondo. Expressive: "surface container low", pieno e senza ombra.
     */
    val CardSurface get() = style(Color(0x4DFFFFFF), Color(0x14FFFFFF), Color(0xFFF1F2FB), Color(0xFF1D2026))
    /** Filo di luce sul bordo del vetro; in Expressive nessun bordo. */
    val CardBorder get() = style(Color(0xFFFFFFFF), Color(0x24FFFFFF), Color.Transparent, Color.Transparent)

    // --- Selezione (selettori a segmenti, chip, voce attiva della barra) ------------------------
    /**
     * Glass: la selezione e' una capsula bianca "in rilievo" con testo scuro, come i segmented
     * control di iOS. Expressive: il "secondary container" di Material, pieno.
     */
    val SelectionFill: Brush
        get() = SolidColor(if (!isGlass && accent != AilaAccent.BLUE) AccentContainer
            else style(Color(0xF2FFFFFF), Color(0x47FFFFFF), Color(0xFFD9E2FF), Color(0xFF34457A)))
    val OnSelection get() = if (!isGlass && accent != AilaAccent.BLUE) OnAccentContainer
        else style(Color(0xFF0B0D12), Color(0xFFFFFFFF), Color(0xFF0B1B45), Color(0xFFDCE3FF))
    /** Fondo del binario su cui scorre la selezione. */
    val TrackFill get() = style(Color(0x4DFFFFFF), Color(0x1AFFFFFF), Color(0xFFE6E8F3), Color(0xFF282B32))

    // --- Gradienti ----------------------------------------------------------------------------
    val HeroGradientTop get() = if (isDarkMode) Color(0xFF141C3A) else Color(0xFF1B2E7A)
    val HeroGradientMid get() = if (isDarkMode) Color(0xFF23408F) else Color(0xFF2F5BD8)
    val HeroGradientBottom get() = if (isDarkMode) Color(0xFF4B3AA8) else Color(0xFF7B4FE3)
    val HeroGradient: Brush
        get() = if (isGlass) {
            // Vetro chiaro, non un pannello colorato: il colore lo da' lo sfondo dietro. Il
            // gradiente blu-viola pieno faceva l'app "giocattolo".
            if (isDarkMode) Brush.verticalGradient(listOf(Color(0x29FFFFFF), Color(0x0FFFFFFF)))
            else Brush.verticalGradient(listOf(Color(0x80FFFFFF), Color(0x40FFFFFF)))
        } else {
            // Material Expressive: il pannello e' il primario "pieno" in due toni vicini, senza la
            // virata al viola del gradiente di Glass.
            if (accent == AilaAccent.BLUE) {
                if (isDarkMode) Brush.linearGradient(listOf(Color(0xFF1B2F6E), Color(0xFF263F8C)))
                else Brush.linearGradient(listOf(Color(0xFF2A52C4), Color(0xFF3A63D8)))
            } else Brush.linearGradient(listOf(AccentDeep, lerpColor(AccentDeep, PrimaryBlue, 0.45f)))
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
            // Glass: tinta piena e piatta come i pulsanti "prominent" di iOS 26. Il gradiente
            // lucido con riflesso e bordo bianco sembrava un giocattolo.
            else -> SolidColor(PrimaryBlue)
        }

    // Testo/superfici sopra HeroGradient (contrasto chiaro su sfondo scuro): uguali nei due temi,
    // perché il pannello è scuro in entrambi.
    // In Liquid Glass il pannello non e' piu' un blocco colorato ma vetro chiaro (piu' sobrio,
    // come iOS): il testo sopra diventa scuro e i riquadri vetro bianco.
    val OnHeroPrimary get() = if (isGlass) TextDark else Color(0xFFFFFFFF)
    val OnHeroSecondary get() = if (isGlass) TextMuted else Color(0xCCFFFFFF)
    val OnHeroSurface get() = if (isGlass) (if (isDarkMode) Color(0x1FFFFFFF) else Color(0x73FFFFFF)) else Color(0x2EFFFFFF)
    val OnHeroBorder get() = if (isGlass) (if (isDarkMode) Color(0x40FFFFFF) else Color(0xE6FFFFFF)) else Color(0x33FFFFFF)

    // --- Tinte dei riquadri icona -------------------------------------------------------------
    // In tema scuro lo sfondo tenue diventa una velatura del colore e il segno si schiarisce,
    // altrimenti i riquadri chiarissimi sparerebbero luce in mezzo a una schermata scura.
    // Expressive: il blu e' il "primary container" di Material (piu' saturo del tenue di Glass).
    val TintBlue get() = if (accent != AilaAccent.BLUE) AccentContainer
        else style(Color(0xFFE8F0FF), Color(0xFF1B2A4A), Color(0xFFDCE3FF), Color(0xFF223A7A))
    val TintBlueInk get() = if (accent != AilaAccent.BLUE) OnAccentContainer
        else style(Color(0xFF1E40AF), Color(0xFF93C5FD), Color(0xFF0B1B45), Color(0xFFDCE3FF))
    val TintViolet get() = if (isDarkMode) Color(0xFF261F4A) else Color(0xFFF5F3FF)
    val TintVioletInk get() = if (isDarkMode) Color(0xFFC4B5FD) else Color(0xFF5B21B6)
    val TintAmber get() = if (isDarkMode) Color(0xFF352A16) else Color(0xFFFFFBEB)
    val TintAmberInk get() = if (isDarkMode) Color(0xFFFCD34D) else Color(0xFF92400E)
    val TintGreen get() = if (isDarkMode) Color(0xFF13301F) else Color(0xFFF0FDF4)
    val TintGreenInk get() = if (isDarkMode) Color(0xFF86EFAC) else Color(0xFF166534)
    val TintRed get() = if (isDarkMode) Color(0xFF351A1D) else Color(0xFFFEF2F2)
    val TintRedInk get() = if (isDarkMode) Color(0xFFFCA5A5) else Color(0xFF991B1B)
    // Glass: velo bianco smerigliato (i riempimenti neutri di iOS sopra uno sfondo colorato).
    val TintSlate get() = style(Color(0x59FFFFFF), Color(0x1AFFFFFF), Color(0xFFE6E8F3), Color(0xFF282B32))
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

/** Interpolazione lineare tra due colori (usata per derivare i toni dall'accento scelto). */
internal fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t
)
