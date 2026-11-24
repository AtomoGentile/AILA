package circolareplus.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import circolareplus.data.remote.dto.ContentDeleteRequestDto
import circolareplus.design.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun DeleteRequestsPanel(
    requests: List<ContentDeleteRequestDto>,
    onApprove: suspend (String) -> Unit,
    onReject: suspend (String) -> Unit
) {
    if (requests.isEmpty()) return
    val scope = rememberCoroutineScope()
    var busyId by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    AilaCard(containerColor = AppTheme.TintAmber) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppTheme.Space16),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Richieste di eliminazione (${requests.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.TintAmberInk
            )
            Text(
                text = "Servono le firme di 2 Rappresentanti e 1 Guardia di Sicurezza.",
                style = MaterialTheme.typography.bodySmall,
                color = AppTheme.TextMuted
            )
            actionError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.TintRedInk)
            }
            requests.forEach { req ->
                val isBusy = busyId == req.id
                AilaCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val scopeLabel = when (req.scope) {
                            "BOARD_PROPOSAL" -> "Proposta in Bacheca"
                            "BOARD_COMMENT" -> "Commento in Bacheca"
                            "FORUM_THREAD" -> "Domanda in Pere"
                            else -> "Messaggio in Pere"
                        }
                        Text(
                            text = "$scopeLabel · chiesta da ${req.requestedByName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = AppTheme.PrimaryBlue
                        )
                        if (req.targetPreview.isNotBlank()) {
                            Text(
                                text = "«${req.targetPreview}»",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AppTheme.TextDark,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Motivo: ${req.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.TextMuted
                        )
                        Text(
                            text = "Firme: Rappresentanti ${req.repApprovals}/${req.repRequired} · Guardia ${req.guardApprovals}/${req.guardRequired}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppTheme.TextDark,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!req.myApproved) {
                                AilaActionButton(
                                    onClick = {
                                        busyId = req.id
                                        actionError = null
                                        scope.launch {
                                            try {
                                                onApprove(req.id)
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                actionError = e.message ?: "Impossibile firmare la richiesta."
                                            } finally {
                                                busyId = null
                                            }
                                        }
                                    },
                                    enabled = !isBusy
                                ) {
                                    Text(if (isBusy) "Firma…" else "Firma e approva")
                                }
                            } else {
                                Text(
                                    text = "Hai già firmato",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AppTheme.TintGreenInk,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            AilaTextButton(
                                onClick = {
                                    busyId = req.id
                                    actionError = null
                                    scope.launch {
                                        try {
                                            onReject(req.id)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            actionError = e.message ?: "Impossibile rifiutare la richiesta."
                                        } finally {
                                            busyId = null
                                        }
                                    }
                                },
                                enabled = !isBusy
                            ) { Text("Rifiuta") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RequestDeleteDialog(
    title: String,
    preview: String,
    onDismiss: () -> Unit,
    onSubmit: suspend (reason: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AilaAlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        containerColor = AppTheme.SurfaceWhite,
        shape = RoundedCornerShape(AppTheme.CardCornerRadius),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "L’eliminazione avverrà solo con la firma di 2 Rappresentanti e 1 Guardia di Sicurezza. La tua firma sarà aggiunta automaticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.TextMuted
                )
                if (preview.isNotBlank()) {
                    Text(
                        text = "«${preview.take(120)}»",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.TextDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500); error = null },
                    label = { Text("Motivo (min. 5 caratteri)") },
                    maxLines = 4,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.TintRedInk)
                }
            }
        },
        confirmButton = {
            AilaTextButton(
                enabled = !submitting && reason.trim().length >= 5,
                onClick = {
                    submitting = true
                    error = null
                    scope.launch {
                        try {
                            onSubmit(reason.trim())
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "Impossibile inviare la richiesta."
                        } finally {
                            submitting = false
                        }
                    }
                }
            ) {
                Text(if (submitting) "Invio…" else "Apri richiesta")
            }
        },
        dismissButton = {
            AilaTextButton(enabled = !submitting, onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@Composable
internal fun ReportContentDialog(
    title: String = "Segnala un contenuto",
    onDismiss: () -> Unit,
    onSubmit: suspend (reason: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AilaAlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        containerColor = AppTheme.SurfaceWhite,
        shape = RoundedCornerShape(AppTheme.CardCornerRadius),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "La segnalazione verrà inviata ai Rappresentanti e alla Guardia di Sicurezza della classe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTheme.TextMuted
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(1000); error = null },
                    label = { Text("Motivo della segnalazione") },
                    maxLines = 5,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = AppTheme.TintRedInk)
                }
            }
        },
        confirmButton = {
            AilaTextButton(
                enabled = !submitting && reason.isNotBlank(),
                onClick = {
                    submitting = true
                    error = null
                    scope.launch {
                        try {
                            onSubmit(reason.trim())
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message ?: "Impossibile inviare la segnalazione."
                        } finally {
                            submitting = false
                        }
                    }
                }
            ) {
                Text(if (submitting) "Invio…" else "Invia segnalazione")
            }
        },
        dismissButton = {
            AilaTextButton(enabled = !submitting, onClick = onDismiss) { Text("Annulla") }
        }
    )
}
