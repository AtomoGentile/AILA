package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.algorithms.DeskAssignment
import circolareplus.design.AppTheme
import circolareplus.domain.model.User

/**
 * Un banco della mappa, in sola lettura: usato dalla mappa pubblicata e dall'anteprima delle
 * proposte, così i due posti in cui si guarda una disposizione si vedono uguali.
 *
 * Ogni posto ha il suo numero (1, 2, 3) e il banco da trio porta anche l'etichetta "TRIO": prima
 * il terzo posto era solo una riga in più con lo stesso aspetto delle altre, e a colpo d'occhio un
 * trio non si distingueva da una coppia.
 *
 * [showThirdSeat] è deciso dalla disposizione intera e non dal singolo banco: in una mappa da
 * trii l'ultimo banco può avere due soli occupanti, ma il posto vuoto va comunque disegnato.
 */
@Composable
fun SeatMapDeskCard(
    desk: DeskAssignment,
    studentsMap: Map<String, User>,
    showThirdSeat: Boolean,
    modifier: Modifier = Modifier,
    focusedStudentId: String? = null
) {
    val seatIds = if (showThirdSeat) {
        listOf(desk.studentAId, desk.studentBId, desk.studentCId)
    } else {
        listOf(desk.studentAId, desk.studentBId)
    }
    val isFocusedDesk = focusedStudentId != null && seatIds.any { it == focusedStudentId }
    val shape = RoundedCornerShape(AppTheme.SmallElementRadius + 2.dp)

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isFocusedDesk) AppTheme.TintAmber else AppTheme.SurfaceWhite
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppTheme.CardElevation),
        modifier = modifier.border(
            width = if (isFocusedDesk) 2.dp else 1.dp,
            color = if (isFocusedDesk) AppTheme.TintAmberInk else AppTheme.Hairline,
            shape = shape
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "F${desk.row + 1}C${desk.column + 1}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TextMuted
                )
                if (showThirdSeat) {
                    Text(
                        text = "TRIO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.TintBlueInk,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AppTheme.TintBlue)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }

            seatIds.forEachIndexed { index, studentId ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 3.dp),
                        color = AppTheme.Hairline
                    )
                }
                SeatRow(
                    number = index + 1,
                    name = studentId?.let { studentsMap[it]?.firstName },
                    isFocused = studentId != null && studentId == focusedStudentId
                )
            }
        }
    }
}

@Composable
private fun SeatRow(number: Int, name: String?, isFocused: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Almeno 15dp e non fisso: col testo di sistema ingrandito il numero usciva dal cerchio.
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 15.dp, minHeight = 15.dp)
                .clip(CircleShape)
                .background(if (name == null) AppTheme.TintSlate else AppTheme.TintBlue),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                modifier = Modifier.padding(horizontal = 3.dp),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (name == null) AppTheme.TextFaint else AppTheme.TintBlueInk
            )
        }
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = name ?: "Vuoto",
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            color = if (name == null) AppTheme.TextFaint else AppTheme.TextDark,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
