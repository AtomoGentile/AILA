package circolareplus.data.mock

import circolareplus.algorithms.DeskAssignment
import circolareplus.algorithms.InterrogationVoteType
import circolareplus.domain.model.*
import circolareplus.ui.screens.SlotUiItem

object MockDataProvider {

    val currentUser = User(
        id = "user_simone",
        firstName = "Simone",
        lastName = "Rossi",
        username = "simone.rossi",
        role = UserRole.REPRESENTATIVE
    )

    val currentProfile = StudentProfile(
        userId = "user_simone",
        heightCm = 175,
        priorityPass = false,
        notificationBoardEnabled = true
    )

    val sampleStudents: List<User> = listOf(
        currentUser,
        User("s_1", "Lorenzo", "Bianchi", "lorenzo"),
        User("s_2", "Giulia", "Verdi", "giulia"),
        User("s_3", "Matteo", "Ferrari", "matteo"),
        User("s_4", "Chiara", "Esposito", "chiara"),
        User("s_5", "Federico", "Romano", "federico"),
        User("s_6", "Sofia", "Gallo", "sofia"),
        User("s_7", "Andrea", "Costa", "andrea")
    )

    val studentsMap: Map<String, User> = sampleStudents.associateBy { it.id }

    val sampleDeskAssignments: List<DeskAssignment> = listOf(
        DeskAssignment(row = 0, column = 0, studentAId = "s_1", studentBId = "s_2"),
        DeskAssignment(row = 0, column = 1, studentAId = "s_3", studentBId = "s_4"),
        DeskAssignment(row = 0, column = 2, studentAId = "s_5", studentBId = "s_6"),
        DeskAssignment(row = 1, column = 0, studentAId = "user_simone", studentBId = "s_7"),
        DeskAssignment(row = 1, column = 1, studentAId = null, studentBId = null),
        DeskAssignment(row = 1, column = 2, studentAId = null, studentBId = null)
    )

    val sampleCirculars: List<Circular> = listOf(
        Circular(
            number = 142,
            title = "Uscita didattica a Palazzo Blu e mostra temporanea",
            publishDate = "2026-09-04",
            r2PdfKey = "circulars/142.pdf"
        ),
        Circular(
            number = 141,
            title = "Corsi pomeridiani facoltativi di potenziamento linguistico (B2/C1)",
            publishDate = "2026-09-03",
            r2PdfKey = "circulars/141.pdf"
        ),
        Circular(
            number = 140,
            title = "Convocazione straordinaria Collegio dei Docenti",
            publishDate = "2026-09-02",
            r2PdfKey = "circulars/140.pdf"
        )
    )

    val sampleClassifications: Map<Int, CircularAiClassification> = mapOf(
        142 to CircularAiClassification(
            circularNumber = 142,
            badge = CircularRelevanceBadge.RELEVANT,
            personalSummary = "Riguarda direttamente la tua classe 4B: autorizzazione e quota entro lunedì per l'uscita a Palazzo Blu.",
            detectedDeadlines = listOf(
                ExtractedDeadline("Quota Palazzo Blu", "2026-09-08", "12:00", "PAGAMENTO")
            )
        ),
        141 to CircularAiClassification(
            circularNumber = 141,
            badge = CircularRelevanceBadge.POTENTIAL,
            personalSummary = "Corso extra pomeridiano facoltativo a frequenza volontaria.",
            detectedDeadlines = emptyList()
        ),
        140 to CircularAiClassification(
            circularNumber = 140,
            badge = CircularRelevanceBadge.NOT_RELEVANT,
            personalSummary = "Comunicazione riservata esclusivamente al corpo docente.",
            detectedDeadlines = emptyList()
        )
    )

    val sampleCalendarEvents: List<CalendarEvent> = listOf(
        CalendarEvent(
            id = "ev_1",
            title = "Verifica di Matematica (Derivate e Studio Funzione)",
            date = "2026-09-05",
            time = "10:00",
            category = CalendarEventCategory.VERIFICA,
            isForAll = true,
            isAiGenerated = false
        ),
        CalendarEvent(
            id = "ev_2",
            title = "Interrogazione di Inglese",
            date = "2026-09-08",
            time = "11:00",
            category = CalendarEventCategory.INTERROGAZIONE,
            isForAll = false,
            isAiGenerated = false
        ),
        CalendarEvent(
            id = "ev_3",
            title = "Pagamento Quota Palazzo Blu (Circolare 142)",
            date = "2026-09-08",
            time = "23:59",
            category = CalendarEventCategory.PAGAMENTO,
            isForAll = true,
            isAiGenerated = true // Inserito da AI
        )
    )

    val sampleProposals: List<Proposal> = listOf(
        Proposal(
            id = "p_1",
            authorName = "Anonimo",
            isAnonymous = true,
            title = "Spostare la verifica di Fisica dal venerdì al martedì",
            description = "Venerdì abbiamo già 6 ore piene e rientriamo tardi. Martedì sarebbe molto più gestibile per tutti.",
            category = "DIDATTICA",
            status = ProposalStatus.NUOVA,
            upvotes = 18,
            downvotes = 2,
            commentsCount = 5,
            isEdited = false,
            createdAt = "2026-09-03"
        ),
        Proposal(
            id = "p_2",
            authorName = "Lorenzo Bianchi",
            isAnonymous = false,
            title = "Organizzazione Torneo d'Istituto di Calcio a 5",
            description = "Proposta per formare la squadra di classe da presentare al rappresentante d'istituto.",
            category = "SPORT",
            status = ProposalStatus.IN_ANALISI,
            upvotes = 22,
            downvotes = 1,
            commentsCount = 8,
            isEdited = true,
            createdAt = "2026-09-01"
        )
    )

    val samplePollSlots: List<SlotUiItem> = listOf(
        SlotUiItem("slot_1", "Lunedì 7 Settembre (Ore 10:00)", "Matematica", 2, isMandatory = false, currentVote = InterrogationVoteType.GREEN),
        SlotUiItem("slot_2", "Mercoledì 9 Settembre (Ore 11:00)", "Matematica", 2, isMandatory = false, currentVote = InterrogationVoteType.YELLOW),
        SlotUiItem("slot_3", "Venerdì 11 Settembre (Ore 09:00)", "Matematica", 2, isMandatory = true, currentVote = InterrogationVoteType.LIGHT_RED)
    )
}
