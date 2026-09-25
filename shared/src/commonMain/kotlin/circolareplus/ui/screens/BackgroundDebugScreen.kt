package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaBackBar
import circolareplus.design.AilaCard
import circolareplus.design.AilaPrimaryButton
import circolareplus.design.AilaSecondaryButton
import circolareplus.design.AppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Diagnostica del refresh in background iOS: log dei risvegli ("bg_log") e simulazione.
 * Su Android mostra solo che non si applica.
 */
@Composable
fun BackgroundDebugScreen(
    isSupported: Boolean,
    readLog: () -> String,
    onClearLog: () -> Unit,
    onSimulate: suspend () -> String,
    /** Segnalibro locale del refresh (bg_last_circular_number). */
    readBookmark: () -> Int = { 0 },
    /** Riporta il segnalibro a (ultima circolare - 1), solo sul telefono. */
    onRewindBookmark: suspend () -> String = { "" },
    onBackClick: () -> Unit
) {
    // Il log sta in UserDefaults, non è stato di Compose: si rilegge a ogni revisione.
    var revision by remember { mutableStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val log = remember(revision) { readLog() }
    val bookmark = remember(revision) { readBookmark() }

    // Esegue un'azione lunga mostrando l'esito e rileggendo log e segnalibro.
    fun runAction(action: suspend () -> String) {
        isRunning = true
        scope.launch {
            lastResult = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Errore: ${e.message}"
            } finally {
                isRunning = false
                revision++
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(AppTheme.BackgroundLight)) {
        AilaBackBar(title = "Diagnostica background", onBackClick = onBackClick)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(AppTheme.Space16),
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)
        ) {
            if (!isSupported) {
                AilaCard {
                    Text(
                        text = "Non applicabile su Android: le circolari arrivano con le notifiche push.",
                        fontSize = 14.sp,
                        color = AppTheme.TextDark,
                        modifier = Modifier.padding(AppTheme.Space16)
                    )
                }
                return@Column
            }

            AilaPrimaryButton(
                text = if (isRunning) "In corso…" else "Simula risveglio",
                enabled = !isRunning,
                onClick = { runAction(onSimulate) },
                fillMaxWidth = true
            )
            // Per provare la notifica senza toccare il D1: al prossimo risveglio risulta 1 nuova.
            AilaSecondaryButton(
                text = "Retrocedi segnalibro",
                onClick = { if (!isRunning) runAction(onRewindBookmark) }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
                AilaSecondaryButton(
                    text = "Svuota log",
                    onClick = {
                        onClearLog()
                        revision++
                    }
                )
                AilaSecondaryButton(text = "Aggiorna", onClick = { revision++ })
            }

            Text(
                text = "Segnalibro attuale: ${if (bookmark == 0) "non ancora fissato" else "n. $bookmark"}",
                fontSize = 13.sp,
                color = AppTheme.TextMuted
            )

            lastResult?.let {
                Text(text = "Esito: $it", fontSize = 13.sp, color = AppTheme.TextMuted)
            }

            AilaCard {
                Column(modifier = Modifier.padding(AppTheme.Space16)) {
                    Text(
                        text = "bg_log (più recenti in fondo)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TextDark
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space8))
                    SelectionContainer {
                        Text(
                            text = log.ifBlank { "Nessun risveglio registrato." },
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AppTheme.TextMuted
                        )
                    }
                }
            }
        }
    }
}
