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
