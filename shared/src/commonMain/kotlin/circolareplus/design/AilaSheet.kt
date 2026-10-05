package circolareplus.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
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
    /** Glass: dissolve tutto il foglio (vetro, maniglia, contenuto), per far posto a un'altra card sopra. */
    faded: Boolean = false,
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
    val fadeAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (faded) 0f else 1f,
        label = "sheetFade"
    )
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
                .graphicsLayer { alpha = fadeAlpha }
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
 * Foglio dall'alto (Material): un pannello agganciato al bordo superiore, sotto la barra di stato,
 * con lo scrim che si scurisce. L'altezza segue il contenuto (fino a quasi tutto lo schermo, sempre
 * sopra la tastiera).
 *
 * Con [origin] (il pulsante toccato, es. il "+" del calendario) il pannello nasce da li' come le
 * pagine di ricerca e notifiche nella Home: la forma cresce dal pulsante fino al rettangolo del
 * pannello e al ritorno ci rientra. Senza origine (aperto da un testo) scende dal bordo alto e
 * risale allo stesso modo.
 *
 * Il contenuto riceve `requestClose`: chiude con l'animazione e, a fine corsa, chiama [onClosed].
 * Indietro di sistema e tocco sullo scrim fanno lo stesso (un tocco, non uno sfioramento: niente
 * trascinamento); un contenuto con passi propri mette un suo gestore di "indietro" (composto dopo
 * questo, quindi ha la precedenza).
 */
@Composable
fun AilaTopSheet(
    origin: AilaTransformOrigin? = null,
    /**
     * Pannello agganciato al bordo BASSO (cresce verso l'alto, maniglia in cima, sopra la
     * tastiera): per i pulsanti che stanno in basso, come il "+" flottante della Bacheca. Da un
     * pulsante in basso un pannello che cresce verso il bordo alto non ha senso.
     */
    fromBottom: Boolean = false,
    onClosed: () -> Unit,
    content: @Composable ColumnScope.(requestClose: () -> Unit) -> Unit
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val closing = remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val bottomRadius = 28.dp
    val bottomRadiusPx = with(density) { bottomRadius.toPx() }
    val panelBounds = remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(Unit) {
        // Parte dopo i primi due fotogrammi: comporre il contenuto la prima volta e' pesante, e la
        // forma non deve saltare avanti. Da un pulsante la molla dei container transform; dal bordo
        // smorzamento critico (niente rimbalzo, che lascerebbe uno spazio vuoto sopra il pannello).
        if (origin != null) {
            // Dal pulsante: stesso motore del container transform delle pagine (parte appena la
            // pagina si calma, orologio a passo limitato, niente salti sul fotogramma lungo).
            awaitCalmFrames()
            progress.animateContainerOpen()
        } else {
            withFrameNanos { }
            withFrameNanos { }
            progress.animateTo(1f, spring(dampingRatio = 1f, stiffness = 500f))
        }
    }
    val requestClose: () -> Unit = {
        if (!closing.value) {
            closing.value = true
            scope.launch {
                if (origin != null) progress.animateContainerClose()
                else progress.animateTo(0f, tween(260, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                onClosed()
            }
        }
    }
    circolareplus.platform.PlatformBackHandler(enabled = true) { requestClose() }
    // Sempre sopra la tastiera: con lei aperta il pannello si accorcia invece di finirci sotto.
    // L'altezza della tastiera si legge in fase di layout (vedi il pannello piu' sotto): letta qui,
    // ogni fotogramma dell'animazione della tastiera ricomponeva tutto il contenuto del foglio.
    val imeInsets = WindowInsets.ime
    val statusInsets = WindowInsets.statusBars
    val navInsets = WindowInsets.navigationBars
    Box(modifier = Modifier.ailaNoTouchWhile(closing.value).fillMaxSize()) {
        // Scrim: tocco fuori = chiudi.
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
                            // Il rettangolo del pannello, esteso sopra lo schermo di un raggio: gli
                            // angoli alti arrotondati durante la corsa non devono vedersi.
                            targetRect = {
                                panelBounds.value?.let {
                                    if (fromBottom) Rect(it.left, it.top, it.right, it.bottom + bottomRadiusPx)
                                    else Rect(it.left, it.top - bottomRadiusPx, it.right, it.bottom)
                                }
                            },
                            targetRadiusPx = bottomRadiusPx
                        )
                        .graphicsLayer {}
                    else Modifier
                )
        ) {
            Column(
                modifier = Modifier
                    .align(if (fromBottom) Alignment.BottomCenter else Alignment.TopCenter)
                    .fillMaxWidth()
                    .layout { measurable, constraints ->
                        val maxSheetHeight = (constraints.maxHeight - imeInsets.getBottom(this) -
                            (if (fromBottom) statusInsets.getTop(this) else 0) - 24.dp.roundToPx())
                            .coerceAtLeast(240.dp.roundToPx())
                            .coerceAtMost(constraints.maxHeight)
                        val placeable = measurable.measure(
                            constraints.copy(maxHeight = maxSheetHeight, minHeight = minOf(constraints.minHeight, maxSheetHeight))
                        )
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    // Dal basso il pannello sale sopra la tastiera (le insets non ridimensionano la finestra).
                    .then(
                        if (fromBottom) Modifier.offset { IntOffset(0, -imeInsets.getBottom(this)) } else Modifier
                    )
                    .then(
                        if (origin == null) Modifier.graphicsLayer {
                            translationY = (if (fromBottom) 1f else -1f) * (1f - progress.value) * size.height
                        } else Modifier
                    )
                    .onGloballyPositioned { panelBounds.value = it.boundsInParent() }
                    .clip(
                        if (fromBottom) RoundedCornerShape(topStart = bottomRadius, topEnd = bottomRadius)
                        else RoundedCornerShape(bottomStart = bottomRadius, bottomEnd = bottomRadius)
                    )
                    .background(AppTheme.SurfaceWhite)
                    // Il pannello prende i tocchi: senza, quelli sulle zone vuote arriverebbero allo
                    // scrim sotto e lo chiuderebbero.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .then(
                        if (fromBottom) Modifier.layout { measurable, constraints ->
                            // Spazio della barra di navigazione, ma non quando c'e' la tastiera (che
                            // la copre gia'): letto in fase di layout, non ricompone.
                            val extra = (navInsets.getBottom(this) - imeInsets.getBottom(this)).coerceAtLeast(0)
                            val placeable = measurable.measure(constraints.copy(minHeight = 0))
                            layout(placeable.width, placeable.height + extra) { placeable.place(0, 0) }
                        } else Modifier.statusBarsPadding()
                    )
            ) {
                val handle: @Composable ColumnScope.(Dp, Dp) -> Unit = { top, bottom ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = top, bottom = bottom)
                            .size(width = 36.dp, height = 5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(AppTheme.TextFaint.copy(alpha = 0.5f))
                    )
                }
                // Dal bordo alto la maniglia sta sotto; dal basso in cima. Il contenuto scorre da solo
                // se non ci sta e la maniglia resta fuori dallo scorrimento.
                if (fromBottom) handle(10.dp, 4.dp)
                // Dal bordo alto il contenuto non deve toccare il margine dello schermo.
                else Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        // Dal fondo il pannello sale GIA' sopra la tastiera (offset di sopra): i moduli
                        // dentro mettono un loro `appImePadding`, che qui contava la tastiera una seconda
                        // volta e stirava il pannello con un vuoto bianco sopra la tastiera.
                        .then(if (fromBottom) Modifier.consumeWindowInsets(imeInsets) else Modifier)
                ) {
                    content(requestClose)
                }
                if (!fromBottom) handle(4.dp, 10.dp)
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
    // Colori espliciti per ogni elemento: su iOS (vetro) i giorni, gli anni e i pulsanti prendevano
    // il colore dal tema Material, che non segue il chiaro/scuro dell'app, e restavano bianchi su
    // vetro chiaro (o scuri su scuro): il calendario non si leggeva.
    val clear = androidx.compose.material3.DatePickerDefaults.colors(
        containerColor = if (glass) Color.Transparent else AppTheme.SurfaceWhite,
        titleContentColor = AppTheme.TextDark,
        headlineContentColor = AppTheme.TextDark,
        weekdayContentColor = AppTheme.TextMuted,
        subheadContentColor = AppTheme.TextDark,
        navigationContentColor = AppTheme.TextDark,
        yearContentColor = AppTheme.TextDark,
        disabledYearContentColor = AppTheme.TextFaint,
        currentYearContentColor = AppTheme.PrimaryBlue,
        selectedYearContentColor = Color.White,
        selectedYearContainerColor = AppTheme.PrimaryBlue,
        dayContentColor = AppTheme.TextDark,
        disabledDayContentColor = AppTheme.TextFaint,
        selectedDayContentColor = Color.White,
        selectedDayContainerColor = AppTheme.PrimaryBlue,
        todayContentColor = AppTheme.PrimaryBlue,
        todayDateBorderColor = AppTheme.PrimaryBlue
    )
    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        // Vetro vero: velo leggero (chiaro o scuro) sull'app sfocata, bordo di luce. Il foglio che
        // sta dietro si dissolve mentre e' aperto (vedi CreatePollDialog), quindi non serve un fondo pieno.
        modifier = if (glass) {
            Modifier.ailaGlassSurface(
                shape,
                tint = if (AppTheme.isDarkMode) Color.Black.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.42f)
            )
        } else Modifier,
        shape = shape,
        tonalElevation = if (glass) 0.dp else 6.dp,
        colors = clear,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                androidx.compose.material3.Text(confirmLabel, color = AppTheme.PrimaryBlue)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text("Annulla", color = AppTheme.TextMuted)
            }
        }
    ) {
        androidx.compose.material3.DatePicker(
            state = state,
            colors = clear
        )
    }
}

/**
 * Foglio dei moduli di creazione (nuova proposta, nuovo sondaggio, cronologia chat): Material = il
 * foglio nasce dal pulsante toccato (cerchio o "+") e ci rientra, come ricerca e notifiche nella
 * Home ([AilaTopSheet] con l'origine); Liquid Glass = il foglio dal basso di sempre.
 *
 * [originKey] lega l'origine al foglio: l'elemento che lo apre deve aver registrato il tocco
 * ([ailaTransformOrigin], `opensPage = true` sui pulsanti). Il contenuto riceve `close`, che chiude
 * con l'animazione e a fine corsa chiama [onDismiss]; [closeRequested] la chiede dall'esterno
 * (operazione finita).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AilaOriginSheet(
    originKey: Any,
    onDismiss: () -> Unit,
    closeRequested: Boolean = false,
    faded: Boolean = false,
    /** Il pulsante che lo apre sta in basso (FAB): il foglio si aggancia al fondo e cresce verso l'alto. */
    fromBottom: Boolean = false,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit
) {
    if (AppTheme.isGlass) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val currentOnDismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
        val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        // Una sola chiusura per volta: un doppio tocco sulla X o X + fine operazione insieme
        // lanciavano due hide() e due onDismiss.
        val closing = remember { booleanArrayOf(false) }
        val close: () -> Unit = {
            if (!closing[0]) {
                closing[0] = true
                scope.launch {
                    // Prima la tastiera: chiusa insieme al foglio, il contenuto la seguiva e saltava.
                    keyboard?.hide()
                    focusManager.clearFocus(force = true)
                    sheetState.hide()
                    currentOnDismiss()
                }
            }
        }
        LaunchedEffect(closeRequested) { if (closeRequested) close() }
        AilaBottomSheet(onDismissRequest = onDismiss, faded = faded, sheetState = sheetState) { content(close) }
    } else {
        val origin = remember(originKey) {
            AilaContainerTransform.assignFreshTo(originKey)
            AilaContainerTransform.originOf(originKey)
        }
        AilaTopSheet(origin = origin, fromBottom = fromBottom, onClosed = onDismiss) { requestClose ->
            LaunchedEffect(closeRequested) { if (closeRequested) requestClose() }
            androidx.compose.runtime.CompositionLocalProvider(LocalAilaSheetReveal provides false) {
                content(requestClose)
            }
        }
    }
}
