# Privacy di AILA

AILA è un progetto scolastico non commerciale per la classe, senza pubblicità, statistiche pubblicitarie o profilazione. Titolare: Simone Bianchin. Per richieste chiedi di persona o usa la pagina del progetto: https://github.com/AtomoGentile/AILA.

## Dati e visibilità

L’app tratta nome, cognome, nome utente, classe e impronta della password; altezza a scaglioni e preferenze per la mappa posti; voti di sondaggi e proposte; eventi, messaggi e file che scegli di condividere; identificatori per le notifiche. Non raccoglie posizione o contatti. Il microfono viene attivato soltanto su richiesta, con il permesso del sistema.

Educazione tra pere è visibile alla classe. Puoi usare il nome reale o uno pseudonimo stabile nella singola discussione: OP per l’autore, Pera educata e un numero per gli altri partecipanti anonimi. Il server conserva il collegamento necessario per permessi, moderazione e notifiche; gli altri partecipanti non ricevono l’identità reale dietro lo pseudonimo. Messaggi e discussioni non scadono per inattività. Risoluzione e risposta in evidenza sono indipendenti; il promemoria dopo tre giorni non chiude la discussione.

## AI e ACA opzionali

In **Impostazioni → AI** puoi attivare o disattivare l’AI, ACA, i singoli servizi e i consensi; scegliere i provider e leggere l’informativa. AI e ACA sono inizialmente disattivate. Spegnerle impedisce nuove elaborazioni personali; non annulla eventi già confermati o messaggi pubblicati. Il servizio scolastico può continuare a riassumere circolari pubbliche condivise.

Ricevere o scegliere un file apre un’anteprima e non lo invia automaticamente a un modello. Le richieste usano esclusivamente la fonte scelta e il contesto AILA pertinente e autorizzato. Il pianificatore, la ricerca del forum e Perabot escludono valutazioni riservate e preferenze sociali; il bot pubblico esclude anche il calendario personale. La chat generale esclude dal cloud le valutazioni riservate. Trascrizioni e riassunti sono indicati come generati e possono essere errati; gli originali restano distinguibili.

Le proposte automatiche dalle conversazioni sono un servizio separato, inizialmente disattivato. Se un lettore lo attiva e consente il cloud, i messaggi umani visibili della discussione aperta possono essere analizzati per proporre impegni. Le proposte sono condivise con la classe e citano i messaggi di origine; chi ha ACA e Calendario attivi può confermare. La prima conferma aggiorna lo stato per tutti. Disattivare la propria AI non impedisce agli altri partecipanti autorizzati di riassumere o analizzare i messaggi già condivisi. Non sono usate chiavi personali di altri utenti.

Dal menu della chat puoi allegare file e vocali: prima scegli la fonte nell’anteprima. Il modello riceve il testo estratto, la trascrizione o la descrizione generata dell’immagine, con i servizi e i consensi richiesti. I byte originali restano nell’archivio locale; la cronologia conserva riferimenti ai file, senza duplicarli.

Ogni creazione, modifica o eliminazione nel calendario interno proposta da ACA richiede anteprima e conferma esplicita. Il modello non riceve credenziali del database o capacità di scrittura arbitrarie.

## Servizi e credenziali

Cloudflare ospita backend, database e allegati e può elaborare richieste con Workers AI. Firebase recapita le notifiche. Gemini può riassumere circolari pubbliche per il servizio scolastico. Le funzioni personali possono usare un motore locale realmente disponibile, il servizio incluso o un provider esterno configurato e autorizzato. La PWA non dichiara disponibili i modelli nativi Apple o Android.

Le API personali sono opzionali e separate dagli abbonamenti ChatGPT o Claude. Le chiavi non sono incluse nella distribuzione, condivise con la classe o registrate nei log. Android usa cifratura con chiave in Keystore, iOS Keychain, il browser l’archivio dell’origine. I task ACA non avviano un secondo provider dopo un errore. L’elaborazione locale non trasmette il contenuto a un servizio AI.

## Conservazione sul server

Gli allegati **fino a 3 MiB inclusi restano 90 giorni**; quelli **oltre 3 MiB restano 7 giorni**. Le bozze forum non pubblicate vengono ripulite dopo 24 ore. Alla scadenza l’accesso remoto viene bloccato e la pulizia elimina il file, il testo estratto e la trascrizione conservati sul server, verificando la rimozione; i messaggi restano. Una trascrizione in corso non ripristina dati già eliminati. La copia server può essere eliminata prima dall’autore o da un moderatore autorizzato.

Audio: massimo 10 minuti e 20 MiB. Formati, firme e limiti vengono controllati; questi controlli non costituiscono una scansione antivirus completa. Operazioni ACA e contatori tecnici hanno conservazione fino a 30 giorni. Le discussioni restano disponibili nel tempo; moderazione e segnalazioni usano registrazioni limitate.

## Copie sul dispositivo

I file importati, aperti o scaricati vengono conservati in un **archivio locale separato per account**, insieme all’eventuale testo o trascrizione disponibile. **Non hanno scadenza automatica: restano finché li elimini manualmente.** La scadenza o la cancellazione della copia server non elimina le copie locali, comprese quelle già scaricate da altri partecipanti.

In Impostazioni → Dati offline → File sul dispositivo puoi decidere se scaricare automaticamente gli allegati ricevuti. L’opzione è inizialmente disattivata. Il download automatico riguarda solo allegati pubblicati e accessibili nella classe; non avvia modelli AI o upload. Se elimini una copia locale, la sincronizzazione automatica non la ripristina.

Da File sul dispositivo puoi eliminare una singola copia o tutte le copie del tuo account presenti su quel dispositivo. L’eliminazione locale non cancella la copia server. Uscire dall’account o svuotare la cache temporanea non elimina l’archivio dei file. Disinstallare l’app o cancellare i dati del sito può rimuoverlo; nella PWA la conservazione dipende anche dalle impostazioni e dallo spazio disponibile nel browser. Eventuali copie esportate e backup del sistema sono gestiti dall’utente e dalla piattaforma.

Cronologia dell’assistente, notifiche e cache temporanea restano sul dispositivo e vengono cancellate con Esci. Le preferenze AI sono sincronizzate per account; una modifica offline resta locale fino alla sincronizzazione.

## Account e diritti

Dal Profilo puoi eliminare l’account: vengono rimossi profilo, preferenze e identificatori delle notifiche; gli interventi condivisi restano senza collegamento all’account. Le copie già salvate sui dispositivi restano soggette alla cancellazione locale descritta sopra.

Puoi chiedere accesso, correzione, cancellazione, limitazione e portabilità e opporti al trattamento (artt. 15–22 GDPR). Puoi rivolgerti al Garante per la protezione dei dati personali: https://www.garanteprivacy.it. AILA è rivolta agli studenti delle superiori e non è destinata ai minori di 14 anni.

Il testo sintetico nell’app è mantenuto in `shared/src/commonMain/kotlin/circolareplus/PrivacyPolicy.kt`.
