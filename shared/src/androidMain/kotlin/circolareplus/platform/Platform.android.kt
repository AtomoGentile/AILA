package circolareplus.platform

actual fun isIos(): Boolean = false

actual fun isAndroid(): Boolean = true

// Da Android 12 lo schermo dichiara i suoi angoli arrotondati (RoundedCorner); prima no: 0.
// Alla primissima composizione la finestra puo' non avere ancora gli inset: si rilegge dopo il
// primo fotogramma.
@androidx.compose.runtime.Composable
actual fun displayCornerRadius(): androidx.compose.ui.unit.Dp {
    val view = androidx.compose.ui.platform.LocalView.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    fun read(): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            view.rootWindowInsets
                ?.getRoundedCorner(android.view.RoundedCorner.POSITION_TOP_LEFT)
                ?.radius ?: 0
        } else 0
    val radiusPx = androidx.compose.runtime.remember(view) { androidx.compose.runtime.mutableIntStateOf(read()) }
    androidx.compose.runtime.LaunchedEffect(view) {
        if (radiusPx.intValue == 0) {
            androidx.compose.runtime.withFrameNanos { }
            radiusPx.intValue = read()
        }
    }
    return with(density) { radiusPx.intValue.toDp() }
}

actual fun appVersionName(): String {
    val context = AndroidAppContext.getOrNull() ?: return "?"
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}

// "Rimuovi animazioni" (Accessibilita') e "Scala durata animazioni: off" (Opzioni sviluppatore)
// scrivono tutti e due ANIMATOR_DURATION_SCALE = 0. Compose da solo non lo guarda per le molle e
// le animazioni infinite scritte a mano, quindi lo si legge qui. Un ContentObserver sulla stessa
// impostazione la tiene aggiornata anche se la si cambia con l'app aperta (dalle Impostazioni
// rapide o in multi-finestra), senza dipendere dal lifecycle che il modulo non usa.
@androidx.compose.runtime.Composable
actual fun isReduceMotionEnabled(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    fun read(): Boolean = try {
        android.provider.Settings.Global.getFloat(
            context.contentResolver,
            android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    } catch (e: Exception) {
        false
    }
    val reduce = androidx.compose.runtime.remember(context) { androidx.compose.runtime.mutableStateOf(read()) }
    androidx.compose.runtime.DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : android.database.ContentObserver(
            android.os.Handler(android.os.Looper.getMainLooper())
        ) {
            override fun onChange(selfChange: Boolean) {
                reduce.value = read()
            }
        }
        val registered = try {
            resolver.registerContentObserver(
                android.provider.Settings.Global.getUriFor(android.provider.Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer
            )
            true
        } catch (e: Exception) {
            false
        }
        onDispose { if (registered) resolver.unregisterContentObserver(observer) }
    }
    return reduce.value
}
