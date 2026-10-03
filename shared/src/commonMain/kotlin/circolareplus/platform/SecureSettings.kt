package circolareplus.platform

import com.russhwolf.settings.Settings

/**
 * Dove tenere i segreti dell'account (token di accesso, chiave Gemini personale).
 *
 * `null` quando le impostazioni normali bastano: su Android stanno nelle SharedPreferences, che
 * il manifest esclude da backup e trasferimento fra telefoni. Su iOS invece NSUserDefaults finisce
 * nei backup di iCloud e del computer, anche in quelli non cifrati, quindi li si mette nel
 * Portachiavi.
 */
expect fun createSecureSettings(): Settings?
