package circolareplus.algorithms

import kotlinx.serialization.Serializable

@Serializable
enum class InterrogationVoteType(val score: Int, val maxAllowed: Int?) {
    GREEN(50, null),       // Prima scelta (Illimitati)
    YELLOW(0, null),       // Neutro / Disponibile (Illimitati)
    LIGHT_RED(-80, null),  // Sconsigliato (Illimitati; il server li diluisce oltre il 40% delle date)
    DARK_RED(-300, 2);     // Blocco grave / Veto (Max 2)

    /** Punti usati nel calcolo. Il Rosso Chiaro resta -80 come codice del voto, ma pesa -60. */
    val points: Int get() = if (this == LIGHT_RED) -60 else score

    companion object {
        fun fromScore(score: Int): InterrogationVoteType =
            entries.firstOrNull { it.score == score } ?: YELLOW
    }
}

@Serializable
data class StudentInterrogationVote(
    val studentId: String,
    val slotId: String,
    val voteType: InterrogationVoteType
)

data class InterrogationSlotInfo(
    val slotId: String,
    val dateIso: String,
    val capacity: Int,
    val isTeacherMandatory: Boolean = false,
    val isCompitoInClasseForAll: Boolean = false // Se per tutti nello stesso giorno, penalità azzerata (0 pt)
)

data class SlotAssignmentResult(
    val slotId: String,
    val studentId: String,
    val assignedScore: Int,
    val sacrificeBonusApplied: Int
)

/**
 * Modulo Sondaggi: Gestione Interrogazioni e Slot Date (Specifica Tecnica v1.0 / Master v3.0)
 */
object SondaggiEngine {

    const val SACRIFICE_LIGHT_RED = 40
    const val SACRIFICE_DARK_RED = 150
    const val MAX_SACRIFICE_BONUS = 300

    /**
     * Valida che i voti espressi dallo studente rispettino i limiti di budget:
     * - Illimitati Verdi (+50), Gialli (0) e Rossi Chiari (-60)
     * - Max 2 Rossi Scuri (-300): è il veto, l'unico tetto rigido
     */
    fun validateStudentVoteBudget(votes: List<StudentInterrogationVote>): Result<Unit> {
        val darkRedCount = votes.count { it.voteType == InterrogationVoteType.DARK_RED }

        if (darkRedCount > 2) {
            return Result.failure(IllegalArgumentException("Hai superato il limite massimo di 2 voti di blocco Rosso Scuro (attuali: $darkRedCount)."))
        }
        return Result.success(Unit)
    }

    /**
     * Calcola il punteggio finale di associazione per lo Studente A allo Slot X:
     * Punteggio Finale = Pvoto + Psacrificio
     * Dove se il giorno ha una verifica "Per Tutti", la penalità viene annullata (0 pt)
     */
    fun calculateSlotScore(
        vote: StudentInterrogationVote?,
        slot: InterrogationSlotInfo,
        accumulatedSacrificeBonus: Int
    ): Int {
        val baseScore = vote?.voteType?.points ?: InterrogationVoteType.YELLOW.points

        // Gestione Sovrapposizioni: se presente verifica 'Per Tutti' nello stesso giorno, la penalità si annulla (0 pt)
        val adjustedVoteScore = if (slot.isCompitoInClasseForAll && baseScore < 0) {
            0
        } else {
            baseScore
        }

        // Applicazione del Bonus Sacrificio: incrementa il valore del voto Verde (+50 + Psacrificio)
        val finalScore = if (vote?.voteType == InterrogationVoteType.GREEN) {
            adjustedVoteScore + accumulatedSacrificeBonus
        } else {
            adjustedVoteScore
        }

        return finalScore
    }

    /**
     * Aggiorna il bonus sacrificio per il giro successivo in base all'esito dell'assegnazione:
     * - Assegnazione passata su Verde / Giallo: Psacrificio = 0 pt (Reset se ottenuto Verde tramite bonus)
     * - Assegnazione passata su Rosso Chiaro: +40 pt
     * - Assegnazione passata su Rosso Scuro: +150 pt
     */
    fun calculateNextSacrificeBonus(
        currentBonus: Int,
        assignedVoteType: InterrogationVoteType
    ): Int {
        return when (assignedVoteType) {
            InterrogationVoteType.GREEN -> 0 // Bonus consumato e azzerato
            InterrogationVoteType.YELLOW -> currentBonus // Non consumato né aumentato
            InterrogationVoteType.LIGHT_RED -> (currentBonus + SACRIFICE_LIGHT_RED).coerceAtMost(MAX_SACRIFICE_BONUS)
            InterrogationVoteType.DARK_RED -> (currentBonus + SACRIFICE_DARK_RED).coerceAtMost(MAX_SACRIFICE_BONUS)
        }
    }
}
