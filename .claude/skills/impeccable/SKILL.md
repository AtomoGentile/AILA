---
name: impeccable
description: Revisione e rifinitura del design di un'interfaccia qualsiasi (web, PWA, app mobile o desktop, in React/Vue/Svelte/HTML-CSS, Jetpack/Compose Multiplatform, SwiftUI, Flutter e simili) fino a un risultato curato, coerente e accessibile. Usala quando l'utente chiede di rendere una schermata o un sito "impeccabile", rifinire, sistemare la grafica, migliorare UI/UX, controllare coerenza, contrasto, dark mode, responsive, animazioni o stati vuoti/errore, oppure prima di un rilascio. Critica, corregge nel codice e verifica.
---

# Impeccable — rifinitura del design

Obiettivo: ogni schermata deve sembrare progettata con cura, dalla stessa mano, per persone vere.
Di norma non si ridisegna il prodotto e non si inventa uno stile nuovo: si porta ogni schermata
al livello del **design system che esiste già**, si tolgono incoerenze e difetti, si chiudono gli
stati dimenticati. Se un design system non c'è, lo si estrae da ciò che l'interfaccia fa già.

Argomenti facoltativi: una schermata, un componente o un percorso; `web` / `app`; `audit` (solo
critica, nessuna modifica); `nuovo` (si sta progettando da zero: vedi la sezione apposita).
Senza argomenti: le schermate toccate di recente (`git log --name-only -15`).

## 1. Contesto del progetto

Leggi, se esistono:
- `.claude/design.md`: note di design specifiche del progetto (brand, token, componenti, scelte
  volute). **Hanno la precedenza** su tutto quello che segue: ciò che lì è dichiarato voluto non
  è un difetto.
- `CLAUDE.md`, linee guida di design, Storybook, Figma citati nel README.

Poi trova il design system nel codice:
- **Token**: colori, tipografia, spaziature, raggi, ombre, durate. Web: variabili CSS in `:root`,
  `tailwind.config.*`, file `theme`/`tokens`. Compose: `MaterialTheme`, oggetti `*Theme`/`Tokens`.
  SwiftUI: estensioni di `Color`/`Font`, asset catalog. Flutter: `ThemeData`.
- **Componenti** condivisi (pulsanti, card, liste, dialoghi, campi, stati vuoti/errore/caricamento).
- **Movimento**: helper o costanti di animazione già usati.
- **Lingua e tono** dei testi esistenti.

Annota in 5-10 righe cosa hai trovato: è il metro con cui giudicare.

## 2. Guarda prima di toccare

- Leggi per intero la schermata e i componenti che usa.
- Se puoi vederla, guardala. Per il web: avvia il server di sviluppo del progetto e fai
  screenshot con Playwright a larghezza telefono (390×844) e desktop (1280×800), tema chiaro e
  scuro (`colorScheme: 'dark'`), e con `reducedMotion: 'reduce'`. Per app native senza emulatore,
  ragiona sul codice e dichiaralo nel risultato.
- Per i dettagli tecnici della piattaforma apri il riferimento giusto:
  [references/web.md](references/web.md) ·
  [references/native.md](references/native.md)

## 3. Critica

Usa questa lista; segna ogni punto come ok / da correggere, con file e riga.

**Gerarchia e layout**
- Si capisce in un secondo cos'è la schermata e qual è l'azione principale. Un solo elemento
  dominante; le azioni secondarie sembrano secondarie.
- Allineamenti su una griglia, spaziature dalla scala del progetto (tipicamente multipli di 4/8),
  margini laterali uguali in tutta l'app. Gruppi vicini = cose collegate.
- Niente card dentro card dentro card, niente bordi + ombre + sfondo tutti insieme senza motivo.
- Responsive: su schermi larghi il testo non si allunga a tutta larghezza; su schermi stretti
  niente scroll orizzontale, niente testo tagliato, niente elementi sovrapposti.

**Tipografia**
- Stili dalla scala tipografica del progetto, non dimensioni sparse. Massimo 3-4 livelli per
  schermata, distinti in modo netto.
- Righe di testo lungo tra ~45 e ~80 caratteri, interlinea comoda; niente intere frasi in
  maiuscolo; numeri tabellari (`tabular-nums`) dove si confrontano cifre.

**Colore e contrasto**
- Contrasto WCAG AA: 4.5:1 testo normale, 3:1 testo grande, icone e bordi dei controlli.
  Punti critici: testo secondario/disabilitato, testo su immagini, gradienti o superfici
  semitrasparenti, tema scuro.
- Colori solo dai token, e che funzionino in tutti i temi e varianti che il progetto supporta.
- Il colore non è l'unico segnale (stato, categoria, errore): serve anche testo o icona.
- Un accento usato per ciò che conta, non ovunque.

**Stati**
- Ogni vista con dati ha: caricamento (skeleton o indicatore, senza salti di layout), vuoto
  (che spiega e propone un'azione), errore (che dice cosa fare, con "Riprova"), offline se l'app
  lo supporta, contenuto molto lungo e molto corto.
- Pulsanti: disabilitato, in corso (niente doppio invio), successo visibile.
- Azioni distruttive o irreversibili chiedono conferma dicendo cosa succede; dove possibile,
  "Annulla" è meglio di una conferma.

**Interazione e accessibilità**
- Aree di tocco ≥ 44×44 pt / 48×48 dp; spazio tra target vicini.
- Ogni controllo ha un nome accessibile (label, `aria-label`, `contentDescription`,
  `accessibilityLabel`); le immagini decorative sono ignorate dagli screen reader.
- Navigazione da tastiera (web/desktop): ordine logico, focus sempre visibile, niente trappole.
- Il testo si ingrandisce (font scaling / zoom 200%) senza rompersi.
- Movimento: breve, con uno scopo (orientare, dare risposta a un'azione), coerente con gli
  helper del progetto; disattivato o ridotto con "riduci movimento".
- Moduli: etichette visibili (non solo placeholder), tipo di tastiera giusto, errori accanto al
  campo, il campo attivo non resta sotto la tastiera.

**Testi**
- Nella lingua e nel tono del prodotto, frasi brevi, parole di chi usa l'app e non del codice.
- Pulsanti con verbi che dicono cosa succede ("Salva modifiche", non "OK").
- La stessa cosa si chiama sempre allo stesso modo.

**Coerenza**
- La stessa funzione ha lo stesso aspetto e gli stessi testi in tutte le schermate e su tutte le
  piattaforme del progetto, adattando solo i controlli nativi.
- Componenti duplicati o quasi uguali vanno ricondotti a quello condiviso.

**Aspetto "generico"**: segnali di un'interfaccia fatta col pilota automatico, da correggere
*a meno che* siano scelte dichiarate del brand:
- gradiente viola-blu su tutto, glassmorphism ovunque, ombre enormi e sfocate;
- emoji al posto di icone, icone di set diversi mescolati;
- ogni sezione in una card arrotondata identica, griglie di "feature card" tutte uguali;
- testo grigio chiaro su bianco, tutto centrato, titoli enormi senza contenuto;
- frasi di riempimento ("Sblocca il tuo potenziale"), placeholder lorem ipsum rimasti.

## 4. Correggi

Salvo l'argomento `audit`:
- Parti da ciò che l'utente vede di più: contrasto e leggibilità, stati mancanti, layout rotti,
  incoerenze evidenti. Poi i dettagli.
- Modifiche piccole e locali, nello stile del file. Se un valore manca nel design system,
  aggiungilo lì (con le varianti di tema) e usalo, invece di scriverlo a mano nella schermata.
- Non cambiare comportamento, flussi, dati o API: se un problema di design li richiede,
  proponilo invece di farlo.

## 5. Verifica

- Fai girare test, typecheck, lint e build del progetto.
- Rifai gli screenshot (stessi formati di prima) e confrontali.
- Rileggi il diff cercando colori, dimensioni e durate scritti a mano e stili duplicati.

## Progettare da zero (`nuovo`)

Se non c'è niente da cui partire: prima di scrivere codice scegli una direzione precisa (tono,
pubblico, un riferimento visivo), poi fissa i token (palette con contrasti verificati in chiaro e
scuro, scala tipografica, scala di spaziature, raggi, durate) e pochi componenti base. Evita
l'aspetto generico descritto sopra; una scelta tipografica e cromatica con carattere vale più di
tanti effetti. Poi applica le sezioni 3-5.

## Risultato

Riassunto in chat: cosa hai cambiato (file:riga, prima → dopo), cosa resta da fare in ordine di
impatto, cosa non hai potuto verificare. Allega gli screenshot prima/dopo quando li hai.
Se il progetto tiene un diario dei lavori, aggiungi una sezione datata.
