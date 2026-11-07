# Workspace desktop PWA

Base: `v2.0.0-beta.23`. La distribuzione resta PWA: un wrapper Windows non risolverebbe
il layout Compose e richiederebbe integrazioni native, aggiornamenti e firma separati.
Questa modifica riguarda l'interfaccia; non aggiunge le funzionalità riservate alle app native.
Le nuove funzioni web restano nel percorso di rilascio condiviso con la PWA iOS.

Il workspace si attiva soltanto nel browser desktop (non Android, iPhone o iPad), con
almeno 1100 × 600 dp disponibili. La dimensione si aggiorna ridimensionando la finestra;
zoom del browser e densità del dispositivo continuano a essere rispettati.

- Sidebar da 224 dp, con Home per prima, ricerca, impostazioni e account. Le destinazioni
  e lo stato sono gli stessi delle tab esistenti.
- Contenuti centrati fino a 1440 dp; la lista circolari usa il 36% dello spazio disponibile,
  tra 360 e 460 dp, lasciando il resto al documento. Scorrimenti dei pannelli indipendenti.
- Home con intestazione distanziata dai bordi e accessi rapidi orizzontali.
- Calendario da 420 dp, eventi nella colonna restante e stato vuoto ridotto a una riga;
  controlli dei posti da 300 dp.
- Card da 16 dp di raggio con bordo tenue in Material e introduzioni di sezione orizzontali.
  Palette, tema scuro, scelta Glass/Material e preferenze sul movimento restano rispettati.

Sotto la soglia desktop restano rail e layout adattivi esistenti. I target nativi e le
PWA mobili non attivano questo ramo, anche con finestre grandi. Nessuna modifica a
backend, autorizzazioni, dati, versioni, workflow di rilascio o integrazioni native.

## Verifica visiva

Prima della distribuzione, controllare con un account studente e rappresentante:

- Home, Classe (circolari aperte/chiuse, ricerca, bacheca e forum), Calendario,
  Sondaggi (attivi/storico e classifica), Posti (preferenze e gestione).
- Finestra desktop 1920×1080, 1366×768, 1100×600 e ridimensionamento sotto 1100 o 600;
  ripetere con zoom al 125%, tema scuro e Glass.
- Navigazione da sidebar, ricerca, account e impostazioni; ritorno alla tab precedente.
- Android/iOS nativi e PWA mobile/tablet: layout precedente, senza sidebar desktop.

Non pubblicare release o distribuire la PWA senza approvazione del proprietario.

## Verifiche eseguite durante lo sviluppo

- `node --test webApp/tests/*.test.cjs`: 14 test superati.
- `:webApp:wasmJsBrowserDevelopmentExecutableDistribution`: build locale completata.
- Chromium/Playwright con API intercettate e dati fittizi: Home, Calendario, Classe,
  Sondaggi, Posti e apertura della ricerca; nessun errore JavaScript rilevato.
- Anteprime a 1440×900, 1100×600 e ridimensionamento a 1099×600; Home in Material
  chiaro/scuro, Glass e browser con user agent Android a 390×844 e iPad a 1440×900
  (esclusione del workspace desktop).

Le prove con risposte simulate verificano il layout e la navigazione; non sostituiscono
la verifica dei dati e delle operazioni con un account reale né il controllo su dispositivi
fisici iOS/Android. Nessuna richiesta di prova è stata inviata al backend reale.

## ACA e Brief sul desktop

La sidebar include **Apri ACA**, indipendente dal gesto di pressione prolungata.
Se AI o ACA sono disattivate apre le impostazioni AI: non attiva automaticamente servizi
né consensi. Il pannello contestuale arriva a 760 dp, con più spazio verticale per le
risposte e il compositore ancora accessibile. La chat completa resta quella esistente.

Il Brief usa tre schede affiancate sul desktop, con intestazioni e testo limitato per
lasciare visibili circolari ed eventi. Le azioni aprono le fonti esistenti; “Non ora” resta
escluso dalle circolari, che vengono riconosciute come lette tramite la lettura. I layout
mobili restano verticali e i criteri di visibilità del Brief non cambiano.

L'artifact `aila-pwa` include `AVVIA-ANTEPRIMA.cmd` e `LEGGIMI-ANTEPRIMA.txt` per la prova
locale Windows con Python 3. Non richiede release o deploy della PWA stabile.
