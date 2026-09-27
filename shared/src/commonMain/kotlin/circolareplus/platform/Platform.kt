package circolareplus.platform

/**
 * Riconosce la piattaforma di runtime.
 *
 * Usato per applicare logica platform-specific senza dipendenze da moduli
 * di configurazione - per esempio, nascondere la sezione "Scarica modelli" su iOS
 * dove l'AI è quella di sistema.
 */

expect fun isIos(): Boolean

expect fun isAndroid(): Boolean

/**
 * Raggio degli angoli arrotondati dello schermo del telefono (0 se lo schermo e' squadrato o non
 * si riesce a saperlo). Serve alle superfici che arrivano fino al bordo in alto (il pannello della
 * Home in Liquid Glass): con gli angoli squadrati il loro bordo di luce finiva sotto la curva
 * dello schermo e se ne perdeva un pezzo proprio dove il riflesso e' piu' forte.
 */
@androidx.compose.runtime.Composable
expect fun displayCornerRadius(): androidx.compose.ui.unit.Dp
