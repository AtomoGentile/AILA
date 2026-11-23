package circolareplus.platform

import android.content.Context
import androidx.fragment.app.FragmentActivity
import java.lang.ref.WeakReference

/**
 * Il `Context` dell'applicazione e un riferimento debole all'Activity corrente (per `BiometricPrompt`),
 * messi a disposizione del codice condiviso.
 *
 * Il `Context` applicativo viene conservato per l'intero ciclo di vita del processo; l'Activity invece
 * e' tenuta solo con `WeakReference` e ripulita in `onDestroy()`, cosi' non crea memory leak alle
 * rotazioni o ricreazioni.
 */
object AndroidAppContext {
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var activityRef: WeakReference<FragmentActivity>? = null

    fun init(context: Context) {
        if (appContext == null) {
            synchronized(this) {
                if (appContext == null) appContext = context.applicationContext
            }
        }
    }

    fun setActivity(activity: FragmentActivity) {
        activityRef = WeakReference(activity)
    }

    fun clearActivity(activity: FragmentActivity) {
        if (activityRef?.get() === activity) {
            activityRef = null
        }
    }

    fun currentActivity(): FragmentActivity? = activityRef?.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    /** `null` finché [init] non è stata chiamata: chi lo usa deve gestire il caso. */
    fun getOrNull(): Context? = appContext

    fun require(): Context = appContext
        ?: error("AndroidAppContext.init() non è stata chiamata: va fatta in MainActivity.onCreate().")
}
