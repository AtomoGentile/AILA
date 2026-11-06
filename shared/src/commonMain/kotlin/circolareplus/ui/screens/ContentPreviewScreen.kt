package circolareplus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import circolareplus.data.AppContainer
import circolareplus.data.remote.dto.ContentAttachmentDto
import circolareplus.design.*
import circolareplus.platform.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Opening a document previews it in memory; it does not archive or upload it. */
@Composable
fun ContentPreviewScreen(
    content: IncomingContent?, attachment: ContentAttachmentDto?, onBack: () -> Unit,
    onContextChange: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var file by remember(content, attachment?.id) { mutableStateOf(content) }
    var text by remember(content, attachment?.id) { mutableStateOf("") }
    var loading by remember(content, attachment?.id) { mutableStateOf(true) }
    var error by remember(content, attachment?.id) { mutableStateOf<String?>(null) }
    val name = content?.fileName ?: attachment?.fileName.orEmpty()
    val currentContext by rememberUpdatedState(onContextChange)
    LaunchedEffect(content, attachment?.id) {
        currentContext("")
        try {
            val archive = AppContainer.localContents
            val saved = content?.localId?.let { archive.entry(it) } ?: attachment?.let { archive.findByRemote(it.id) }
            if(file == null) file = saved?.let { archive.read(it) } ?: attachment?.let {
                check(!it.isExpired()) { "Il documento non è più disponibile sul server." }
                IncomingContent(it.fileName, it.mime, AppContainer.acaRepository.downloadFromServer(it.id))
            }
            val original = file ?: error("Documento non disponibile.")
            text = original.extractedText ?: attachment?.transcript ?: when(original.mime) {
                "text/plain" -> original.bytes.decodeToString()
                "application/pdf" -> AppContainer.pdfTextExtractor.extractText(original.bytes)
                else -> ""
            }
            currentContext("Documento: $name\n${text.take(100_000)}")
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { error = e.message ?: "Documento non disponibile." }
        finally { loading = false }
    }
    DisposableEffect(Unit) { onDispose { currentContext("") } }
    Column(Modifier.fillMaxSize()) {
        AilaBackBar("Documento", onBackClick=onBack)
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(name, style=MaterialTheme.typography.titleLarge, color=AppTheme.TextDark)
            if(loading) CircularProgressIndicator()
            error?.let { Text(it,color=AppTheme.TintRedInk) }
            file?.let { original ->
                OutlinedButton(onClick={scope.launch {
                    try { openContentFile(original.bytes,original.fileName,original.mime) }
                    catch(e: CancellationException) { throw e }
                    catch(e: Exception) { error=e.message }
                }}) { Text("Apri l’originale") }
            }
            if(text.isNotBlank()) SelectionContainer {
                Text(text,style=MaterialTheme.typography.bodyLarge,color=AppTheme.TextDark)
            }
        }
    }
}
