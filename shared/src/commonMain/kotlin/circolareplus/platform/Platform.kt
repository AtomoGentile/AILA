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
