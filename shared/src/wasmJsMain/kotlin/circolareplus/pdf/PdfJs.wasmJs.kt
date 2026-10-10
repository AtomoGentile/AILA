@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package circolareplus.pdf

import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.wasmMemory
import kotlin.wasm.unsafe.withScopedMemoryAllocator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.await
import kotlinx.coroutines.withContext

/**
 * Ponte tra Kotlin e pdf.js (https://mozilla.github.io/pdf.js/, licenza Apache-2.0), condiviso da
 * [renderPdfPages] e da `PdfTextExtractor`: sul browser non c'e' PDFKit ne' PdfRenderer, quindi
 * rendering e testo passano entrambi da qui.
 *
 * La libreria sta in `pdfjs/` accanto a index.html e si carica con un `import()` dinamico SOLO alla
 * prima richiesta di un PDF: chi non apre mai una circolare non scarica ne' compila il mezzo
 * megabyte di pdf.js. Con l'import dinamico non serve toccare index.html, e la CSP (`script-src
 * 'self'`, `worker-src 'self' blob:`) resta com'e': libreria e worker sono file della stessa origine.
 *
 * Il commento `webpackIgnore` e' indispensabile: webpack impacchetta il loader di Kotlin/Wasm e,
 * davanti a un `import(variabile)`, proverebbe a includere tutto (o a rompersi); cosi' lascia
 * l'`import()` nativo del browser.
 *
 * Niente cmaps: servono solo ai PDF con font CJK non incorporati, che nelle circolari scolastiche
 * non esistono (+1,5 MB di file mai usati). Gli standard_fonts (800 KB, scaricati solo se un PDF
 * usa Helvetica/Times/Arial senza incorporarli) e i `.wasm` (JPEG2000, JBIG2, profili colore ICC:
 * tipici delle scansioni) invece ci sono.
 */
internal object PdfJs {
    private const val BASE = "pdfjs/"

    // La Promise dell'import, non il modulo: due richieste ravvicinate (testo + pagine della stessa
    // circolare) condividono un solo caricamento. Se fallisce (rete assente, file mancante) si
    // azzera, cosi' il tentativo dopo riparte invece di restare rotto fino al riavvio.
    private var library: Promise<JsAny?>? = null

    private suspend fun library(): JsAny {
        val pending = library ?: importLibrary(assetUrl(BASE + "pdf.min.mjs"), assetUrl(BASE + "pdf.worker.min.mjs"))
            .also { library = it }
        try {
            return pending.await() ?: error("pdf.js non disponibile")
        } catch (e: Throwable) {
            if (library === pending) library = null
            throw e
        }
    }

    /**
     * Apre il PDF. `null` se non si puo' leggere (file vuoto, corrotto, protetto da password,
     * libreria non caricabile): come PDFKit e PdfRenderer sulle altre piattaforme, chi chiama
     * tratta il caso come "nessuna pagina / nessun testo" e ricade sul tasto "Apri esternamente"
     * o sulla classificazione dal solo titolo, senza mostrare errori.
     */
    suspend fun open(bytes: ByteArray): JsAny? {
        if (bytes.isEmpty()) return null
        var task: JsAny? = null
        try {
            val lib = library()
            val loadingTask = createLoadingTask(
                lib,
                bytes.toUint8Array(),
                assetUrl(BASE + "wasm/"),
                assetUrl(BASE + "standard_fonts/")
            )
            task = loadingTask
            return documentOf(loadingTask).await()
        } catch (e: CancellationException) {
            task?.let { destroyTask(it) }
            throw e
        } catch (e: Throwable) {
            // PasswordException, InvalidPDFException, MissingPDFException, rete caduta...
            task?.let { destroyTask(it) }
            warn("pdf.js: PDF non apribile (" + (e.message ?: "errore") + ")")
            return null
        }
    }

    /** Libera il worker e la memoria del documento; non fallisce mai, nemmeno se il worker e' morto. */
    suspend fun close(doc: JsAny) {
        withContext(NonCancellable) {
            try {
                destroyDocument(doc).await()
            } catch (e: Throwable) {
                // Gia' distrutto: non c'e' piu' nulla da liberare.
            }
        }
    }

    fun pageCount(doc: JsAny): Int = numPages(doc)

    /** Pagina 1-based, come in pdf.js. `null` se la pagina e' illeggibile. */
    suspend fun page(doc: JsAny, number: Int): JsAny? = try {
        pageOf(doc, number).await()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    /**
     * Disegna la pagina su un canvas largo [targetWidthPx] pixel e la restituisce come PNG.
     *
     * PNG e non pixel grezzi (come fa anche la versione iOS): una pagina 1240x1754 sono 8,7 MB di
     * RGBA, e Compose le tiene TUTTE in memoria finche' la circolare e' aperta; il PNG di una pagina
     * di testo pesa poche centinaia di KB e Skia lo decodifica solo quando serve. Su iPad, dove
     * Safari chiude le schede che superano il gigabyte, la differenza e' tra aprire una circolare di
     * 44 pagine e vederla ricaricarsi da sola. Il costo e' la codifica, pagata una volta sola dal
     * browser fuori dal thread principale.
     */
    suspend fun renderToPng(page: JsAny, targetWidthPx: Int): ByteArray? {
        val job = startRender(page, targetWidthPx)
        try {
            renderDone(job).await()
            val png = canvasToPng(job).await() ?: return null
            return png.toByteArray()
        } catch (e: CancellationException) {
            // L'utente ha lasciato la schermata a meta' pagina: si ferma anche il disegno.
            cancelRender(job)
            throw e
        } finally {
            releaseRender(job, page)
        }
    }

    /** Testo della pagina in ordine di lettura, vedi [pageTextOf]. */
    suspend fun pageText(page: JsAny): String = try {
        pageTextOf(page).await()?.toString() ?: ""
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        ""
    }
}

// ---------------------------------------------------------------------------------------------
// Copia dei byte tra Kotlin e JS.
//
// Kotlin/Wasm tiene i ByteArray in oggetti GC, non nella memoria lineare: da JS non si vedono.
// Copiarli un byte alla volta costa una chiamata JS per byte (un PDF da 3 MB = 3 milioni di
// chiamate). Si passa invece per la memoria lineare, a blocchi: Kotlin scrive/legge il blocco con
// Pointer (istruzioni wasm, nessuna chiamata JS) e JS sposta tutto il blocco con un solo set().
// ---------------------------------------------------------------------------------------------

private const val CHUNK = 64 * 1024

@OptIn(UnsafeWasmMemoryApi::class)
private fun ByteArray.toUint8Array(): JsAny {
    val source = this
    val target = newUint8Array(source.size)
    var offset = 0
    while (offset < source.size) {
        val length = minOf(CHUNK, source.size - offset)
        val start = offset
        withScopedMemoryAllocator { allocator ->
            val pointer = allocator.allocate(length)
            for (i in 0 until length) (pointer + i).storeByte(source[start + i])
            copyFromWasm(wasmMemory, target, start, pointer.address.toInt(), length)
        }
        offset += length
    }
    return target
}

@OptIn(UnsafeWasmMemoryApi::class)
private fun JsAny.toByteArray(): ByteArray {
    val source = this
    val size = byteLength(source)
    val result = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val length = minOf(CHUNK, size - offset)
        val start = offset
        withScopedMemoryAllocator { allocator ->
            val pointer = allocator.allocate(length)
            copyToWasm(wasmMemory, source, start, pointer.address.toInt(), length)
            for (i in 0 until length) result[start + i] = (pointer + i).loadByte()
        }
        offset += length
    }
    return result
}

// La memoria si passa da Kotlin (`wasmMemory`) invece di pescarla da `wasmExports.memory` nel
// loader: quell'accesso e' deprecato e stampa un errore in console.
private fun copyFromWasm(memory: JsAny, target: JsAny, targetOffset: Int, address: Int, length: Int) {
    js("target.set(new Uint8Array(memory.buffer, address, length), targetOffset)")
}

private fun copyToWasm(memory: JsAny, source: JsAny, sourceOffset: Int, address: Int, length: Int) {
    js("new Uint8Array(memory.buffer, address, length).set(source.subarray(sourceOffset, sourceOffset + length))")
}

private fun newUint8Array(size: Int): JsAny = js("new Uint8Array(size)")

private fun byteLength(array: JsAny): Int = js("array.length")

// ---------------------------------------------------------------------------------------------
// Chiamate a pdf.js
// ---------------------------------------------------------------------------------------------

// Relativo alla pagina, non alla radice del sito: la PWA puo' stare anche in una sottocartella.
private fun assetUrl(path: String): String = js("new URL(path, document.baseURI).href")

private fun warn(message: String) {
    js("console.warn(message)")
}

private fun importLibrary(libraryUrl: String, workerUrl: String): Promise<JsAny?> = js(
    "import(/* webpackIgnore: true */ libraryUrl).then(function (lib) { lib.GlobalWorkerOptions.workerSrc = workerUrl; return lib; })"
)

// verbosity 0: solo errori in console (i warning di pdf.js sui PDF di scuola sarebbero rumore).
// useWorkerFetch: font e wasm li scarica direttamente il worker, senza rimbalzare dal thread
// principale. Niente cMapUrl: vedi il commento di [PdfJs].
private fun createLoadingTask(lib: JsAny, data: JsAny, wasmUrl: String, fontsUrl: String): JsAny = js(
    "lib.getDocument({ data: data, wasmUrl: wasmUrl, standardFontDataUrl: fontsUrl, useWorkerFetch: true, isEvalSupported: false, verbosity: 0 })"
)

private fun documentOf(task: JsAny): Promise<JsAny?> = js("task.promise")

private fun destroyTask(task: JsAny) {
    js("try { task.destroy(); } catch (e) {}")
}

private fun destroyDocument(doc: JsAny): Promise<JsAny?> = js("doc.destroy()")

private fun numPages(doc: JsAny): Int = js("doc.numPages")

private fun pageOf(doc: JsAny, number: Int): Promise<JsAny?> = js("doc.getPage(number)")

// Un "lavoro" di rendering e' {canvas, task}: un solo oggetto JS per non far girare avanti e
// indietro tra i due mondi pezzi che servono sempre insieme.
private fun startRender(page: JsAny, targetWidthPx: Int): JsAny = js(
    """
    (function () {
        // La scala si ricava dalla larghezza voluta, non dai punti della pagina: ogni pagina
        // arriva larga uguale, qualunque sia il formato (A4, A3, orizzontale...). getViewport
        // gia' tiene conto di rotazione e CropBox, come PDFKit su iOS.
        var base = page.getViewport({ scale: 1 });
        var scale = Math.max(1, targetWidthPx) / base.width;
        var viewport = page.getViewport({ scale: scale });
        // Tetto di 16 megapixel: oltre, Safari su iOS rifiuta il canvas (resta vuoto) e una
        // pagina molto lunga o un A0 brucerebbe memoria per niente.
        var area = viewport.width * viewport.height;
        if (area > 16000000) {
            viewport = page.getViewport({ scale: scale * Math.sqrt(16000000 / area) });
        }
        var canvas = document.createElement('canvas');
        canvas.width = Math.max(1, Math.round(viewport.width));
        canvas.height = Math.max(1, Math.round(viewport.height));
        // Sfondo bianco esplicito: come su Android e iOS, le zone vuote non devono restare
        // trasparenti (nere sul tema scuro, dove il PNG verrebbe mostrato su fondo scuro).
        var task = page.render({ canvas: canvas, viewport: viewport, background: 'rgb(255,255,255)' });
        return { canvas: canvas, task: task };
    })()
    """
)

private fun renderDone(job: JsAny): Promise<JsAny?> = js("job.task.promise")

private fun cancelRender(job: JsAny) {
    js("try { job.task.cancel(); } catch (e) {}")
}

private fun canvasToPng(job: JsAny): Promise<JsAny?> = js(
    """
    new Promise(function (resolve, reject) {
        job.canvas.toBlob(function (blob) {
            if (!blob) { reject(new Error('canvas.toBlob ha restituito null')); return; }
            blob.arrayBuffer().then(function (buffer) { resolve(new Uint8Array(buffer)); }, reject);
        }, 'image/png');
    })
    """
)

// Azzerare le dimensioni del canvas libera subito il suo buffer di pixel (Safari lo tiene
// altrimenti fino al garbage collector, e 44 canvas da 9 MB sono 400 MB); page.cleanup() libera
// le risorse della pagina (immagini decodificate, font) senza chiudere il documento.
private fun releaseRender(job: JsAny, page: JsAny) {
    js("job.canvas.width = 0; job.canvas.height = 0; try { page.cleanup(); } catch (e) {}")
}

/**
 * Testo di una pagina in ordine di lettura.
 *
 * `getTextContent()` restituisce i frammenti nell'ordine in cui sono scritti nel file, non in
 * quello in cui si leggono: nelle tabelle fatte con Word la materia finiva da una parte e giorno,
 * ora e aula da un'altra. E' lo stesso problema per cui su Android PdfBox gira con
 * `sortByPosition = true`, e qui si ottiene lo stesso risultato: frammenti raggruppati in righe
 * per posizione verticale (nel sistema di coordinate della pagina GIA' ruotata, quindi anche un
 * PDF con /Rotate si legge dall'alto in basso), ordinati da sinistra a destra, uno spazio tra
 * frammenti staccati e un a-capo tra una riga e l'altra.
 */
private fun pageTextOf(page: JsAny): Promise<JsAny?> = js(
    """
    page.getTextContent().then(function (content) {
        var vt = page.getViewport({ scale: 1 }).transform;
        var items = [];
        content.items.forEach(function (it) {
            if (typeof it.str !== 'string' || it.str === '' || !it.transform) return;
            var t = it.transform;
            items.push({
                str: it.str,
                x: vt[0] * t[4] + vt[2] * t[5] + vt[4],
                y: vt[1] * t[4] + vt[3] * t[5] + vt[5],
                width: it.width || 0,
                size: Math.max(Math.hypot(t[2], t[3]), it.height || 0, 1)
            });
        });
        // Dall'alto in basso; a parita' di riga (stessa y entro mezzo corpo del carattere) da
        // sinistra a destra. Il confronto e' con la y del PRIMO frammento della riga, non con
        // l'ultimo, cosi' una riga con apici e pedici non si sfalda a catena.
        items.sort(function (a, b) { return a.y - b.y || a.x - b.x; });
        var lines = [];
        var line = null;
        items.forEach(function (item) {
            if (line && Math.abs(item.y - line.y) <= Math.max(2, line.size * 0.5)) {
                line.items.push(item);
                line.size = Math.max(line.size, item.size);
            } else {
                line = { y: item.y, size: item.size, items: [item] };
                lines.push(line);
            }
        });
        return lines.map(function (l) {
            l.items.sort(function (a, b) { return a.x - b.x; });
            var text = '';
            var end = null;
            l.items.forEach(function (item) {
                // Frammenti staccati ma senza uno spazio scritto in mezzo (Word posiziona le
                // parole una per una): senza questo "LUNEDI 13,30" diventerebbe "LUNEDI13,30".
                if (end !== null && item.x - end > item.size * 0.1
                        && !/\s/.test(text.charAt(text.length - 1)) && !/^\s/.test(item.str)) {
                    text += ' ';
                }
                text += item.str;
                end = item.x + item.width;
            });
            return text.trimEnd();
        }).filter(function (s) { return s.length > 0; }).join('\n');
    })
    """
)
