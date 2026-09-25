package circolareplus.design

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Margini di sicurezza (status bar, notch, Dynamic Island, barra di navigazione/home, tastiera).
 *
 * Su entrambe le piattaforme l'app disegna a tutto schermo: su iOS perche' iosApp/iOSApp.swift
 * usa `.ignoresSafeArea`, su Android perche' da targetSdk 35 la finestra e' sempre edge-to-edge
 * (MainActivity chiama `enableEdgeToEdge`). Fuori dallo Scaffold nessuno tiene il contenuto
 * lontano dalle aree di sistema, e senza questi margini la tastiera copriva i campi di testo.
 *
 * Prima valevano solo su iOS: Android non era edge-to-edge e ci pensava il sistema.
 */
fun Modifier.appSafeDrawingPadding(): Modifier = safeDrawingPadding()

/**
 * Solo la tastiera (senza status bar/home indicator), per schermate già dentro uno Scaffold o un
 * bottom sheet. Se un genitore ha gia' applicato lo spazio della tastiera (i bottom sheet di
 * Material lo fanno) quel margine risulta gia' consumato e qui non si aggiunge nulla.
 */
fun Modifier.appImePadding(): Modifier = imePadding()

/** Larghezza massima del contenuto delle schermate principali su tablet e iPad. */
val MaxContentWidth = 840.dp

/** Larghezza massima di moduli e schermate "a colonna" (accesso, onboarding, offline). */
val MaxFormWidth = 520.dp

/** Larghezza massima dei dialoghi che altrimenti occupano tutta la larghezza dello schermo. */
val MaxDialogWidth = 560.dp

/**
 * Colonna centrata larga al massimo [max]: sui telefoni non cambia nulla (sono piu' stretti), su
 * tablet e iPad evita card e righe lunghe quanto lo schermo, difficili da leggere. Lo sfondo va
 * applicato PRIMA di questo modificatore, cosi' resta a tutta larghezza.
 */
fun Modifier.appContentWidth(max: Dp = MaxContentWidth): Modifier =
    fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = max)
        .fillMaxWidth()
