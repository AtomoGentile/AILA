package circolareplus.platform

import com.russhwolf.settings.Settings

// Le preferenze sono gia' escluse da backup e trasferimento (res/xml/data_extraction_rules.xml).
actual fun createSecureSettings(): Settings? = null
