package circolareplus.platform

import android.content.Context

/**
 * Il `Context` dell'applicazione, messo a disposizione del codice condiviso.
 *
 * Serve perché l'AI locale ha bisogno del Context per tre cose che il codice comune non può
 * ottenere da sé: leggere la RAM totale (`ActivityManager`), sapere dove scrivere i file dei
 * modelli (`filesDir`) e costruire il motore di inferenza. Il modulo `:shared` non ha un
 * `ContentProvider` di inizializzazione, quindi si segue la stessa strada già usata da
 * `PdfBoxInit`: `MainActivity` lo riempie all'avvio.
 *
 * Si tiene l'`applicationContext` e mai l'Activity: legare un Context di Activity a un oggetto
 * statico lo terrebbe in vita a ogni rotazione dello schermo.
 */
object AndroidAppContext {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) appContext = context.applicationContext
            }
        }
    }

    /** `null` finché [init] non è stata chiamata: chi lo usa deve gestire il caso. */
    fun getOrNull(): Context? = appContext

    fun require(): Context = appContext
        ?: error("AndroidAppContext.init() non è stata chiamata: va fatta in MainActivity.onCreate().")
}
