package com.circolareplus

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import circolareplus.ai.PdfBoxInit
import circolareplus.data.AppContainer
import circolareplus.platform.AndroidAppContext
import circolareplus.design.AilaTheme
import circolareplus.design.AppTheme
import circolareplus.ui.screens.MainAppShell

class MainActivity : ComponentActivity() {

    // Su Android 13+ (API 33) le notifiche richiedono un permesso runtime esplicito: senza
    // questa richiesta, le notifiche push arriverebbero al dispositivo ma non verrebbero mai
    // mostrate all'utente, anche con FCM e il manifest configurati correttamente.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* esito ignorato: se negato, l'app resta comunque utilizzabile senza notifiche */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Va chiamato prima di super.onCreate(): installSplashScreen() legge il tema
        // "Theme.App.Starting" impostato nel manifest per questa activity e lo scambia con
        // postSplashScreenTheme una volta che la prima frame è pronta.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Richiesto una tantum da PdfBox-Android prima di qualsiasi estrazione testo PDF
        // (classificazione AI delle circolari): carica le risorse font dagli assets del modulo.
        PdfBoxInit.init(this)

        // Serve all'AI locale: da qui il codice condiviso legge la RAM del telefono, sa dove
        // salvare i modelli scaricati e costruisce il motore di inferenza. Va prima di
        // AppContainer, che nel ripristino della sessione puo' gia' chiedere un classificatore.
        AndroidAppContext.init(this)

        // Tema scelto nelle Impostazioni, riletto prima della prima composizione: se si
        // aspettasse un LaunchedEffect, chi usa il tema scuro vedrebbe un lampo bianco a ogni
        // avvio dell'app.
        AppTheme.isDarkMode = AppContainer.settings.isDarkMode

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            // AilaTheme avvolge tutto (login e caricamento compresi): senza, i componenti
            // standard di Material — interruttori, slider, campi di testo, dialoghi — restavano
            // nel viola di default invece del blu AILA.
            AilaTheme {
                // Nessun utente/profilo fittizio: MainAppShell tenta da sé il ripristino
                // della sessione reale da un token salvato localmente, altrimenti mostra il login.
                MainAppShell()
            }
        }
    }
}
