# Separazione degli aggiornamenti stabili e beta

## Android

Il canale dipende dalla versione installata. Un'installazione stabile non riceve beta;
per entrare nella beta serve installare volontariamente l'APK di una pre-release.
Una beta cerca sia nuove beta sia nuove stabili e propone solo una versione superiore.

| Installata | Candidata | Esito |
| --- | --- | --- |
| 1.6.2 | 1.6.3-beta.1 | Non proposta dal canale stabile |
| 1.6.3-beta.1 | 1.6.3-beta.2 | Aggiornamento beta |
| 1.6.3-beta.2 | 1.6.3-beta.10 | Aggiornamento beta |
| 1.6.3-beta.10 | 1.6.3 | Passaggio alla stabile |
| 1.6.3 | 1.6.3-beta.20 | Non proposta |
| 1.7.0-beta.1 | 1.6.3 | Nessun downgrade |

La selezione confronta il numero di versione, non la data di pubblicazione o l'ordine
restituito da GitHub. Esclude bozze, release senza APK completo, canali non supportati e
release con tag e flag `prerelease` incoerenti. Una beta marcata per errore stabile viene
quindi rifiutata anche se GitHub la restituisce come `latest`.

La cache dell'APK è associata a tag, URL, dimensione e digest. Il solo numero di byte non
basta a riutilizzare un file: una beta e una stabile della stessa dimensione sono distinte.
Il controllo è ripetuto prima dell'installazione, anche dopo la richiesta del permesso Android.

Le build senza tag vengono indicate come sviluppo (`1.6.3-dev`), non come una vecchia
stabile. Anche il canale appare nella riga degli aggiornamenti.

## iOS / SideStore

`source.json` distribuisce soltanto stabili. Il selettore verifica il tag canonico,
i flag `draft`/`prerelease` e il completamento dell'IPA; una beta non entra nel feed
nemmeno se viene marcata per errore come stabile. Dopo il download sono controllati
anche versione, canale e bundle identifier contenuti nell'IPA.

Le beta iOS restano distribuite manualmente come IPA nelle pre-release GitHub: non è
stato introdotto un feed beta automatico. Il flusso stabile non viene cambiato in beta.

La build IPA usa il tag per produrre metadati distinti:

- `CFBundleShortVersionString`: numero base compatibile con iOS, ad esempio `1.6.3`;
- `CFBundleVersion`: numero crescente del workflow IPA;
- `AILAReleaseVersion`: versione completa, ad esempio `1.6.3-beta.2`;
- `AILAUpdateChannel`: `beta` oppure `stable`.

Le Impostazioni leggono la versione completa, così una beta iOS non appare più come
una stabile. Le build locali senza tag indicano `development`.

## Rilascio e limiti

I workflow validano i tag (`vX.Y.Z` o `vX.Y.Z-beta.N`, N >= 1) prima di pubblicare gli
allegati e impostano esplicitamente il flag pre-release derivato dalla validazione.

Le correzioni riguardano le prossime build. La beta `v1.6.3-beta.1` già pubblicata contiene
il vecchio controllo aggiornamenti: per ricevere questa correzione occorre installare
manualmente una nuova build. Nessun allegato già pubblicato è stato sostituito.

La separazione qui descritta riguarda la distribuzione APK/IPA. PWA e Worker continuano
ad avere l'ambiente condiviso configurato nel progetto: il deploy della PWA da `main`
non è un ambiente beta separato. Isolare anche quei servizi richiede un ambiente dedicato.

## Verifica

I test Kotlin coprono selezione dei canali, ordinamento beta numerico, promozione alla
stabile, assenza di downgrade, tag/flag incoerenti, APK mancanti e identità della cache.
I test Python verificano validazione dei tag, esclusione delle beta dal feed stabile,
selezione delle IPA complete e generazione dei metadati iOS.
