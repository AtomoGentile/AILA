# Note di design di AILA

> Letto per primo dalla skill generica `/impeccable` (`.claude/skills/impeccable/`): qui ci sono
> le parti specifiche di AILA, che hanno la precedenza sulle indicazioni generiche.

## Il design system da rispettare

**App (Compose Multiplatform)** — `shared/src/commonMain/kotlin/circolareplus/design/`
- `AppTheme.kt`: unica fonte di colori, raggi e spaziature. Due stili (`UiStyle`):
  **Liquid Glass** (predefinito su iOS) e **Material/Expressive** (predefinito su Android), più
  chiaro/scuro e l'accento scelto dall'utente (`AilaAccent`). I colori si leggono da `AppTheme`
  (`TextDark`, `TextMuted`, `TextFaint`, `SurfaceWhite`, `CardSurface`, `Tint*`, `PrimaryBlue`…)
  o da `MaterialTheme.colorScheme`, mai `Color(0x…)` scritto in una schermata.
- Spaziature: solo `AppTheme.Space4…Space48` (griglia da 4). Raggi: `CardCornerRadius`,
  `ButtonCornerRadius`, `SmallElementRadius`.
- Componenti: `AilaScreenHeader`, `AilaBackBar`, `AilaCard`, `AilaListRow`, `AilaIconTile`,
  `AilaPrimaryButton` / `AilaSecondaryButton` / `AilaDestructiveButton`, `AilaConfirmDialog`,
  `AilaSectionTitle`, `AilaEmptyState`, `AilaErrorState`, `AilaSwitch`, `AilaFab`,
  `AilaSegmentedTabs`, `AilaSlidingChipRow`, `AilaSheet`, `AilaProgressBar`,
  `AilaMorphingLoader`. Prima di scrivere un componente nuovo controlla che non esista già.
- Movimento: `AilaMotion.kt` (`ailaAppear`, `ailaPressable`, `ailaPushTransition`,
  `ailaTabTransition`, molle `ailaNavigationSpring`/`ailaSpatialSpring`). Niente `tween` e
  durate inventate nelle schermate.
- Insets: `PlatformInsets.kt` (`iosSafeDrawingPadding`, `iosImePadding`) su ogni schermata con
  campi di testo o a tutto schermo.

**PWA (Preact)** — `web/src/styles.css`, `web/src/ui/*.tsx`
- Token CSS in `:root` (`--bg`, `--surface`, `--text`, `--muted`, `--border`, `--accent`,
  `--accent-grad`, `--radius`) con la variante `prefers-color-scheme: dark`. Font Sora.
- Niente colori o raggi letterali nei componenti: si usano le variabili; se manca un token si
  aggiunge in `:root` **e** nel blocco dark.
- Nessuno stile inline che la CSP blocca (`style-src 'self'` in `web/public/_headers`): classi
  in `styles.css`.

Il brand è blu notte + blu/viola (`#3B82F6` → `#8B5CF6`) ed è voluto: gradienti e vetro sono
parte dell'identità, non "difetti da AI". Il problema è usarli dove non servono (testo su
gradiente illeggibile, vetro sopra vetro, tre gradienti nella stessa schermata).

## Procedura

### 1. Guarda prima di toccare
- Leggi per intero la schermata e i componenti che usa.
- Se puoi vederla, guardala: per la PWA `cd web && npm run dev` e screenshot con Playwright
  (Chromium è in `/opt/pw-browsers`) a 390×844 e 1280×800, chiaro e scuro
  (`colorScheme: 'dark'`). Per l'app Compose non c'è un emulatore: ragiona sul codice e dillo.

### 2. Critica, con questa lista
Segna ogni punto come ok / da correggere, con file e riga.

**Gerarchia e layout**
- Un solo elemento principale per schermata; titolo con `AilaScreenHeader` o `AilaBackBar`.
- Allineamenti su un'unica griglia; margini laterali uguali in tutta l'app; niente card dentro
  card dentro card.
- Liste lunghe: `LazyColumn` con `key`; su tablet/desktop (`AilaAdaptiveList`, `max-width` della
  PWA) il contenuto non si allunga a tutta larghezza.

**Tipografia**
- Si usano gli stili di `MaterialTheme.typography` / le regole `h1/h2/h3` della PWA, non
  `fontSize = 17.sp` sparsi. Massimo 3 dimensioni per schermata.
- Testi lunghi (circolari, risposte dell'assistente) con interlinea comoda e larghezza di riga
  leggibile; niente maiuscolo per frasi intere.

**Colore e contrasto**
- Contrasto WCAG AA: 4.5:1 per il testo normale, 3:1 per testo grande e icone. Controlla in
  modo particolare `TextFaint`, testo su `HeroGradient`, testo su vetro in modalità scura.
- Ogni colore funziona in chiaro, scuro, Glass, Material e con ogni `AilaAccent`.
- Il colore non è l'unico segnale: le categorie delle circolari (Ti riguarda / Potenziale
  interesse / Non ti riguarda) e i livelli dei sondaggi (verde/giallo/rosso chiaro/rosso scuro)
  hanno anche testo o icona.

**Stati**
- Ogni schermata che carica dati ha caricamento (`AilaMorphingLoader`/skeleton), vuoto
  (`AilaEmptyState` con un'azione utile), errore (`AilaErrorState` con "Riprova") e offline.
- Pulsanti: stato disabilitato e "in corso" (niente doppio invio di voti o proposte).
- Azioni distruttive o irreversibili (eliminare, chiudere un sondaggio, sbloccare l'anonimato)
  passano da `AilaConfirmDialog` con testo che dice cosa succede.

**Interazione e accessibilità**
- Aree di tocco almeno 48dp (Android) / 44pt (iOS) / 44px (PWA).
- `contentDescription` su icone che fanno qualcosa, `null` su quelle decorative; `aria-label`
  sui pulsanti-icona della PWA; focus visibile da tastiera nella PWA.
- Movimento: animazioni brevi e con uno scopo. La PWA oggi non rispetta
  `prefers-reduced-motion`: ogni animazione CSS nuova o toccata va disattivata lì dentro.
- Tastiera: `KeyboardOptions` giuste (tipo, azione "Avanti/Fatto"), il campo attivo non resta
  sotto la tastiera.

**Testi**
- Italiano semplice, tono da compagno di classe, frasi brevi. Pulsanti con verbi
  ("Invia proposta", non "OK"). Errori che dicono cosa fare, non codici.
- Stessa parola per la stessa cosa ovunque (Rappresentante, Guardia, bacheca, circolare).

**Coerenza tra piattaforme**
- La stessa funzione ha la stessa struttura e gli stessi testi nell'app e nella PWA
  (`web/src/ui/` rispetto a `shared/.../ui/screens/`), adattando solo i controlli nativi.

### 3. Correggi
- Parti dai problemi che l'utente vede di più: contrasto, stati mancanti, layout rotti,
  incoerenze evidenti. Poi i dettagli.
- Modifiche piccole e locali, nello stile del file. Se un valore manca nel design system,
  aggiungilo in `AppTheme.kt` / `styles.css` e usalo, invece di scriverlo a mano.
- Non cambiare comportamento, flussi o dati: se un problema di design richiede di cambiarli,
  proponilo invece di farlo.

### 4. Verifica
- PWA: `cd web && npm test && npm run typecheck`, poi di nuovo gli screenshot prima/dopo.
- App: `./gradlew :androidApp:assembleDebug` (e `./gradlew check`) se Gradle e l'SDK sono
  disponibili; altrimenti scrivi che non è stato compilato.
- Rileggi il diff cercando colori, dimensioni e durate scritti a mano.

## Risultato
Riassunto in chat: cosa hai cambiato (file:riga, prima → dopo), cosa resta da fare e cosa non
hai potuto verificare (ad esempio "nessuna prova a schermo su iOS"). Allega gli screenshot
prima/dopo della PWA quando li hai. Per lavori grandi aggiungi una sezione datata in `TODO.md`.
