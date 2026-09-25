package com.circolareplus

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import circolareplus.ai.PdfBoxInit
import circolareplus.data.AppContainer
import circolareplus.platform.AndroidAppContext
import circolareplus.design.AilaTheme
import circolareplus.design.AppTheme
import circolareplus.domain.model.NotificationCategoryMapper
import circolareplus.ui.PendingDeepLink
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
        applySystemBarStyle(AppTheme.isDarkMode)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Avvio a freddo dal tocco su una notifica di sistema (vedi CircolareMessagingService):
        // l'extra viene letto qui e MainAppShell, non appena parte, ci naviga sopra da sé.
        applyPendingDeepLinkFrom(intent)

        // Telefoni solo in verticale; i tablet (lato corto >= 600dp) restano liberi di ruotare.
        // Fatto da codice e non con screenOrientation nel manifest, che bloccherebbe anche i tablet.
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        setContent {
            // Status bar e barra di navigazione seguono il tema scelto DENTRO l'app, come su iOS
            // (MainViewController.kt): prima restavano chiare anche col tema scuro. AppTheme.isDarkMode
            // e' stato di Compose, quindi l'effetto riparte a ogni cambio dalle Impostazioni.
            val isDark = AppTheme.isDarkMode
            LaunchedEffect(isDark) { applySystemBarStyle(isDark) }
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

    // MainActivity è "singleTask" (vedi AndroidManifest.xml): se l'app è già in background e
    // l'utente tocca una notifica di sistema, l'istanza esistente viene riportata in primo piano
    // tramite onNewIntent invece di passare di nuovo per onCreate — senza questo override, la
    // categoria della notifica andrebbe persa in quel caso.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyPendingDeepLinkFrom(intent)
    }

    // Tornando in primo piano si rilegge l'elenco: nel frattempo puo' essere arrivata una
    // circolare (e la notifica di sistema che l'annunciava non aggiorna nulla da sola).
    override fun onResume() {
        super.onResume()
        circolareplus.push.DataRefreshEvents.request()
    }

    /**
     * Finestra edge-to-edge (obbligatoria da targetSdk 35: il contenuto passa sotto le barre di
     * sistema e i margini li mette il codice condiviso, vedi design/PlatformInsets.kt) con barre
     * trasparenti e icone chiare o scure secondo il tema dell'app.
     */
    private fun applySystemBarStyle(isDark: Boolean) {
        val style = if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun applyPendingDeepLinkFrom(intent: Intent) {
        val explicit = intent.getStringExtra("notification_category")
        val category = if (!explicit.isNullOrBlank()) {
            explicit
        } else {
            // Notifica mostrata da FCM stesso (app in background: onMessageReceived non parte):
            // l'extra "notification_category" non c'e', ma i campi "data" del push sono extra
            // dell'intent e il mapper ricava da lì la stessa categoria.
            val extras = intent.extras
            @Suppress("DEPRECATION")
            val data = extras?.keySet()?.associateWith { extras.get(it)?.toString().orEmpty() }.orEmpty()
            NotificationCategoryMapper.categoryFrom(data)
        }
        if (category.isNotBlank()) {
            PendingDeepLink.category = category
        }
    }
}
