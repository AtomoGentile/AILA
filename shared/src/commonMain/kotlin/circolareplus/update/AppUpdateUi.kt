package circolareplus.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaProgressBar
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.AppTheme
import circolareplus.design.MaxDialogWidth
import circolareplus.design.ailaDialogMotion
import circolareplus.design.rememberAilaDialogCloser
import circolareplus.platform.AppForeground
import circolareplus.platform.openInstallPermissionSettings

/**
 * Da mettere una volta sola nella shell dell'app: avvia il controllo delle release all'apertura e
 * a ogni ritorno in primo piano, e mostra la finestra quando c'e' una versione nuova.
 */
@Composable
fun AppUpdateHost() {
    val foreground = AppForeground.isForeground
    LaunchedEffect(foreground) {
        if (foreground) AppUpdateManager.onForeground()
    }
    if (AppUpdateManager.dialogVisible) AppUpdateDialog()
}

@Composable
private fun AppUpdateDialog() {
    val state = AppUpdateManager.state
    val closer = rememberAilaDialogCloser()
    val dismiss = { closer.close { AppUpdateManager.dismissDialog(later = true) } }

    // Il contenuto cambia con lo stato (disponibile -> in download -> permesso -> installazione)
    // dentro la stessa finestra, senza chiuderla e riaprirla.
    val update: AppUpdate? = when (state) {
        is UpdateState.Available -> state.update
        is UpdateState.Downloading -> state.update
        is UpdateState.ReadyToInstall -> state.update
        is UpdateState.NeedsPermission -> state.update
        is UpdateState.Failed -> state.update
        else -> null
    }
    if (update == null) {
        // Stato senza una versione da mostrare (es. controllo rifatto a mano): niente finestra.
        LaunchedEffect(Unit) { AppUpdateManager.dismissDialog() }
        return
    }

    val title = when (state) {
        is UpdateState.Downloading -> "Scarico l'aggiornamento"
        is UpdateState.NeedsPermission -> "Serve un permesso"
        else -> "Aggiornamento disponibile"
    }

    AlertDialog(
        onDismissRequest = dismiss,
        modifier = Modifier
            .ailaDialogMotion(closer)
            .widthIn(max = MaxDialogWidth)
            .fillMaxWidth()
            .padding(horizontal = AppTheme.Space20),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = AppTheme.SurfaceWhite,
        shape = RoundedCornerShape(AppTheme.CardCornerRadius),
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge, color = AppTheme.TextDark) },
        text = {
            Column {
                when (state) {
                    is UpdateState.Downloading -> {
                        Text(
                            text = "Versione ${update.version}. Puoi continuare a usare l'app: " +
                                "quando è pronto si apre l'installazione.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.TextMuted
                        )
                        Spacer(Modifier.height(AppTheme.Space16))
                        val total = state.total.takeIf { it > 0 } ?: update.apkSize
                        AilaProgressBar(progress = if (total > 0) (state.downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f)
                        Spacer(Modifier.height(AppTheme.Space8))
                        Text(
                            text = "${megabytes(state.downloaded)} di ${megabytes(total)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppTheme.TextFaint
                        )
                    }
                    is UpdateState.NeedsPermission -> Text(
                        text = "Android vuole che tu consenta ad AILA di installare app. Apri le impostazioni, " +
                            "attiva «Consenti da questa fonte» e torna qui: l'installazione parte da sola.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AppTheme.TextMuted
                    )
                    else -> {
                        val size = if (update.apkSize > 0) " · ${megabytes(update.apkSize)}" else ""
                        Text(
                            text = "Versione ${update.version}$size. I tuoi dati restano dove sono. " +
                                "Meglio con il Wi-Fi.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AppTheme.TextMuted
                        )
                        if (state is UpdateState.Failed) {
                            Spacer(Modifier.height(AppTheme.Space8))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.PollDarkRed
                            )
                        }
                        val notes = AppUpdateParser.cleanNotes(update.notes)
                        if (notes.isNotEmpty()) {
                            Spacer(Modifier.height(AppTheme.Space12))
                            Text(
                                text = "Novità",
                                style = MaterialTheme.typography.titleSmall,
                                color = AppTheme.TextDark
                            )
                            Spacer(Modifier.height(4.dp))
                            Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.TextMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Downloading -> {}
                is UpdateState.NeedsPermission ->
                    AilaPrimaryButton(text = "Apri impostazioni", onClick = { openInstallPermissionSettings() }, large = true)
                is UpdateState.ReadyToInstall ->
                    AilaPrimaryButton(text = "Installa", onClick = { AppUpdateManager.startUpdate() }, large = true)
                is UpdateState.Failed ->
                    AilaPrimaryButton(text = "Riprova", onClick = { AppUpdateManager.startUpdate() }, large = true)
                else ->
                    AilaPrimaryButton(text = "Aggiorna", onClick = { AppUpdateManager.startUpdate() }, large = true)
            }
        },
        dismissButton = {
            when (state) {
                // Il download prosegue anche a finestra chiusa: lo stato resta nelle Impostazioni.
                is UpdateState.Downloading ->
                    AilaSecondaryButton(text = "Nascondi", onClick = { closer.close { AppUpdateManager.dismissDialog() } }, large = true)
                is UpdateState.NeedsPermission ->
                    AilaSecondaryButton(text = "Annulla", onClick = { closer.close { AppUpdateManager.dismissDialog() } }, large = true)
                else ->
                    AilaSecondaryButton(text = "Più tardi", onClick = dismiss, large = true)
            }
        }
    )
}

private fun megabytes(bytes: Long): String {
    val tenths = bytes * 10 / (1024 * 1024)
    return "${tenths / 10},${tenths % 10} MB"
}
