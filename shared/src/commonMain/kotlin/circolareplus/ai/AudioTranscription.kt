package circolareplus.ai

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class AudioTranscription(val text:String,val label:String)

/** Recheck live consent after recognition and before any remote fallback. */
internal suspend fun transcribeVoice(
    preferLocal:Boolean, localOnly:Boolean, audioAllowed:()->Boolean, cloudAllowed:()->Boolean,
    local:suspend ()->String?, cloud:suspend ()->AudioTranscription
):AudioTranscription {
    check(audioAllowed()){ "Attiva la trascrizione e il consenso audio nelle impostazioni AI." }
    if(preferLocal || localOnly) {
        val text=local()
        currentCoroutineContext().ensureActive()
        check(audioAllowed()){ "La trascrizione audio è stata disattivata." }
        if(!text.isNullOrBlank())return AudioTranscription(text,"Trascritto sul dispositivo")
    }
    check(!localOnly){ "Il motore vocale locale o la lingua italiana non sono disponibili per questo file." }
    check(cloudAllowed()){ "Consenti l’elaborazione cloud degli allegati audio nelle impostazioni AI." }
    val result=cloud()
    currentCoroutineContext().ensureActive()
    check(audioAllowed()&&cloudAllowed()){ "Il consenso all’elaborazione audio è stato disattivato." }
    check(result.text.isNotBlank()){ "Non è stato riconosciuto testo nel vocale." }
    return result
}
