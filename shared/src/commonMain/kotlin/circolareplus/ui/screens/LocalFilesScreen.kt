package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import circolareplus.data.AppContainer
import circolareplus.data.repository.LocalContentEntry
import circolareplus.data.repository.LocalContentPreferences
import circolareplus.design.*
import circolareplus.platform.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LocalFilePreferencesControls(onOpenFiles: () -> Unit) {
    val repo = AppContainer.localContents
    val scope = rememberCoroutineScope()
    var preferences by remember { mutableStateOf(LocalContentPreferences()) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try { preferences = repo.preferences(); loaded = true }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("File sul dispositivo", style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
        Text("Le copie locali non scadono: restano fino alla cancellazione manuale, anche dopo la scadenza sul server. Svuotare la cache o uscire dall’account non le elimina.",
            style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
        Row(Modifier.fillMaxWidth().toggleable(value = preferences.automaticDownload, enabled = loaded && !saving,
            role = Role.Switch, onValueChange = { enabled ->
                saving = true
                scope.launch {
                    try { preferences = preferences.copy(automaticDownload = enabled); repo.savePreferences(preferences); error = null }
                    catch (e: CancellationException) { throw e }
                    catch (e: Exception) { error = "Scelta non sincronizzata sul dispositivo: ${e.message.orEmpty()}" }
                    finally { saving = false }
                }
            }), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Scarica automaticamente gli allegati", color = AppTheme.TextDark)
                Text("Salva su questo dispositivo gli allegati ricevuti nel forum durante la sincronizzazione. Può usare connessione e spazio. Se disattivato, i nuovi file si salvano quando li importi o apri; le copie già presenti restano.",
                    style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
            }
            Switch(preferences.automaticDownload, onCheckedChange = null, enabled = loaded && !saving)
        }
        OutlinedButton(onClick = onOpenFiles, modifier = Modifier.fillMaxWidth()) { Text("Gestisci i file sul dispositivo") }
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = AppTheme.TintRedInk, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun LocalFilesScreen(onBack: () -> Unit, onOpen: (IncomingContent) -> Unit) {
    val repo = AppContainer.localContents
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<LocalContentEntry>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LocalContentEntry?>(null) }
    var deletingAll by remember { mutableStateOf(false) }
    val revision = repo.revision
    LaunchedEffect(revision) {
        try { files = repo.list(); error = null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message }
    }
    fun work(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block(); error = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message }
            finally { busy = false }
        }
    }
    PlatformBackHandler { onBack() }
    Column(Modifier.fillMaxSize().background(AppTheme.BackgroundLight).windowInsetsPadding(WindowInsets.safeDrawing)) {
        AilaBackBar("File sul dispositivo", onBackClick = onBack)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("${files.size} file · ${files.sumOf { it.bytes } / 1024} KB", style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
                Text("Nessuna scadenza automatica. La cancellazione qui riguarda solo la copia di questo dispositivo, non quella sul server. Le copie restano separate per account.", color = AppTheme.TextMuted)
                Text("Disinstallare l’app o cancellare i dati del sito può rimuovere l’archivio. Nel browser la conservazione dipende anche dallo spazio disponibile e dalle impostazioni del sito.", style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                if (files.isNotEmpty()) TextButton(onClick = { deletingAll = true }, enabled = !busy) { Text("Elimina tutte le copie locali") }
            }
            if (files.isEmpty()) item { Text("I file che importi, apri o scarichi compariranno qui.", color = AppTheme.TextMuted) }
            items(files, key = { it.id }) { file ->
                AilaCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(file.fileName, style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
                        Text("${file.mime} · ${file.bytes / 1024} KB · Copia locale", style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { work { onOpen(repo.read(file)) } }, enabled = !busy) { Text("Apri") }
                            TextButton(onClick = { deleting = file }, enabled = !busy) { Text("Elimina dal dispositivo") }
                        }
                    }
                }
            }
            error?.let { item { Text(it, color = AppTheme.TintRedInk) } }
        }
    }
    if (deleting != null || deletingAll) AlertDialog(
        onDismissRequest = { deleting = null; deletingAll = false },
        title = { Text(if (deletingAll) "Eliminare tutte le copie locali?" else "Eliminare la copia locale?") },
        text = { Text("${deleting?.fileName.orEmpty()}\nLa copia sul server resta fino alla sua scadenza. Se è già scaduta, il file non potrà essere scaricato di nuovo. Il download automatico non ripristinerà questa copia.") },
        confirmButton = { TextButton(onClick = {
            val item = deleting; val all = deletingAll; deleting = null; deletingAll = false
            work { if (all) repo.removeAll() else if (item != null) repo.remove(item) }
        }) { Text("Elimina dal dispositivo") } },
        dismissButton = { TextButton(onClick = { deleting = null; deletingAll = false }) { Text("Annulla") } }
    )
}
