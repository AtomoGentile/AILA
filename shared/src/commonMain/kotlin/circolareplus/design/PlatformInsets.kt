package circolareplus.design

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier

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
