package circolareplus.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Componenti condivisi del linguaggio grafico AILA.
 *
 * Perché esistono: prima ogni schermata si scriveva a mano intestazione, card, riquadri icona e
 * pulsanti, ognuna con i propri esadecimali e le proprie spaziature. Il risultato era un'app
 * ordinata ma che non somigliava al mockup — mancavano proprio gli elementi che nel mockup si
 * ripetono ovunque: il riquadro icona colorato accanto a ogni riga, l'ombra morbida sotto le
 * card, il gradiente nei pulsanti e nei pannelli, il marchio in cima alle schermate.
 *
 * Scelta di fondo, confermata con Simone: nel mockup solo la Home ha il pannello a gradiente;
 * tutte le altre schermate hanno intestazione chiara.
 */

/**
 * Il marchio AILA. Ora è il segno vettoriale di [AilaLogoTile] e non più il PNG: quello è
 * un'immagine senza canale alpha, quindi si portava dietro il proprio fondo blu notte e a 24dp
 * si impastava. Vedi la nota estesa in AilaLogo.kt.
 */
@Composable
fun AilaBrandMark(size: Dp = 26.dp, modifier: Modifier = Modifier) {
    AilaLogoTile(size = size, modifier = modifier)
}

/**
 * Intestazione chiara standard: riga del marchio, titolo, sottotitolo e uno slot di azione a
 * destra. Il marchio in cima c'è perché nel mockup ogni schermata lo porta: senza, le schermate
 * sembravano di un'app anonima.
 */
@Composable
fun AilaScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    showBrand: Boolean = true,
    action: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth().background(AppTheme.SurfaceWhite)) {
        if (showBrand) {
            Row(
                modifier = Modifier.padding(
                    start = AppTheme.Space16,
                    end = AppTheme.Space16,
                    top = AppTheme.Space20
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AilaBrandMark(size = 24.dp)
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                Text(
                    text = "AILA",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = AppTheme.PrimaryBlue,
                    letterSpacing = 1.sp
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppTheme.Space16,
                    end = AppTheme.Space16,
                    top = if (showBrand) AppTheme.Space8 else AppTheme.Space24,
                    bottom = AppTheme.Space16
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TextDark,
                    maxLines = 1
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = AppTheme.TextMuted,
                        maxLines = 1
                    )
                }
            }
            if (action != null) {
                Spacer(modifier = Modifier.width(AppTheme.Space12))
                action()
            }
        }
        HorizontalDivider(color = AppTheme.Hairline)
    }
}

/**
 * Barra superiore delle schermate aperte "sopra" le tab (dettaglio circolare, Scheda Classe,
 * Sondaggi, Notifiche): freccia indietro + titolo, stessa altezza e stesso divisore
 * dell'intestazione normale, così passare da una all'altra non fa "saltare" il layout.
 */
@Composable
fun AilaBackBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth().background(AppTheme.SurfaceWhite)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = AppTheme.Space12,
                    end = AppTheme.Space16,
                    top = AppTheme.Space20,
                    bottom = AppTheme.Space12
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                    .background(AppTheme.TintSlate)
                    .ailaPressable(pressedScale = 0.9f) { onBackClick() },
                contentAlignment = Alignment.Center
            ) {
                // Era il carattere "←": un glifo di testo, quindi disegnato dal sistema operativo,
                // diverso fra Android e iOS e di peso incoerente con le altre icone.
                AppIcons.ChevronLeft(modifier = Modifier.size(19.dp), color = AppTheme.TextDark)
            }
            Spacer(modifier = Modifier.width(AppTheme.Space12))
            Text(
                text = title,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (action != null) {
                Spacer(modifier = Modifier.width(AppTheme.Space8))
                action()
            }
        }
        HorizontalDivider(color = AppTheme.Hairline)
    }
}

/**
 * Selettore a segmenti (mockup: "Info / Membri / Materiali"). Usato per Circolari/Bacheca dentro
 * la tab "Classe" e per Accedi/Registrati nel login: prima erano chip identiche a quelle dei
 * filtri di contenuto, quindi non si capiva che cambiavano schermata invece di filtrare la lista.
 */
@Composable
fun AilaSegmentedTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    key: Any? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius + 3.dp))
            .background(AppTheme.TintSlate)
            .padding(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            // `key(isSelected)` forza Compose a ricreare da zero il nodo di layout/disegno del
            // segmento quando cambia la selezione, invece di riusare quello già composto. Senza,
            // dopo un giro "Sondaggio" -> "Storico" -> "Sondaggio" il pill selezionato a volte
            // restava con lo sfondo di default anche se `selectedIndex` era di nuovo giusto: il
            // resto della UI (che dipende dallo stesso stato) si aggiornava correttamente, quindi
            // il problema era isolato al layer di disegno di questo pill, non allo stato.
            androidx.compose.runtime.key(index, isSelected) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
                        .then(
                            if (isSelected) Modifier.background(AppTheme.PrimaryGradient)
                            else Modifier
                        )
                        .ailaPressable(pressedScale = 0.94f) { onSelect(index) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else AppTheme.TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Card bianca con ombra morbida: la superficie base di tutte le liste. */
@Composable
fun AilaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = AppTheme.SurfaceWhite,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(AppTheme.CardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = AppTheme.CardElevation),
        modifier = modifier
            .fillMaxWidth()
            // Scala più contenuta delle righe: su una superficie grande il 3% si nota già.
            .then(
                if (onClick != null) {
                    Modifier.ailaPressable(pressedScale = 0.985f) { onClick() }
                } else Modifier
            ),
        content = content
    )
}

/** Riquadro icona colorato: l'elemento che nel mockup accompagna ogni riga di ogni lista. */
@Composable
fun AilaIconTile(
    tint: Color = AppTheme.TintBlue,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(tint),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

/**
 * Riga standard "icona + titolo + sottotitolo + freccia" dentro una card. Copre la Home, il
 * Profilo, le notifiche e le liste di accesso rapido, che prima erano tutte scritte a mano.
 */
@Composable
fun AilaListRow(
    title: String,
    subtitle: String? = null,
    tint: Color = AppTheme.TintBlue,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.ailaPressable(pressedScale = 0.98f) { onClick() }
                } else Modifier
            )
            .padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            AilaIconTile(tint = tint, icon = icon)
            Spacer(modifier = Modifier.width(AppTheme.Space12))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark,
                maxLines = 1
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = AppTheme.TextMuted,
                    maxLines = 2
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(AppTheme.Space8))
            trailing()
        } else if (showChevron) {
            Spacer(modifier = Modifier.width(AppTheme.Space8))
            AppIcons.ChevronRight(modifier = Modifier.size(16.dp), color = AppTheme.TextFaint)
        }
    }
}

/**
 * Pulsante primario col riempimento sfumato del brand. Non è un Button di Material perché quello
 * accetta solo un colore pieno, e nel mockup i pulsanti sono sfumati.
 */
@Composable
fun AilaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = false,
    compact: Boolean = false,
    icon: (@Composable (Color) -> Unit)? = null
) {
    val shape = RoundedCornerShape(AppTheme.ButtonCornerRadius)
    Box(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(shape)
            .then(
                if (enabled) Modifier.background(AppTheme.PrimaryGradient)
                else Modifier.background(AppTheme.TintSlate)
            )
            .ailaPressable(enabled = enabled, pressedScale = 0.955f) { onClick() }
            .padding(
                horizontal = if (compact) AppTheme.Space12 else AppTheme.Space16,
                vertical = if (compact) 8.dp else 11.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        val contentColor = if (enabled) Color.White else AppTheme.TextFaint
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon(contentColor)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

/** Pulsante secondario: stesso ingombro del primario ma solo contorno. */
@Composable
fun AilaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    icon: (@Composable (Color) -> Unit)? = null
) {
    val shape = RoundedCornerShape(AppTheme.ButtonCornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(AppTheme.SurfaceWhite)
            .border(1.dp, AppTheme.Hairline, shape)
            .ailaPressable(pressedScale = 0.955f) { onClick() }
            .padding(
                horizontal = if (compact) AppTheme.Space12 else AppTheme.Space16,
                vertical = if (compact) 8.dp else 11.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon(AppTheme.TextDark)
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark,
                maxLines = 1
            )
        }
    }
}

/** Pulsante pieno per azioni distruttive ("Elimina"): stesso ingombro di [AilaPrimaryButton], ma
 * in tinta rossa invece che nel gradiente del brand — quello resta riservato alle azioni positive. */
@Composable
fun AilaDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(AppTheme.ButtonCornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(AppTheme.TintRed)
            .ailaPressable(pressedScale = 0.955f) { onClick() }
            .padding(
                horizontal = if (compact) AppTheme.Space12 else AppTheme.Space16,
                vertical = if (compact) 8.dp else 11.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = if (compact) 12.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TintRedInk,
            maxLines = 1
        )
    }
}

/**
 * Finestra di conferma standard (es. "Eliminare la proposta?"): stessi raggi, colori e pulsanti
 * del resto dell'app invece dell'`AlertDialog` grezzo di Material, che risaltava come l'unico
 * elemento "di sistema" in mezzo a schermate tutte disegnate con questo linguaggio grafico.
 */
@Composable
fun AilaConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "Elimina",
    dismissLabel: String = "Annulla",
    isDestructive: Boolean = true
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppTheme.SurfaceWhite,
        shape = RoundedCornerShape(AppTheme.CardCornerRadius),
        title = {
            Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppTheme.TextDark)
        },
        text = {
            Text(text = message, fontSize = 13.sp, color = AppTheme.TextMuted, lineHeight = 18.sp)
        },
        confirmButton = {
            if (isDestructive) {
                AilaDestructiveButton(text = confirmLabel, onClick = onConfirm, compact = true)
            } else {
                AilaPrimaryButton(text = confirmLabel, onClick = onConfirm, compact = true)
            }
        },
        dismissButton = {
            AilaSecondaryButton(text = dismissLabel, onClick = onDismiss, compact = true)
        }
    )
}

/** Titolo di sezione con link opzionale a destra ("Prossimi eventi — Vedi tutti"). */
@Composable
fun AilaSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextDark
        )
        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTheme.PrimaryBlue,
                modifier = Modifier.ailaPressable(pressedScale = 0.95f) { onActionClick() }
            )
        }
    }
}

/** Cerchio sfumato con l'icona dentro, usato dagli stati vuoti e d'errore. */
@Composable
private fun StateBadge(brush: Brush, icon: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(84.dp).clip(CircleShape).background(brush),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

/**
 * Stato vuoto curato (mockup "Vuoto"): cerchio sfumato con l'icona, titolo, spiegazione e —
 * quando ha senso — il pulsante che porta all'azione risolutiva. Sostituisce le righe di testo
 * grigio centrate usate finora, che sembravano un errore di caricamento.
 */
@Composable
fun AilaEmptyState(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: @Composable () -> Unit = {
        AppIcons.Document(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue)
    }
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.Space32, vertical = AppTheme.Space48),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StateBadge(
            brush = Brush.linearGradient(listOf(AppTheme.TintBlue, AppTheme.TintViolet)),
            icon = icon
        )
        Spacer(modifier = Modifier.height(AppTheme.Space16))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextDark,
            textAlign = TextAlign.Center
        )
        if (message != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = AppTheme.TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(AppTheme.Space20))
            AilaPrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

/**
 * Stato di errore (mockup "Errore di rete"): stessa struttura dello stato vuoto ma in tinta calda
 * e con "Riprova". Prima l'errore era una riga di testo rosso al centro dello schermo, senza
 * alcun modo di ritentare se non uscire e rientrare dalla scheda.
 */
@Composable
fun AilaErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String = "Qualcosa non ha funzionato",
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.Space32, vertical = AppTheme.Space48),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StateBadge(
            brush = Brush.linearGradient(listOf(AppTheme.TintRed, AppTheme.TintAmber)),
            icon = { Text(text = "!", fontSize = 34.sp, fontWeight = FontWeight.Black, color = AppTheme.TintRedInk) }
        )
        Spacer(modifier = Modifier.height(AppTheme.Space16))
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextDark,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = AppTheme.TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(AppTheme.Space20))
            AilaPrimaryButton(text = "Riprova", onClick = onRetry)
        }
    }
}

/**
 * Azione con icona e contatore (voti, commenti). Sostituisce le coppie "emoji + numero" della
 * bacheca: ha un'area di tocco vera, il colore del design system e due animazioni — rimpicciolisce
 * mentre la si preme e "rimbalza" quando il valore cambia, così si vede che il tocco è arrivato
 * anche prima che la risposta del server aggiorni il numero.
 */
@Composable
fun AilaIconAction(
    label: String,
    tint: Color,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: @Composable (Color) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Rimbalzo alla variazione del contatore: la chiave è il testo, quindi scatta quando cambia.
    var bounce by remember { mutableStateOf(false) }
    LaunchedEffect(label) {
        bounce = true
        delay(160)
        bounce = false
    }

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.92f
            bounce -> 1.10f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "iconActionScale"
    )
    val background by animateColorAsState(
        targetValue = if (selected) ink.copy(alpha = 0.16f) else tint,
        animationSpec = tween(200),
        label = "iconActionBg"
    )

    Row(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(background)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon(ink)
        if (label.isNotEmpty()) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ink)
        }
    }
}

/**
 * Marchio "generato dall'AI": il badge di [AilaAssistantGlyph] (senza il dettaglio dell'avatar,
 * troppo piccolo per restare leggibile a queste dimensioni) che pulsa piano. Sostituisce l'emoji
 * 🤖/✨ e comunica da sola che quel contenuto è il lavoro attivo dell'AI, non di una persona.
 */
@Composable
fun AilaAiBadge(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.TintViolet,
    ink: Color = AppTheme.TintVioletInk
) {
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            pulse = !pulse
            delay(1400)
        }
    }
    val alpha by animateFloatAsState(
        targetValue = if (pulse) 1f else 0.55f,
        animationSpec = tween(1400),
        label = "aiBadgePulse"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AilaAssistantGlyph(
            size = 13.dp,
            modifier = Modifier.alpha(alpha),
            brush = SolidColor(ink),
            badgeColor = ink,
            showAvatarDetail = false
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ink)
    }
}

/** Pallino colorato: stato di pertinenza, giorni con eventi, indicatori vari. */
@Composable
fun AilaDot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(color))
}

