package circolareplus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import circolareplus.data.AppContainer
import circolareplus.data.remote.dto.ClassSearchHitDto
import circolareplus.design.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

@Composable
internal fun ClassSearchPanel(onOpen: (ClassSearchHitDto)->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var hits by remember { mutableStateOf<List<ClassSearchHitDto>>(emptyList()) }
    var completedQuery by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    val focus=remember { FocusRequester() }
    val keyboard=LocalSoftwareKeyboardController.current
    val trimmed=query.trim()
    val valid=trimmed.length>=2 || trimmed.length==1 && trimmed[0].isDigit()
    val kinds=listOf("ALL","CIRCULAR","BOARD","FORUM")
    val visible=if(completedQuery==trimmed) hits.filter { filter=="ALL" || it.kind==filter } else emptyList()
    DisposableEffect(Unit) {onDispose {keyboard?.hide()}}
    LaunchedEffect(Unit) { delay(if(AppTheme.reduceMotion) 0 else 180);focus.requestFocus() }
    LaunchedEffect(trimmed,retry) {
        error=null;loading=false
        if(!valid) {hits=emptyList();completedQuery="";return@LaunchedEffect}
        loading=true
        try {
            delay(300)
            hits=AppContainer.acaRepository.searchClass(trimmed)
            completedQuery=trimmed
        } catch(e: CancellationException) { throw e }
        catch(_: Exception) { error="Non riesco a cercare adesso. Controlla la connessione e riprova." }
        finally { loading=false }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=AppTheme.Space16)) {
        AilaPillTextField(
            value=query,onValueChange={query=it.take(120)},placeholder="Cerca in circolari, bacheca e Pere…",
            singleLine=true,modifier=Modifier.fillMaxWidth().focusRequester(focus),
            leadingIcon={AppIcons.Search(Modifier.size(20.dp),AppTheme.TextMuted)},
            trailingIcon={if(query.isNotEmpty()) AilaIconButton("Svuota la ricerca",{query=""},size=40.dp) { tint -> AppIcons.Close(Modifier.size(16.dp),tint) }}
        )
        Spacer(Modifier.height(12.dp))
        AilaFilterBar(labels=listOf("Tutto","Circolari","Bacheca","Pere"),selectedIndex=kinds.indexOf(filter),onSelect={filter=kinds[it]})
        Spacer(Modifier.height(12.dp))
        if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=AppTheme.Space24+LocalBottomBarPadding.current),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when {
                !valid -> item { Text("Cerca per numero o titolo di circolare, proposta o contenuto delle discussioni.",style=MaterialTheme.typography.bodyMedium,color=AppTheme.TextMuted) }
                error!=null -> item { Column {
                    Text(error.orEmpty(),color=AppTheme.TextMuted)
                    TextButton(onClick={retry++}) {Text("Riprova")}
                } }
                !loading && completedQuery==trimmed && visible.isEmpty() -> item {
                    Text("Nessun risultato per “$trimmed” in questa categoria.",color=AppTheme.TextMuted,style=MaterialTheme.typography.bodyMedium)
                }
            }
            itemsIndexed(visible,key={_,hit->"${hit.kind}:${hit.id}"}) {index,hit->
                AilaCard(modifier=Modifier.ailaAppear(index),onClick={keyboard?.hide();onOpen(hit)}) {
                    AilaListRow(
                        title=hit.title,subtitle=hit.text,
                        tint=when(hit.kind) {"CIRCULAR"->AppTheme.TintBlue;"BOARD"->AppTheme.TintViolet;else->AppTheme.TintGreen},
                        onClick={keyboard?.hide();onOpen(hit)},
                        icon={when(hit.kind) {
                            "CIRCULAR"->AppIcons.Document(Modifier.size(22.dp),AppTheme.TintBlueInk)
                            else->AppIcons.ChatBubble(Modifier.size(22.dp),if(hit.kind=="BOARD") AppTheme.TintVioletInk else AppTheme.TintGreenInk)
                        }}
                    )
                }
            }
        }
    }
}
