package circolareplus.ai.assistant

/**
 * Testo gia' estratto dei PDF delle circolari (allegati compresi), per numero.
 *
 * Lo riempiono sia l'analisi delle circolari sia l'assistente: il PDF che l'analisi ha appena
 * scaricato e letto serve anche a rispondere a "quando inizia il corso di teatro?", senza
 * riscaricarlo. Resta in memoria per la durata dell'app, con un tetto sul numero di circolari
 * perche' un testo con gli allegati puo' pesare parecchie decine di KB.
 */
object CircularTextCache {

    private const val MAX_ENTRIES = 40

    private val texts = LinkedHashMap<Int, String>()

    fun get(number: Int): String? = texts[number]

    fun put(number: Int, text: String) {
        if (text.isBlank()) return
        texts.remove(number)
        texts[number] = text
        while (texts.size > MAX_ENTRIES) texts.remove(texts.keys.first())
    }
}
