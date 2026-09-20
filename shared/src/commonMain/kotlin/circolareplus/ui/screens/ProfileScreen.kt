package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AilaCard
import circolareplus.design.AilaListRow
import circolareplus.design.AilaScreenHeader
import circolareplus.design.AilaSectionTitle
import circolareplus.design.ailaAppear
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.domain.model.StudentProfile
import circolareplus.domain.model.User
import circolareplus.domain.model.UserRole
import kotlinx.coroutines.launch

/**
 * Scheda "Altro": profilo e impostazioni. Riscritta col linguaggio del mockup — card d'identità
 * a gradiente in cima e poi righe con riquadro icona raggruppate in sezioni, invece di quattro
 * riquadri bianchi tutti uguali e tutti con lo stesso peso visivo.
 *
 * Il contenuto scorre (`verticalScroll`): prima era una Column fissa, quindi il tasto "Esci
 * dall'Account" in fondo restava fuori schermo e irraggiungibile.
 */
@Composable
fun ProfileScreen(
    user: User = User(id = "user_1", firstName = "Nome", lastName = "Cognome", username = "username", role = UserRole.STUDENT),
    profile: StudentProfile = StudentProfile(userId = "user_1", heightCm = 170, className = "", academicYear = "", priorityPass = false, notificationBoardEnabled = true),
    userAiApiKey: String = "",
    onOpenSettings: () -> Unit = {},
    onManageClassRoster: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    /**
     * Elimina l'account dopo la conferma con password. Restituisce il messaggio d'errore da
     * mostrare nella finestra, oppure `null` se l'account e' stato eliminato.
     */
    onDeleteAccount: suspend (password: String) -> String? = { null }
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isRepresentative = user.role == UserRole.REPRESENTATIVE
    val subtitle = buildString {
        if (profile.className.isNotBlank()) {
            append("Classe: ${profile.className}")
            if (profile.academicYear.isNotBlank()) {
                append(" • ")
            }
        }
        if (profile.academicYear.isNotBlank()) {
            append("Anno Scolastico ${profile.academicYear}")
        }
    }.ifBlank { null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
    ) {
        AilaScreenHeader(
            title = "Il mio Profilo",
            subtitle = subtitle
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.Space16)
                .padding(top = AppTheme.Space16, bottom = AppTheme.Space32)
        ) {
            // --- Card identità -------------------------------------------------------------
            // Rifatta: era un riquadro bianco con le iniziali in un cerchietto e due righe
            // "etichetta: valore" allineate a destra, indistinguibile dalle card di impostazioni
            // sotto. Ora è il pannello a gradiente del brand, con l'avatar in evidenza e i due
            // dati come riquadri affiancati, che si leggono a colpo d'occhio.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppTheme.CardCornerRadius + 4.dp))
                    .background(AppTheme.HeroGradient)
                    .padding(AppTheme.Space20)
                    .ailaAppear(0)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(AppTheme.OnHeroSurface)
                            .border(2.dp, AppTheme.OnHeroBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${user.firstName.take(1)}${user.lastName.take(1)}".uppercase(),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.OnHeroPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(AppTheme.Space12))

                    Text(
                        text = "${user.firstName} ${user.lastName}",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.OnHeroPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "@${user.username}",
                        fontSize = 13.sp,
                        color = AppTheme.OnHeroSecondary,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(AppTheme.Space12))

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppTheme.OnHeroSurface)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRepresentative) {
                            AppIcons.Crown(modifier = Modifier.size(13.dp), color = AppTheme.OnHeroPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (isRepresentative) "Rappresentante di Classe" else "Studente",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.OnHeroPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(AppTheme.Space20))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)
                    ) {
                        HeroStatBox(
                            label = "Altezza",
                            value = "${profile.heightCm} cm",
                            modifier = Modifier.weight(1f)
                        )
                        HeroStatBox(
                            label = "Priority Pass",
                            value = if (profile.priorityPass) "Attivo" else "Non attivo",
                            highlighted = profile.priorityPass,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.Space24))

            // --- Sezione: gestione classe (solo Rappresentante) -----------------------------
            if (isRepresentative) {
                AilaSectionTitle(text = "Gestione classe")
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                AilaCard {
                    AilaListRow(
                        title = "Scheda Classe",
                        subtitle = "Valutazioni didattica/comportamento e Priority Pass",
                        tint = AppTheme.TintViolet,
                        onClick = onManageClassRoster,
                        icon = { AppIcons.Profile(modifier = Modifier.size(21.dp), color = AppTheme.TintVioletInk) }
                    )
                }
                Spacer(modifier = Modifier.height(AppTheme.Space24))
            }


            // --- Sezione: Impostazioni ------------------------------------------------------
            // La chiave AI stava qui, in mezzo al profilo, con la casella di testo sempre aperta.
            // Ora vive in Impostazioni insieme a tema, stile e filtri delle notifiche: il profilo
            // torna a essere la scheda della persona, non un pannello di configurazione.
            AilaSectionTitle(text = "Impostazioni")
            Spacer(modifier = Modifier.height(AppTheme.Space12))
            AilaCard {
                AilaListRow(
                    title = "Impostazioni",
                    subtitle = if (userAiApiKey.isBlank()) {
                        "Chiave AI non configurata, tema, notifiche"
                    } else {
                        "Chiave AI configurata, tema, notifiche"
                    },
                    tint = AppTheme.TintSlate,
                    onClick = onOpenSettings,
                    icon = {
                        AppIcons.Sliders(modifier = Modifier.size(21.dp), color = AppTheme.TintSlateInk)
                    }
                )
            }

            Spacer(modifier = Modifier.height(AppTheme.Space24))

            // --- Uscita ---------------------------------------------------------------------
            AilaCard {
                AilaListRow(
                    title = "Esci dall'account",
                    tint = AppTheme.TintRed,
                    onClick = onLogoutClick,
                    icon = {
                        // Era il carattere "→", disegnato dal sistema: stessa incoerenza delle
                        // altre frecce di testo sostituite nel kit.
                        AppIcons.ChevronRight(modifier = Modifier.size(20.dp), color = AppTheme.TintRedInk)
                    }
                )
                HorizontalDivider(color = AppTheme.Hairline, modifier = Modifier.padding(start = 72.dp))
                AilaListRow(
                    title = "Elimina account",
                    subtitle = "Cancella definitivamente il tuo account e i tuoi dati",
                    tint = AppTheme.TintRed,
                    onClick = { showDeleteDialog = true },
                    icon = {
                        AppIcons.Trash(modifier = Modifier.size(20.dp), color = AppTheme.TintRedInk)
                    }
                )
            }
        }
    }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = onDeleteAccount
        )
    }
}

/**
 * Conferma dell'eliminazione account: spiega cosa si perde e chiede la password, cosi' un tocco
 * sbagliato — o un telefono lasciato sbloccato — non basta a cancellare tutto.
 */
@Composable
private fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onConfirm: suspend (String) -> String?
) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        containerColor = AppTheme.SurfaceWhite,
        shape = RoundedCornerShape(AppTheme.CardCornerRadius),
        title = {
            Text(
                text = "Eliminare l'account?",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark
            )
        },
        text = {
            Column {
                Text(
                    text = "L'operazione è definitiva: profilo, voti, preferenze, proposte e commenti " +
                        "vengono cancellati e non si possono recuperare. Per confermare, inserisci la password.",
                    fontSize = 13.sp,
                    color = AppTheme.TextMuted,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                androidx.compose.material3.OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    placeholder = { Text("Password", fontSize = 13.sp) },
                    singleLine = true,
                    enabled = !isDeleting,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp),
                    colors = circolareplus.design.ailaFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(AppTheme.Space8))
                    Text(text = error!!, fontSize = 12.sp, color = AppTheme.TintRedInk, lineHeight = 17.sp)
                }
            }
        },
        confirmButton = {
            circolareplus.design.AilaDestructiveButton(
                text = if (isDeleting) "Elimino…" else "Elimina",
                compact = true,
                onClick = {
                    if (isDeleting) return@AilaDestructiveButton
                    if (password.isBlank()) {
                        error = "Inserisci la password per confermare."
                        return@AilaDestructiveButton
                    }
                    isDeleting = true
                    scope.launch {
                        // Se va a buon fine la schermata sparisce (l'utente non e' piu' loggato):
                        // non serve chiudere la finestra a mano.
                        error = onConfirm(password)
                        isDeleting = false
                    }
                }
            )
        },
        dismissButton = {
            circolareplus.design.AilaSecondaryButton(
                text = "Annulla",
                compact = true,
                onClick = { if (!isDeleting) onDismiss() }
            )
        }
    )
}

/** Riquadro con un dato del profilo dentro la card d'identità a gradiente. */
@Composable
private fun HeroStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp))
            .background(AppTheme.OnHeroSurface)
            .padding(vertical = AppTheme.Space12, horizontal = AppTheme.Space12),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (highlighted) {
                AppIcons.Star(modifier = Modifier.size(12.dp), color = AppTheme.OnHeroPrimary)
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.OnHeroPrimary,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, fontSize = 11.sp, color = AppTheme.OnHeroSecondary, maxLines = 1)
    }
}
