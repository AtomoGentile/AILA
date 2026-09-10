package circolareplus.algorithms

import kotlinx.serialization.Serializable

@Serializable
enum class InterrogationVoteType(val score: Int, val maxAllowed: Int?) {
    GREEN(50, 3),          // Prima scelta (Max 3)
    YELLOW(0, null),       // Neutro / Disponibile (Illimitati)
    LIGHT_RED(-80, 3),     // Sconsigliato (Max 3)
    DARK_RED(-300, 2);     // Blocco grave / Veto (Max 2)

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

    /**
     * Valida che i voti espressi dallo studente rispettino i limiti di budget:
     * - Max 3 Verdi (+50)
     * - Illimitati Gialli (0)
     * - Max 3 Rossi Chiari (-80)
     * - Max 2 Rossi Scuri (-300)
     */
    fun validateStudentVoteBudget(votes: List<StudentInterrogationVote>): Result<Unit> {
        val greenCount = votes.count { it.voteType == InterrogationVoteType.GREEN }
        val lightRedCount = votes.count { it.voteType == InterrogationVoteType.LIGHT_RED }
        val darkRedCount = votes.count { it.voteType == InterrogationVoteType.DARK_RED }

        if (greenCount > 3) {
            return Result.failure(IllegalArgumentException("Hai superato il limite massimo di 3 scelte Verdi (attuali: $greenCount)."))
        }
        if (lightRedCount > 3) {
            return Result.failure(IllegalArgumentException("Hai superato il limite massimo di 3 voti Rosso Chiaro (attuali: $lightRedCount)."))
        }
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
        val baseScore = vote?.voteType?.score ?: InterrogationVoteType.YELLOW.score

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
     * - Assegnazione passata su Rosso Chiaro: +100 pt
     * - Assegnazione passata su Rosso Scuro: +250 pt
     */
    fun calculateNextSacrificeBonus(
        currentBonus: Int,
        assignedVoteType: InterrogationVoteType
    ): Int {
        return when (assignedVoteType) {
            InterrogationVoteType.GREEN -> 0 // Bonus consumato e azzerato
            InterrogationVoteType.YELLOW -> currentBonus // Non consumato né aumentato
            InterrogationVoteType.LIGHT_RED -> currentBonus + 100
            InterrogationVoteType.DARK_RED -> currentBonus + 250
        }
    }
}
