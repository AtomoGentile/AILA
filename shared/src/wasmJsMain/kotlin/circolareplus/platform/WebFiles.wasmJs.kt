package circolareplus.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.await
import kotlin.io.encoding.Base64
import kotlin.js.Promise

/**
 * File sul web: condivisione o download di un PDF in memoria e selettore di PDF dal dispositivo.
 * Usati dall'esportazione della mappa posti e dai documenti della Gita.
 */
actual suspend fun sharePdfFile(bytes: ByteArray, fileName: String) {
    shareOrDownload(Base64.encode(bytes), fileName, fileName.substringBeforeLast('.')).await<JsAny?>()
}

@Composable
actual fun rememberPdfFilePicker(onPicked: (fileName: String, bytes: ByteArray) -> Unit): () -> Unit {
    val currentOnPicked = rememberUpdatedState(onPicked)
    return remember {
        {
            // Il selettore si puo' aprire solo dentro il tocco dell'utente: questa lambda viene
            // chiamata dal tocco, e l'input nasce e si apre li', senza attese in mezzo.
            openPdfPicker { name, base64 ->
                val bytes = try {
                    Base64.decode(base64)
                } catch (e: IllegalArgumentException) {
                    null
                }
                if (bytes != null) currentOnPicked.value(name, bytes)
            }
        }
    }
}

private fun openPdfPicker(onPicked: (String, String) -> Unit) {
    js(
        "(function () { var input = document.createElement('input'); input.type = 'file'; input.accept = 'application/pdf,.pdf'; " +
            "input.style.display = 'none'; " +
            "input.onchange = function () { var f = input.files && input.files[0]; if (!f) { input.remove(); return; } " +
            "var r = new FileReader(); r.onload = function () { var s = String(r.result); onPicked(f.name, s.substring(s.indexOf(',') + 1)); input.remove(); }; " +
            "r.onerror = function () { input.remove(); }; r.readAsDataURL(f); }; " +
            "input.oncancel = function () { input.remove(); }; " +
            "document.body.appendChild(input); input.click(); })()"
    )
}

// Su telefoni e tablet si apre il foglio di condivisione con il file, come fa l'app nativa; sui
// computer si scarica. Il foglio di condivisione non si usa sui computer anche se il browser lo
// offre: su Windows apre una finestra di sistema che a molti sembra "non succede niente", e in un
// browser senza interfaccia la promessa non si risolve mai e l'esportazione resta appesa. Se il
// foglio viene negato (Safari, se e' passato troppo tempo dal tocco) si ripiega sul download.
internal fun shareOrDownload(base64: String, fileName: String, title: String): Promise<JsAny?> = js(
    "(async function () { " +
        "var bin = atob(base64); var bytes = new Uint8Array(bin.length); for (var i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i); " +
        "var file = new File([bytes], fileName, { type: 'application/pdf' }); " +
        "var mobile = /Android|iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.maxTouchPoints > 1 && /Macintosh/.test(navigator.userAgent)); " +
        "try { if (mobile && navigator.canShare && navigator.canShare({ files: [file] })) { await navigator.share({ files: [file], title: title }); return null; } } " +
        "catch (e) { if (e && e.name === 'AbortError') return null; } " +
        "var url = URL.createObjectURL(file); var a = document.createElement('a'); a.href = url; a.download = fileName; " +
        "document.body.appendChild(a); a.click(); a.remove(); setTimeout(function () { URL.revokeObjectURL(url); }, 60000); return null; })()"
)
