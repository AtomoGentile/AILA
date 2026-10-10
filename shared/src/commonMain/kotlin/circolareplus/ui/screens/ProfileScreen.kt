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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import circolareplus.design.AilaCard
import circolareplus.design.AilaListRow
import circolareplus.design.AilaScreenHeader
import circolareplus.design.AilaSectionTitle
import circolareplus.design.ailaAppear
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.ailaGlassSurface
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
    /** Le notifiche stanno qui: prima avevano la campanella in Home. */
    onOpenNotifications: () -> Unit = {},
    hasUnreadNotifications: Boolean = false,
    onLogoutClick: () -> Unit = {},
    /**
     * Elimina l'account dopo la conferma con password. Restituisce il messaggio d'errore da
     * mostrare nella finestra, oppure `null` se l'account e' stato eliminato.
     */
    onDeleteAccount: suspend (password: String) -> String? = { null },
    /** false quando la schermata e' aperta dall'avatar, sotto una barra con la freccia indietro. */
    showHeader: Boolean = true
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    val isRepresentative = user.role == UserRole.REPRESENTATIVE
    // Classe e anno: prima erano il sottotitolo dell'intestazione, ora stanno nella card identita'.
    val classLine = buildString {
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
        if (showHeader) {
            AilaScreenHeader(title = "Il mio Profilo")
        }

        val identitySection: @Composable () -> Unit = {
            // --- Card identità -------------------------------------------------------------
            // Rifatta: era un riquadro bianco con le iniziali in un cerchietto e due righe
            // "etichetta: valore" allineate a destra, indistinguibile dalle card di impostazioni
            // sotto. Ora è il pannello a gradiente del brand, con l'avatar in evidenza e i due
            // dati come riquadri affiancati, che si leggono a colpo d'occhio.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        // Liquid Glass: pannello di vetro col suo bordo di luce e un velo piu'
                        // presente del resto del vetro (col solo gradiente, ormai quasi a zero,
                        // e senza bordo, il pannello spariva). Material: il gradiente pieno.
                        // Il velo resta scritto qui: nessun token di AppTheme ha questi valori
                        // (OnHeroSurface in Glass e' quasi trasparente, ed e' proprio il problema).
                        if (AppTheme.isGlass) Modifier.ailaGlassSurface(
                            RoundedCornerShape(AppTheme.CardCornerRadius + 4.dp),
                            tint = if (AppTheme.isDarkMode) Color(0x1AFFFFFF) else Color(0x40FFFFFF)
                        )
                        else Modifier
                            .clip(RoundedCornerShape(AppTheme.CardCornerRadius + 4.dp))
                            .background(AppTheme.HeroGradient)
                    )
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
                            style = MaterialTheme.typography.displaySmall,
                            color = AppTheme.OnHeroPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(AppTheme.Space12))

                    Text(
                        text = "${user.firstName} ${user.lastName}",
                        style = MaterialTheme.typography.titleLarge,
                        color = AppTheme.OnHeroPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "@${user.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.OnHeroSecondary,
                        maxLines = 1
                    )
                    if (classLine != null) {
                        Text(
                            text = classLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppTheme.OnHeroSecondary,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(AppTheme.Space12))

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppTheme.OnHeroSurface)
                            .padding(horizontal = AppTheme.Space12, vertical = AppTheme.Space4),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isRepresentative) {
                            AppIcons.Crown(modifier = Modifier.size(13.dp), color = AppTheme.OnHeroPrimary)
                            Spacer(modifier = Modifier.width(AppTheme.Space4))
                        }
                        Text(
                            text = if (isRepresentative) "Rappresentante" else "Studente",
                            style = MaterialTheme.typography.labelMedium,
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
        }
        val restSection: @Composable () -> Unit = {
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
                    title = "Notifiche",
                    subtitle = if (hasUnreadNotifications) "Ci sono novità da leggere" else "Circolari, bacheca e calendario",
                    tint = AppTheme.TintBlue,
                    onClick = onOpenNotifications,
                    icon = {
                        AppIcons.Bell(
                            modifier = Modifier.size(21.dp),
                            color = AppTheme.TintBlueInk,
                            hasBadge = hasUnreadNotifications
                        )
                    }
                )
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
                    title = "Cambia password",
                    subtitle = "Gli altri dispositivi dovranno accedere di nuovo",
                    tint = AppTheme.TintSlate,
                    onClick = { showPasswordDialog = true },
                    icon = {
                        AppIcons.Lock(modifier = Modifier.size(20.dp), color = AppTheme.TintSlateInk)
                    }
                )
                HorizontalDivider(color = AppTheme.Hairline, modifier = Modifier.padding(start = 72.dp))
                AilaListRow(
                    title = "Esci dall'account",
                    tint = AppTheme.TintRed,
                    onClick = { showLogoutConfirm = true },
                    icon = {
                        // Era il carattere "→", disegnato dal sistema: stessa incoerenza delle
                        // altre frecce di testo sostituite nel kit.
                        AppIcons.ChevronRight(modifier = Modifier.size(20.dp), color = AppTheme.TintRedInk)
                    }
                )
                HorizontalDivider(color = AppTheme.Hairline, modifier = Modifier.padding(start = 72.dp))
                AilaListRow(
                    title = "Elimina account",
                    subtitle = "Elimina definitivamente il tuo account e i tuoi dati",
                    tint = AppTheme.TintRed,
                    onClick = { showDeleteDialog = true },
                    icon = {
                        AppIcons.Trash(modifier = Modifier.size(20.dp), color = AppTheme.TintRedInk)
                    }
                )
            }
        }
        if (circolareplus.design.LocalWideLayout.current) {
            // Tablet e iPad larghi: la scheda della persona a sinistra, classe, impostazioni e
            // uscita a destra.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = AppTheme.Space16)
                    .padding(top = AppTheme.Space16, bottom = AppTheme.Space32 + circolareplus.design.LocalBottomBarPadding.current),
                horizontalArrangement = Arrangement.spacedBy(AppTheme.Space20)
            ) {
                Column(modifier = Modifier.weight(1f)) { identitySection() }
                Column(modifier = Modifier.weight(1f)) { restSection() }
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.Space16)
                .padding(top = AppTheme.Space16, bottom = AppTheme.Space32 + circolareplus.design.LocalBottomBarPadding.current)
        ) {
            identitySection()
            Spacer(modifier = Modifier.height(AppTheme.Space24))
            restSection()
        }
        }
    }

    if (showDeleteDialog) {
        DeleteAccountDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = onDeleteAccount
        )
    }
    // L'uscita cancella anche chiave Gemini, conversazioni con l'assistente e analisi salvate su
    // questo telefono: meglio dirlo prima (come nella PWA).
    if (showLogoutConfirm) {
        circolareplus.design.AilaConfirmDialog(
            title = "Uscire da AILA?",
            message = "Su questo telefono verranno cancellati anche la chiave AI, le conversazioni con l'assistente e i dati offline.",
            onDismiss = { showLogoutConfirm = false },
            onConfirm = {
                showLogoutConfirm = false
                onLogoutClick()
            },
            confirmLabel = "Esci"
        )
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(onDismiss = { showPasswordDialog = false })
    }
}

/** Cambio password: quella attuale, la nuova due volte. Il token nuovo lo salva il repository. */
@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    @Composable
    fun field(value: String, placeholder: String, onChange: (String) -> Unit) {
        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = {
                onChange(it)
                error = null
            },
            placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
            singleLine = true,
            enabled = !busy && !done,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
            ),
            shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp),
            colors = circolareplus.design.ailaFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = AppTheme.SurfaceWhite,
        shape = RoundedCornerShape(AppTheme.CardCornerRadius),
        title = {
            Text(text = "Cambia password", style = MaterialTheme.typography.titleLarge, color = AppTheme.TextDark)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)) {
                if (done) {
                    Text(
                        text = "Password cambiata. Su questo telefono resti dentro; sugli altri dispositivi va fatto di nuovo l'accesso.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.TextMuted
                    )
                } else {
                    field(current, "Password attuale") { current = it }
                    field(next, "Nuova password (almeno 8 caratteri)") { next = it }
                    field(repeat, "Ripeti la nuova password") { repeat = it }
                    error?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium, color = AppTheme.TintRedInk) }
                }
            }
        },
        confirmButton = {
            circolareplus.design.AilaPrimaryButton(
                text = when {
                    done -> "Fatto"
                    busy -> "Salvo…"
                    else -> "Cambia"
                },
                compact = true,
                onClick = {
                    if (done) {
                        onDismiss()
                        return@AilaPrimaryButton
                    }
                    if (busy) return@AilaPrimaryButton
                    error = when {
                        current.isBlank() -> "Scrivi la password attuale."
                        next.length < 8 -> "La nuova password deve essere di almeno 8 caratteri."
                        next != repeat -> "Le due password nuove non coincidono."
                        else -> null
                    }
                    if (error != null) return@AilaPrimaryButton
                    busy = true
                    scope.launch {
                        try {
                            circolareplus.data.AppContainer.authRepository.changePassword(current, next)
                            done = true
                        } catch (e: circolareplus.data.remote.ApiException) {
                            error = e.message
                        } catch (e: Exception) {
                            error = "Impossibile contattare il server. Riprova."
                        } finally {
                            busy = false
                        }
                    }
                }
            )
        },
        dismissButton = {
            if (!done) {
                circolareplus.design.AilaSecondaryButton(
                    text = "Annulla",
                    compact = true,
                    onClick = { if (!busy) onDismiss() }
                )
            }
        }
    )
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
                style = MaterialTheme.typography.titleLarge,
                color = AppTheme.TextDark
            )
        },
        text = {
            Column {
                Text(
                    text = "L'operazione è definitiva: profilo, voti, preferenze, proposte e commenti " +
                        "vengono cancellati e non si possono recuperare. Per confermare, inserisci la password.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppTheme.TextMuted
                )
                Spacer(modifier = Modifier.height(AppTheme.Space12))
                androidx.compose.material3.OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    placeholder = { Text("Password", style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    enabled = !isDeleting,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                    ),
                    shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp),
                    colors = circolareplus.design.ailaFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(AppTheme.Space8))
                    Text(text = error!!, style = MaterialTheme.typography.bodyMedium, color = AppTheme.TintRedInk)
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
                Spacer(modifier = Modifier.width(AppTheme.Space4))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = AppTheme.OnHeroPrimary,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.height(AppTheme.Space4))
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = AppTheme.OnHeroSecondary, maxLines = 1)
    }
}
