package circolareplus.platform

import com.russhwolf.settings.Settings

// Nel browser non c'e' un portachiavi: le impostazioni vanno nel localStorage dell'origine, che
// non finisce in nessun backup.
actual fun createSecureSettings(): Settings? = null
