package circolareplus.ai

/**
 * Estrae il testo grezzo da un PDF (le circolari scaricate dalla cache R2 del Worker), in modo
 * da poterlo passare a [ClientSideAiClassifier] per la classificazione AI locale.
 *
 * Implementazione specifica per piattaforma:
 * - Android: PdfBox-Android (fork puro-Java di Apache PDFBox, nessuna dipendenza AWT/Swing).
 * - iOS: PDFKit nativo (Apple), nessuna libreria terza necessaria.
 *
 * L'estrazione avviene interamente sul dispositivo: nessun byte del PDF lascia il telefono per
 * questo scopo (coerente con la privacy-by-design della classificazione AI, vedi
 * ClientSideAiClassifier). Se l'estrazione fallisce (PDF scansionato senza testo, file corrotto,
 * ecc.) l'implementazione restituisce stringa vuota invece di lanciare, così il chiamante può
 * ricadere sulla classificazione euristica basata solo sul titolo.
 */
expect class PdfTextExtractor() {
    suspend fun extractText(pdfBytes: ByteArray): String
}
