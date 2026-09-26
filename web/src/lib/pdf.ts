// pdf.js caricato solo quando serve (dettaglio circolare): rendering su canvas e testo per l'AI.
import type { PDFDocumentProxy } from 'pdfjs-dist';
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url';

let lib: Promise<typeof import('pdfjs-dist')> | null = null;

function pdfjs() {
  if (!lib) {
    lib = import('pdfjs-dist').then((m) => {
      m.GlobalWorkerOptions.workerSrc = workerUrl;
      return m;
    });
  }
  return lib;
}

/** Scarica (passando dalla cache del service worker) e apre un PDF. */
export async function openPdf(url: string): Promise<PDFDocumentProxy> {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`PDF non disponibile (${res.status})`);
  const data = new Uint8Array(await res.arrayBuffer());
  const m = await pdfjs();
  return m.getDocument({ data }).promise;
}

/** Testo di tutte le pagine, una pagina per paragrafo. */
export async function extractText(doc: PDFDocumentProxy): Promise<string> {
  const pages: string[] = [];
  for (let i = 1; i <= doc.numPages; i++) {
    const page = await doc.getPage(i);
    const content = await page.getTextContent();
    pages.push(
      content.items
        .map((it) => ('str' in it ? it.str + (it.hasEOL ? '\n' : ' ') : ''))
        .join('')
    );
  }
  return pages.join('\n\n');
}

/** Disegna una pagina larga `cssWidth` pixel CSS, nitida sugli schermi retina. */
export async function renderPage(doc: PDFDocumentProxy, pageNumber: number, canvas: HTMLCanvasElement, cssWidth: number) {
  const page = await doc.getPage(pageNumber);
  const base = page.getViewport({ scale: 1 });
  const ratio = Math.min(window.devicePixelRatio || 1, 3);
  const viewport = page.getViewport({ scale: (cssWidth / base.width) * ratio });
  canvas.width = Math.floor(viewport.width);
  canvas.height = Math.floor(viewport.height);
  canvas.style.width = `${cssWidth}px`;
  canvas.style.height = `${Math.floor(viewport.height / ratio)}px`;
  await page.render({ canvas, viewport }).promise;
}
