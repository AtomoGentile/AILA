package circolareplus.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Il menu che sale dal basso, uguale in tutta l'app.
 *
 * Liquid Glass: come i fogli di iOS 26, staccato dai bordi dello schermo, con gli angoli che
 * seguono la curva del telefono, di vetro scuro (o chiaro) traslucido col filo di luce sul bordo
 * e la maniglia a capsula. Prima era un pannello pieno e opaco, l'unico pezzo dell'app che non
 * sembrava vetro.
 * Material Expressive: il foglio pieno di Material 3, a tutta larghezza.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AilaBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit
) {
    if (!AppTheme.isGlass) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = AppTheme.SurfaceWhite,
            // Niente chiusura trascinando: uno sfioramento del dito, o un gesto storto mentre si
            // scorre il contenuto, chiudeva il foglio con quello che si stava scrivendo. Si chiude
            // con la X, con indietro o toccando fuori.
            sheetGesturesEnabled = false,
            content = content
        )
        return
    }
    // Mentre il foglio e' aperto l'app dietro si sfoca (vedi AilaSheetBackdrop): e' questo, non
    // il fondo del foglio, a tenere leggibile il testo con il vetro trasparente come il resto.
    androidx.compose.runtime.DisposableEffect(Unit) {
        AilaSheetBackdrop.openSheets++
        onDispose { AilaSheetBackdrop.openSheets-- }
    }
    val inset = 8.dp
    // Angoli concentrici a quelli dello schermo (il foglio sta a [inset] dal bordo).
    val radius = maxOf(circolareplus.platform.displayCornerRadius() - inset, 28.dp)
    val shape = RoundedCornerShape(radius)
    // Il foglio di Material resta trasparente e senza maniglia: il vetro e la maniglia sono
    // disegnati dentro il contenuto, che si muove sempre insieme al foglio quando lo si trascina.
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = androidx.compose.ui.graphics.RectangleShape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        sheetGesturesEnabled = false,
        // Velo leggero: la sfocatura fa gia' il grosso, il velo stacca un po' il foglio.
        scrimColor = if (AppTheme.isDarkMode) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.2f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = inset, end = inset, bottom = inset)
                // Stessa trasparenza del resto del vetro (richiesta di Simone).
                .ailaGlassSurface(shape),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Maniglia a capsula, come iOS.
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 14.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(AppTheme.TextFaint)
            )
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

/**
 * Quanti menu dal basso sono aperti in questo momento. La shell (MainAppShell) sfoca l'app dietro
 * finche' ce n'e' almeno uno, come iOS dietro ai fogli: il foglio e' in una finestra sua, quindi
 * la sfocatura non puo' farla lui.
 */
object AilaSheetBackdrop {
    var openSheets by androidx.compose.runtime.mutableIntStateOf(0)
}

/**
 * Foglio dal basso di Material che nasce dal pulsante toccato e ci si richiude (container
 * transform), al posto del foglio di sistema che sale e scende da solo. Il livello e' a schermo
 * intero e il foglio sta in fondo: la forma cresce dal pulsante fino al rettangolo del foglio (mai
 * a tutto schermo), con lo scrim che si scurisce. Senza un'origine (aperto da un testo, non da un
 * pulsante) sale dal basso e scende allo stesso modo.
 *
 * Il contenuto riceve `requestClose`: chiude con l'animazione e, a fine corsa, chiama [onClosed].
 * Indietro di sistema e tocco sullo scrim fanno lo stesso; un contenuto con passi propri mette un
 * suo gestore di "indietro" (composto dopo questo, quindi ha la precedenza).
 */
@Composable
fun AilaContainerSheet(
    origin: AilaTransformOrigin?,
    onClosed: () -> Unit,
    content: @Composable ColumnScope.(requestClose: () -> Unit) -> Unit
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val closing = remember { mutableStateOf(false) }
    val topRadius = 28.dp
    val topRadiusPx = with(LocalDensity.current) { topRadius.toPx() }
    val sheetBounds = remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(Unit) {
        // Parte dopo i primi due fotogrammi: comporre il contenuto la prima volta e' pesante, e la
        // forma non deve saltare avanti.
        withFrameNanos { }
        withFrameNanos { }
        progress.animateTo(
            1f,
            if (origin != null) ailaContainerFloatSpring() else spring(dampingRatio = 0.9f, stiffness = 400f)
        )
    }
    val requestClose: () -> Unit = {
        if (!closing.value) {
            closing.value = true
            scope.launch {
                progress.animateTo(0f, if (origin != null) ailaContainerCloseSpec() else tween(240))
                onClosed()
            }
        }
    }
    circolareplus.platform.PlatformBackHandler(enabled = true) { requestClose() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxSheetHeight = maxHeight * 0.92f
        // Scrim: tocco fuori = chiudi (un tocco, non uno sfioramento: niente trascinamento).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.32f))
                .pointerInput(Unit) { detectTapGestures { requestClose() } }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (origin != null) Modifier
                        .ailaContainerReveal(
                            progress = { progress.value },
                            origin = origin,
                            containerColor = AppTheme.PrimaryBlue,
                            pageColor = AppTheme.SurfaceWhite,
                            closing = { closing.value },
                            // Il rettangolo del foglio, esteso sotto lo schermo di un raggio: gli
                            // angoli bassi arrotondati durante la corsa non devono vedersi.
                            targetRect = { sheetBounds.value?.let { Rect(it.left, it.top, it.right, it.bottom + topRadiusPx) } },
                            targetRadiusPx = topRadiusPx
                        )
                        .graphicsLayer {}
                    else Modifier
                )
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight)
                    .then(
                        if (origin == null) Modifier.graphicsLayer { translationY = (1f - progress.value) * size.height }
                        else Modifier
                    )
                    .onGloballyPositioned { sheetBounds.value = it.boundsInParent() }
                    .clip(RoundedCornerShape(topStart = topRadius, topEnd = topRadius))
                    .background(AppTheme.SurfaceWhite)
                    // Il foglio prende i tocchi: senza, quelli sulle zone vuote arriverebbero allo
                    // scrim sotto e lo chiuderebbero.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .navigationBarsPadding()
            ) {
                content(requestClose)
            }
        }
    }
}

/**
 * Selettore data uguale in tutta l'app. Liquid Glass: la card non e' piu' il riquadro lilla
 * pieno di Material ma vetro traslucido col filo di luce sul bordo, e l'app dietro si sfoca
 * come sotto i fogli (vedi AilaSheetBackdrop). Negli altri stili resta il dialogo di Material.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AilaDatePickerDialog(
    state: androidx.compose.material3.DatePickerState,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit
) {
    val glass = AppTheme.isGlass
    if (glass) {
        androidx.compose.runtime.DisposableEffect(Unit) {
            AilaSheetBackdrop.openSheets++
            onDispose { AilaSheetBackdrop.openSheets-- }
        }
    }
    val shape = RoundedCornerShape(if (glass) 32.dp else 28.dp)
    val clear = androidx.compose.material3.DatePickerDefaults.colors(containerColor = Color.Transparent)
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = if (glass) Modifier.ailaGlassSurface(shape) else Modifier,
        shape = shape,
        tonalElevation = if (glass) 0.dp else 6.dp,
        colors = if (glass) clear else androidx.compose.material3.DatePickerDefaults.colors(),
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                androidx.compose.material3.Text(confirmLabel)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("Annulla")
            }
        }
    ) {
        androidx.compose.material3.DatePicker(
            state = state,
            colors = if (glass) clear else androidx.compose.material3.DatePickerDefaults.colors()
        )
    }
}
