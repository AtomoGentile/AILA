package circolareplus.design

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
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
 * Da questa larghezza (e con almeno [RailMinHeight] di altezza) la barra delle tab in basso
 * diventa una barra laterale a sinistra: tablet Android e iPad, in verticale e in orizzontale.
 */
val RailMinWidth = 600.dp

/** Altezza minima per la barra laterale: sotto (finestre basse, multi-finestra) resta in basso. */
val RailMinHeight = 480.dp

/**
 * Da questa larghezza le circolari si aprono accanto alla lista (due pannelli) e la bacheca va
 * su due colonne: iPad in orizzontale, iPad grandi e tablet Android larghi.
 */
val TwoPaneMinWidth = 840.dp

/** Larghezza massima del contenuto a due colonne (bacheca su schermi larghi). */
val MaxWideContentWidth = 1160.dp

/**
 * true quando la finestra e' larga da tablet (da [RailMinWidth]). Serve ai controlli nati per il
 * telefono (tab segmentate, filtri, scorciatoie): lì riempiono la riga e vanno bene, su un tablet
 * diventerebbero strisce lunghe mezzo schermo con le etichette perse in mezzo. Si decide sulla
 * larghezza della finestra e non su [LocalWideLayout], che scatta solo da [TwoPaneMinWidth].
 */
@androidx.compose.runtime.Composable
fun isTabletWidth(): Boolean {
    val widthPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
    val density = androidx.compose.ui.platform.LocalDensity.current
    return with(density) { widthPx.toDp() } >= RailMinWidth
}

/** Larghezza massima di un segmento delle tab segmentate su tablet (vedi [isTabletWidth]). */
val TabletSegmentMaxWidth = 200.dp

/** true quando la finestra e' larga abbastanza per due pannelli (vedi [TwoPaneMinWidth]). */
val LocalWideLayout = androidx.compose.runtime.compositionLocalOf { false }

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

/**
 * Spazio da lasciare in fondo alle schermate delle tab perche' l'ultimo elemento non finisca
 * sotto la barra flottante: il contenuto scorre SOTTO la pillola (in Glass si intravede
 * attraverso il vetro), ma a fine lista deve potersi fermare sopra. Lo fornisce MainAppShell;
 * fuori dalle tab vale 0.
 */
val LocalBottomBarPadding = androidx.compose.runtime.compositionLocalOf { 0.dp }

/**
 * Margine sotto la barra flottante dovuto alla barra di sistema. Su iOS l'inset del home indicator
 * (34dp sugli iPhone con Face ID) e' molto piu' alto dello spazio che serve davvero: la barra di
 * sistema di iOS sta molto piu' vicina al bordo, e con l'inset intero la pillola risultava
 * sollevata e sembrava troppo alta. Android resta com'era (barra gesti o a tre tasti).
 */
@androidx.compose.runtime.Composable
fun tabBarSystemInset(): Dp {
    val inset = WindowInsets.navigationBars
        .asPaddingValues().calculateBottomPadding()
    return if (circolareplus.platform.isIos()) (inset - 18.dp).coerceAtLeast(0.dp) else inset
}
