package circolareplus.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.data.AppContainer
import circolareplus.data.remote.ApiException
import circolareplus.data.remote.dto.ClassOptionDto
import circolareplus.design.AilaLogoTile
import circolareplus.design.AilaSegmentedTabs
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.design.appSafeDrawingPadding
import circolareplus.design.appContentWidth
import circolareplus.design.MaxFormWidth
import circolareplus.design.ailaAppear
import circolareplus.design.ailaFieldColors
import circolareplus.domain.model.StudentProfile
import circolareplus.domain.model.User
import kotlinx.coroutines.launch

/**
 * Accesso e registrazione.
 *
 * Rifatta dopo l'osservazione di Simone ("ste schermate puoi farle meglio" + il logo che appariva
 * come un quadrato spiaccicato). Cosa è cambiato e perché:
 *
 * - il logo non è più il PNG mostrato a 72dp senza ritaglio — che essendo un'immagine senza canale
 *   alpha stampava il proprio fondo blu notte sulla card bianca — ma il marchio vettoriale con
 *   alone morbido;
 * - lo sfondo non è più un grigio piatto: una velatura del gradiente del brand in alto lega la
 *   schermata al resto dell'app;
 * - i campi usano i segnaposto invece delle etichette fluttuanti, come nel mockup, e la password
 *   ha il pulsante mostra/nascondi (prima si poteva solo sbagliare a scrivere e non accorgersene);
 * - l'altezza in registrazione non è più uno slider nudo con una didascalia, ma una riga con il
 *   valore in evidenza e la spiegazione del perché viene chiesta;
 * - i campi della registrazione entrano ed escono con un'animazione invece di apparire di scatto.
 */
@Composable
fun AuthScreen(
    onLoginSuccess: (User, StudentProfile) -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }

    // Campi comuni
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Campi specifici per la registrazione (Nome, Cognome, Altezza 140-210 a scaglioni di 5cm)
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var selectedHeightCm by remember { mutableStateOf(175) }
    var representativeCode by remember { mutableStateOf("") }

    // Classe. Prima non si sceglieva: il server ne conosceva una sola e tutti finivano lì
    // dentro. L'elenco arriva dal server (le classi che esistono già) e si può comunque
    // scriverne una nuova: la prima persona di una classe la porta in vita registrandosi.
    var classInput by remember { mutableStateOf("") }
    var knownClasses by remember { mutableStateOf<List<ClassOptionDto>>(emptyList()) }
    // Codice della classe: per entrare in una classe che ha gia' iscritti serve quello che il
    // Rappresentante vede nella Scheda Classe. Il campo compare quando la classe scelta esiste gia'
    // (o quando il server lo chiede, se l'elenco delle classi non si e' caricato).
    var classCode by remember { mutableStateOf("") }
    var classCodeAsked by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Si carica una volta sola, in sottofondo: se il server non risponde la registrazione resta
    // possibile scrivendo la classe a mano, quindi l'errore qui non va mostrato.
    LaunchedEffect(Unit) {
        knownClasses = try {
            AppContainer.authRepository.listClasses()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun submit() {
        errorMessage = null
        if (isRegisterMode) {
            if (firstName.isBlank() || lastName.isBlank() || username.isBlank() || password.isBlank()) {
                errorMessage = "Compila tutti i campi obbligatori."
                return
            }
            if (classInput.isBlank()) {
                errorMessage = "Indica la tua classe (per esempio 4 CSA)."
                return
            }
            if (password.length < 8) {
                errorMessage = "La password deve essere di almeno 8 caratteri."
                return
            }
        } else if (username.isBlank() || password.isBlank()) {
            errorMessage = "Inserisci username e password."
            return
        }

        isLoading = true
        coroutineScope.launch {
            try {
                val (user, profile) = if (isRegisterMode) {
                    AppContainer.authRepository.register(
                        firstName = firstName.trim(),
                        lastName = lastName.trim(),
                        username = username.trim(),
                        password = password,
                        heightCm = selectedHeightCm,
                        classLabel = classInput.trim(),
                        representativeCode = representativeCode.trim(),
                        classCode = classCode
                    )
                } else {
                    AppContainer.authRepository.login(
                        username = username.trim(),
                        password = password
                    )
                }
                onLoginSuccess(user, profile)
            } catch (e: ApiException) {
                if (isRegisterMode && e.statusCode == 403 && e.message.contains("codice classe", ignoreCase = true)) {
                    classCodeAsked = true
                }
                // Il codice Rappresentante vale per una classe sola e una volta sola: i messaggi del
                // server (altra classe, gia' usato, scaduto) dicono gia' cosa fare; qui si traduce
                // solo il caso del server non ancora aggiornato.
                errorMessage = if (isRegisterMode && e.statusCode == 503 && e.message.contains("Rappresentante")) {
                    "La registrazione come Rappresentante non è ancora attiva sul server. Riprova più tardi."
                } else {
                    e.message
                }
            } catch (e: Exception) {
                errorMessage = "Impossibile contattare il server. Controlla la connessione e riprova."
            } finally {
                isLoading = false
            }
        }
    }

    val joiningExistingClass = remember(classInput, knownClasses) {
        val wanted = classKey(classInput)
        wanted.isNotEmpty() && knownClasses.any { it.studentCount > 0 && classKey(it.label) == wanted }
    }

    if (showResetDialog) {
        ResetPasswordDialog(
            initialUsername = username.trim(),
            onDismiss = { showResetDialog = false },
            onReset = { user, profile ->
                showResetDialog = false
                onLoginSuccess(user, profile)
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(AppTheme.BackgroundLight)) {
        // Velatura del gradiente del brand: la schermata non è più un rettangolo grigio piatto.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            AppTheme.PrimaryBlue.copy(alpha = if (AppTheme.isDarkMode) 0.20f else 0.13f),
                            AppTheme.BackgroundLight.copy(alpha = 0f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .appSafeDrawingPadding()
                .appContentWidth(MaxFormWidth)
                .verticalScroll(scrollState)
                .padding(horizontal = AppTheme.Space20, vertical = AppTheme.Space32),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(AppTheme.Space16))

            AilaLogoTile(size = 74.dp, glow = true, modifier = Modifier.ailaAppear(0))

            Spacer(modifier = Modifier.height(AppTheme.Space8))

            Text(
                text = "AILA",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = AppTheme.PrimaryBlue,
                modifier = Modifier.ailaAppear(1)
            )
            Text(
                text = "La tua scuola, sincronizzata.",
                fontSize = 13.sp,
                color = AppTheme.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.ailaAppear(1)
            )

            Spacer(modifier = Modifier.height(AppTheme.Space24))

            Card(
                shape = RoundedCornerShape(AppTheme.CardCornerRadius + 4.dp),
                colors = CardDefaults.cardColors(containerColor = AppTheme.SurfaceWhite),
                elevation = CardDefaults.cardElevation(defaultElevation = AppTheme.CardElevation),
                modifier = Modifier.fillMaxWidth().ailaAppear(2)
            ) {
                Column(modifier = Modifier.padding(AppTheme.Space20)) {

                    AilaSegmentedTabs(
                        labels = listOf("Accedi", "Registrati"),
                        selectedIndex = if (isRegisterMode) 1 else 0,
                        onSelect = { index ->
                            isRegisterMode = index == 1
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(AppTheme.Space20))

                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = circolareplus.design.ailaExpandEnter(),
                        exit = circolareplus.design.ailaCollapseExit()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = AppTheme.Space16)
                                .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                                .background(AppTheme.TintRed)
                                .padding(AppTheme.Space12),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcons.Warning(
                                modifier = Modifier.size(18.dp),
                                color = AppTheme.TintRedInk
                            )
                            Spacer(modifier = Modifier.width(AppTheme.Space8))
                            Text(
                                text = errorMessage ?: "",
                                fontSize = 13.sp,
                                color = AppTheme.TintRedInk,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isRegisterMode,
                        enter = circolareplus.design.ailaExpandEnter(),
                        exit = circolareplus.design.ailaCollapseExit()
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(AppTheme.Space12)
                            ) {
                                AuthField(
                                    value = firstName,
                                    onValueChange = { firstName = it },
                                    placeholder = "Nome",
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = nameKeyboard
                                )
                                AuthField(
                                    value = lastName,
                                    onValueChange = { lastName = it },
                                    placeholder = "Cognome",
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = nameKeyboard
                                )
                            }

                            Spacer(modifier = Modifier.height(AppTheme.Space16))

                            ClassPicker(
                                value = classInput,
                                onValueChange = { classInput = it },
                                knownClasses = knownClasses
                            )

                            AnimatedVisibility(
                                visible = joiningExistingClass || classCodeAsked,
                                enter = circolareplus.design.ailaExpandEnter(),
                                exit = circolareplus.design.ailaCollapseExit()
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(AppTheme.Space12))
                                    AuthField(
                                        value = classCode,
                                        onValueChange = { classCode = it.uppercase().take(12) },
                                        placeholder = "Codice classe",
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(
                                            capitalization = KeyboardCapitalization.Characters,
                                            autoCorrectEnabled = false
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(AppTheme.Space4))
                                    Text(
                                        text = "Te lo dà il Rappresentante: lo trova nella Scheda Classe.",
                                        fontSize = 11.sp,
                                        color = AppTheme.TextFaint
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(AppTheme.Space16))

                            HeightPicker(
                                heightCm = selectedHeightCm,
                                onHeightChange = { selectedHeightCm = it }
                            )

                            Spacer(modifier = Modifier.height(AppTheme.Space16))
                        }
                    }

                    AuthField(
                        value = username,
                        onValueChange = { username = it },
                        placeholder = "Username",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false
                        )
                    )

                    Spacer(modifier = Modifier.height(AppTheme.Space12))

                    AuthField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = "Password",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password
                        ),
                        visualTransformation = if (isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailing = {
                            // Prima non c'era: una password sbagliata di un carattere restava
                            // invisibile e sembrava un errore del server.
                            // L'occhio al posto di "Mostra"/"Nascondi": e' il simbolo che tutti
                            // conoscono e non ruba spazio alla password.
                            Box(
                                modifier = Modifier
                                    .padding(end = AppTheme.Space4)
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { isPasswordVisible = !isPasswordVisible }
                                    .semantics {
                                        contentDescription =
                                            if (isPasswordVisible) "Nascondi password" else "Mostra password"
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isPasswordVisible) {
                                    AppIcons.EyeOff(modifier = Modifier.size(20.dp), color = AppTheme.TextMuted)
                                } else {
                                    AppIcons.Eye(modifier = Modifier.size(20.dp), color = AppTheme.TextMuted)
                                }
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = !isRegisterMode,
                        enter = circolareplus.design.ailaExpandEnter(),
                        exit = circolareplus.design.ailaCollapseExit()
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            TextButton(onClick = { showResetDialog = true }) {
                                Text("Password dimenticata?", fontSize = 13.sp, color = AppTheme.PrimaryBlue)
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = isRegisterMode,
                        enter = circolareplus.design.ailaExpandEnter(),
                        exit = circolareplus.design.ailaCollapseExit()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(AppTheme.Space12))
                            AuthField(
                                value = representativeCode,
                                onValueChange = { representativeCode = it },
                                placeholder = "Codice Rappresentante della classe (facoltativo)",
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false
                                )
                            )
                            Spacer(modifier = Modifier.height(AppTheme.Space4))
                            Text(
                                text = "Solo per i Rappresentanti eletti: il codice vale per la classe scelta sopra e una volta sola. Lascialo vuoto se ti registri come studente.",
                                fontSize = 11.sp,
                                color = AppTheme.TextFaint
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(AppTheme.Space24))

                    // Resta un Box "a mano" invece di AilaPrimaryButton perché deve poter
                    // mostrare lo spinner al posto del testo mentre la richiesta è in corso.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(AppTheme.ButtonCornerRadius))
                            .background(
                                if (isLoading) Brush.horizontalGradient(
                                    listOf(AppTheme.TintSlate, AppTheme.TintSlate)
                                ) else AppTheme.PrimaryGradient
                            )
                            .clickable(enabled = !isLoading) { submit() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AppTheme.TextMuted,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isRegisterMode) "Completa la registrazione" else "Accedi",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppTheme.Space16))

            Text(
                text = "Nessuna email, nessun dato della scuola: username e password li scegli tu.",
                fontSize = 11.sp,
                color = AppTheme.TextFaint,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = AppTheme.Space16).ailaAppear(3)
            )

            var showPrivacy by remember { mutableStateOf(false) }
            Text(
                text = if (showPrivacy) "Chiudi l'informativa" else "Informativa sulla privacy",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showPrivacy = !showPrivacy }
                    .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space8)
                    .ailaAppear(3)
            )
            AnimatedVisibility(visible = showPrivacy) {
                PrivacyPolicyContent(modifier = Modifier.padding(horizontal = AppTheme.Space16))
            }

            Spacer(modifier = Modifier.height(AppTheme.Space24))
        }
    }
}

/** "4^ CSA", "4 csa" e "4CSA" sono la stessa classe. */
private fun classKey(label: String): String = label.uppercase().filter { it.isLetterOrDigit() }

/**
 * Password dimenticata: niente email nell'app, quindi il Rappresentante genera un codice monouso
 * dalla Scheda Classe e lo dice al compagno, che qui sceglie la password nuova ed entra.
 */
@Composable
private fun ResetPasswordDialog(
    initialUsername: String,
    onDismiss: () -> Unit,
    onReset: (User, StudentProfile) -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        // Stessa superficie e forma dei dialoghi del design system (AilaConfirmDialog).
        containerColor = AppTheme.SurfaceWhite,
        shape = RoundedCornerShape(AppTheme.CardCornerRadius),
        title = { Text("Password dimenticata") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.Space12)) {
                Text(
                    text = "Chiedi al Rappresentante un codice di reset (lo genera dalla Scheda Classe, vale 24 ore), poi scegli la password nuova.",
                    fontSize = 13.sp,
                    color = AppTheme.TextMuted,
                    lineHeight = 18.sp
                )
                AuthField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "Username",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false)
                )
                AuthField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(12) },
                    placeholder = "Codice di reset",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false)
                )
                AuthField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    placeholder = "Nuova password (almeno 8 caratteri)",
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password
                    )
                )
                error?.let { Text(it, fontSize = 13.sp, color = AppTheme.TintRedInk) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    error = null
                    when {
                        username.isBlank() || code.isBlank() -> error = "Scrivi username e codice."
                        newPassword.length < 8 -> error = "La password deve essere di almeno 8 caratteri."
                        else -> {
                            busy = true
                            scope.launch {
                                try {
                                    val (user, profile) = AppContainer.authRepository.resetPassword(username, code, newPassword)
                                    onReset(user, profile)
                                } catch (e: ApiException) {
                                    error = e.message
                                } catch (e: Exception) {
                                    error = "Impossibile contattare il server. Riprova."
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                }
            ) { Text(if (busy) "Attendi…" else "Reimposta ed entra") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Annulla") }
        }
    )
}

private val nameKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.Words,
    autoCorrectEnabled = false
)

/** Campo di testo del login: segnaposto invece di etichetta fluttuante, come nel mockup. */
@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    // Senza indicazioni iOS correggeva/maiuscolava a suo modo username e codici, e la password
    // non veniva trattata come tale (niente modalita' sicura, niente suggerimenti password).
    keyboardOptions: KeyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(text = placeholder, fontSize = 14.sp, color = AppTheme.TextFaint)
        },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailing,
        shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp),
        colors = ailaFieldColors(),
        modifier = modifier
    )
}

/**
 * Selettore dell'altezza. Serve all'algoritmo della mappa posti (chi è più alto non va davanti a
 * chi è più basso): la spiegazione sta accanto al campo, non in una nota che nessuno legge.
 */
@Composable
private fun HeightPicker(
    heightCm: Int,
    onHeightChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Altezza",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.TextDark
            )
            Text(
                text = "$heightCm cm",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TintBlueInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppTheme.ButtonCornerRadius))
                    .background(AppTheme.TintBlue)
                    .padding(horizontal = AppTheme.Space12, vertical = AppTheme.Space4)
            )
        }

        // Da 140 a 210 a scaglioni di 5cm: (210-140)/5 = 14 intervalli, quindi 13 tacche interne.
        Slider(
            value = heightCm.toFloat(),
            onValueChange = { raw ->
                val rounded = (kotlin.math.round(raw / 5f) * 5).toInt()
                onHeightChange(rounded.coerceIn(140, 210))
            },
            valueRange = 140f..210f,
            steps = 13
        )

        Text(
            text = "Serve solo a calcolare la visuale verso la cattedra nella mappa posti.",
            fontSize = 11.sp,
            color = AppTheme.TextFaint,
            lineHeight = 15.sp
        )
    }
}

/**
 * Scelta della classe: le classi già esistenti come pastiglie da toccare, più la possibilità di
 * scriverne una nuova.
 *
 * L'elenco serve a evitare il problema pratico del testo libero: "4 CSA", "4^CSA" e "4csa"
 * sarebbero tre classi diverse, ognuna con la sua bacheca, e nessuno potrebbe più unirle. Il
 * server normalizza comunque l'etichetta, ma vedere la propria classe già lì e toccarla è più
 * veloce e sbaglia meno.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassPicker(
    value: String,
    onValueChange: (String) -> Unit,
    knownClasses: List<ClassOptionDto>
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "La tua classe",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppTheme.TextDark
        )

        if (knownClasses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(AppTheme.Space8))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppTheme.Space8),
                verticalArrangement = Arrangement.spacedBy(AppTheme.Space8)
            ) {
                knownClasses.forEach { option ->
                    val isSelected = value.trim().equals(option.label, ignoreCase = true)
                    Text(
                        text = option.label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else AppTheme.TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTheme.ButtonCornerRadius))
                            .then(
                                if (isSelected) Modifier.background(AppTheme.PrimaryGradient)
                                else Modifier.background(AppTheme.TintSlate)
                            )
                            .clickable { onValueChange(option.label) }
                            // 44dp di altezza: con 8dp sopra e sotto la chip della classe era
                            // alta circa 34dp, difficile da centrare col pollice.
                            .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.Space8))

        AuthField(
            value = value,
            onValueChange = onValueChange,
            placeholder = if (knownClasses.isEmpty()) "4 CSA" else "Oppure scrivi la tua classe",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(AppTheme.Space4))

        Text(
            text = "Vedrai circolari, bacheca, calendario e mappa posti di questa classe soltanto.",
            fontSize = 11.sp,
            color = AppTheme.TextFaint,
            lineHeight = 15.sp
        )
    }
}
