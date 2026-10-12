package circolareplus.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

/**
 * Le ore di lezione al giorno della classe: 5 (8:00-13:00) o 6 (8:00-14:00). Lo sceglie il
 * Rappresentante alla registrazione e lo cambia dalla Scheda Classe. Il calendario offre solo
 * queste ore e l'AI traduce "alle 9" nell'ora giusta (vedi circolareplus.ai.LessonHours).
 *
 * Stato di Compose, come AppTheme: chi lo legge si ridisegna se cambia. Si tiene anche nelle
 * impostazioni locali, cosi' senza rete resta l'ultimo valore noto.
 */
object ClassSchedule {
    const val SHORT_DAY = 5
    const val LONG_DAY = 6

    var lessonHours: Int by mutableStateOf(AppContainer.settings.classLessonHours)
        private set

    /** Imposta il valore noto (dal server, o appena scelto) e lo ricorda. */
    fun apply(hours: Int) {
        if (hours != SHORT_DAY && hours != LONG_DAY) return
        lessonHours = hours
        AppContainer.settings.classLessonHours = hours
    }

    /** Rilegge le ore dal server; senza rete resta il valore di prima. */
    suspend fun refresh() {
        try {
            apply(AppContainer.preferencesRepository.getClassConfig().lessonHours)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline o server non raggiungibile: vale l'ultimo valore noto.
        }
    }

    /** Solo Rappresentante. Il server rifiuta gli altri e qualunque valore fuori da 5 e 6. */
    suspend fun change(hours: Int) {
        AppContainer.preferencesRepository.setLessonHours(hours)
        apply(hours)
    }

    /** Le due scelte, per i selettori: etichetta per esteso. */
    fun label(hours: Int): String = if (hours == SHORT_DAY) "5 ore (8-13)" else "6 ore (8-14)"
}
