package circolareplus.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

private object NothingOverlayDepth { var value = 0 }

@Composable
fun NothingSheet(onDismiss: () -> Unit, closeRequested: Boolean = false, faded: Boolean = false,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val barVisibility = LocalFloatingTabBarVisibility.current
    DisposableEffect(Unit) {
        NothingOverlayDepth.value++
        barVisibility(false)
        onDispose { NothingOverlayDepth.value--; if(NothingOverlayDepth.value == 0) barVisibility(true) }
    }
    LaunchedEffect(Unit) { progress.animateTo(1f,tween(if(AppTheme.reduceMotion) 0 else 180,easing = FastOutSlowInEasing)) }
    fun close() {
        if(closing) return
        closing = true
        keyboard?.hide()
        focus.clearFocus(force = true)
        scope.launch { progress.animateTo(0f,tween(if(AppTheme.reduceMotion) 0 else 160)); onDismiss() }
    }
    LaunchedEffect(closeRequested) { if(closeRequested) close() }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value }.background(Color.Black.copy(alpha = .5f))
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { close() })
            val panel = RoundedCornerShape(28.dp)
            Column(Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 12.dp).padding(bottom = 12.dp)
                .widthIn(max = 680.dp).fillMaxWidth().heightIn(max = (maxHeight-48.dp).coerceAtLeast(100.dp))
                .graphicsLayer { alpha = progress.value * if(faded) .2f else 1f; translationY = if(AppTheme.reduceMotion) 0f else (1f-progress.value)*24.dp.toPx() }
                .clip(panel).background(AppTheme.SurfaceWhite).border(1.dp,AppTheme.Hairline,panel)) {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp),contentAlignment = Alignment.Center) {
                    NothingMenuRail()
                }
                val type = androidx.compose.material3.MaterialTheme.typography
                androidx.compose.material3.MaterialTheme(typography = type.copy(titleLarge = nothingDisplayStyle(22,30), headlineSmall = nothingDisplayStyle(24,32))) {
                    content { close() }
                }
            }
        }
    }
}
