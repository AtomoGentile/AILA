package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.data.remote.dto.RatingEntryDto
import circolareplus.design.AilaCard
import circolareplus.design.AilaSwitch
import circolareplus.design.AppTheme

/**
 * Scheda Classe del Rappresentante: unica schermata dove chi ha il ruolo REPRESENTATIVE può
 * impostare, per ogni compagno, le valutazioni didattica/comportamento (1-5, usate dall'algoritmo
 * della Mappa Posti) e il Priority Pass (diritto a stare in una delle prime 3 file). Prima di
 * questa schermata, `RatingsRepository`/`/api/ratings` esistevano già lato client e backend ma non
 * erano collegati a nessuna interfaccia: era impossibile assegnare qualunque cosa dall'app.
 */
@Composable
fun ClassRosterScreen(
    entries: List<RatingEntryDto>,
    currentUserId: String = "",
    onDidacticChange: (studentId: String, value: Int) -> Unit,
    onBehaviorChange: (studentId: String, value: Int) -> Unit,
    onPriorityPassChange: (studentId: String, enabled: Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
    ) {
        Column(modifier = Modifier.padding(horizontal = AppTheme.Space16, vertical = AppTheme.Space8)) {
            Text(
                text = "Valutazioni didattica/comportamento (1-5) e Priority Pass: usati dall'algoritmo " +
                    "quando generi le proposte di Mappa Posti.",
                fontSize = 12.sp,
                color = AppTheme.TextMuted
            )
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                circolareplus.design.AilaEmptyState(
                    title = "Nessun compagno di classe",
                    message = "Appena i tuoi compagni si registrano con il codice della classe, compaiono qui.",
                    icon = { circolareplus.design.AppIcons.Profile(modifier = Modifier.size(30.dp), color = AppTheme.PrimaryBlue) }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = AppTheme.Space16, vertical = AppTheme.Space8)
            ) {
                items(entries, key = { it.studentId }) { entry ->
                    ClassRosterRow(
                        entry = entry,
                        isSelf = entry.studentId == currentUserId,
                        onDidacticChange = { onDidacticChange(entry.studentId, it) },
                        onBehaviorChange = { onBehaviorChange(entry.studentId, it) },
                        onPriorityPassChange = { onPriorityPassChange(entry.studentId, it) }
                    )
                    Spacer(modifier = Modifier.height(AppTheme.Space12))
                }
            }
        }
    }
}

@Composable
private fun ClassRosterRow(
    entry: RatingEntryDto,
    isSelf: Boolean = false,
    onDidacticChange: (Int) -> Unit,
    onBehaviorChange: (Int) -> Unit,
    onPriorityPassChange: (Boolean) -> Unit
) {
    AilaCard {
        Column(modifier = Modifier.padding(AppTheme.Space16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppTheme.TintBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${entry.firstName.take(1)}${entry.lastName.take(1)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TintBlueInk
                    )
                }
                Spacer(modifier = Modifier.width(AppTheme.Space12))
                Text(
                    text = "${entry.firstName} ${entry.lastName}" + if (isSelf) " (Tu)" else "",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TextDark
                )
            }

            Spacer(modifier = Modifier.height(AppTheme.Space12))

            RatingStepper(
                label = "Didattica",
                value = entry.didactic ?: 3,
                onValueChange = onDidacticChange
            )

            Spacer(modifier = Modifier.height(8.dp))

            RatingStepper(
                label = "Comportamento",
                value = entry.behavior ?: 3,
                onValueChange = onBehaviorChange
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Priority Pass (prime 3 file)", fontSize = 14.sp, color = AppTheme.TextMuted)
                AilaSwitch(
                    checked = entry.priorityPass,
                    onCheckedChange = onPriorityPassChange
                )
            }
        }
    }
}

/** Selettore 1-5 con frecce +/-, senza dipendenze da picker più complessi. */
@Composable
private fun RatingStepper(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, color = AppTheme.TextMuted)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (value > 1) onValueChange(value - 1) },
                modifier = Modifier.size(28.dp)
            ) {
                Text(text = "−", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppTheme.PrimaryBlue)
            }
            Text(
                text = "$value",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = AppTheme.TextDark,
                modifier = Modifier.padding(horizontal = AppTheme.Space8)
            )
            IconButton(
                onClick = { if (value < 5) onValueChange(value + 1) },
                modifier = Modifier.size(28.dp)
            ) {
                Text(text = "+", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppTheme.PrimaryBlue)
            }
        }
    }
}
