package circolareplus.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import circolareplus.design.AppTheme
import circolareplus.domain.model.SocialPreference
import circolareplus.domain.model.SocialPreferenceScore
import circolareplus.domain.model.User

@Composable
fun SocialPreferencesVotingScreen(
    classmates: List<User>,
    currentVotes: Map<String, SocialPreferenceScore>,
    onVoteChanged: (targetStudentId: String, score: SocialPreferenceScore) -> Unit,
    onSubmitVotes: () -> Unit
) {
    // Calcolo limiti massimi per prevenire abusi (max 2 voti +2 e max 2 voti -2)
    val plusTwoCount = currentVotes.values.count { it == SocialPreferenceScore.STRONG_AFFINITY }
    val minusTwoCount = currentVotes.values.count { it == SocialPreferenceScore.STRONG_REJECTION }

    // Votare tutti è obbligatorio: "0" non è più preselezionato per chi non è mai stato votato,
    // è una scelta esplicita come le altre (e viene registrato come tale). Il tasto Salva resta
    // spento finché resta anche un solo compagno senza voto.
    val missingVotes = classmates.count { it.id !in currentVotes }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.BackgroundLight)
            .padding(AppTheme.Space16)
    ) {
        Text(
            text = "Esprimi le tue Preferenze",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = AppTheme.TextDark
        )
        Text(
            text = "Finestra aperta dal Rappresentante \u2022 Voti strettamente confidenziali",
            fontSize = 12.sp,
            color = AppTheme.TextMuted
        )

        Text(
            text = "Devi votare tutti i compagni prima di salvare" +
                if (missingVotes > 0) " • ne mancano $missingVotes" else " • hai votato tutti",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (missingVotes > 0) AppTheme.PollDarkRed else AppTheme.TintGreenInk,
            modifier = Modifier.padding(top = AppTheme.Space4)
        )

        Spacer(modifier = Modifier.height(AppTheme.Space12))

        // Contatori di Budget (+2 e -2)
        Card(
            shape = RoundedCornerShape(AppTheme.CardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = AppTheme.TintBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppTheme.Space12),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Text(
                    text = "Preferenza massima (+2): $plusTwoCount/2",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.TintBlueInk
                )
                Text(
                    text = "Rifiuto assoluto (-2): $minusTwoCount/2",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.PollDarkRed
                )
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.Space16))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(AppTheme.Space12),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(classmates) { classmate ->
                // null = non ancora votato: nessun pulsante selezionato.
                val currentScore = currentVotes[classmate.id]

                Card(
                    shape = RoundedCornerShape(AppTheme.CardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.SurfaceWhite),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(AppTheme.Space12)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${classmate.firstName} ${classmate.lastName}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppTheme.TextDark,
                                modifier = Modifier.weight(1f)
                            )
                            if (currentScore == null) {
                                Text(
                                    text = "Da votare",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AppTheme.PollDarkRed
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(AppTheme.Space8))

                        // Bottoni per i 5 valori (+2, +1, 0, -1, -2)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            SocialScoreButton(
                                label = "+2",
                                isSelected = currentScore == SocialPreferenceScore.STRONG_AFFINITY,
                                enabled = currentScore == SocialPreferenceScore.STRONG_AFFINITY || plusTwoCount < 2,
                                activeColor = Color(0xFF22C55E),
                                onClick = { onVoteChanged(classmate.id, SocialPreferenceScore.STRONG_AFFINITY) }
                            )
                            SocialScoreButton(
                                label = "+1",
                                isSelected = currentScore == SocialPreferenceScore.MEDIUM_AFFINITY,
                                activeColor = Color(0xFF86EFAC),
                                onClick = { onVoteChanged(classmate.id, SocialPreferenceScore.MEDIUM_AFFINITY) }
                            )
                            SocialScoreButton(
                                label = "0",
                                isSelected = currentScore == SocialPreferenceScore.NEUTRAL,
                                activeColor = Color(0xFFCBD5E1),
                                onClick = { onVoteChanged(classmate.id, SocialPreferenceScore.NEUTRAL) }
                            )
                            SocialScoreButton(
                                label = "-1",
                                isSelected = currentScore == SocialPreferenceScore.MILD_REJECTION,
                                activeColor = Color(0xFFFCA5A5),
                                onClick = { onVoteChanged(classmate.id, SocialPreferenceScore.MILD_REJECTION) }
                            )
                            SocialScoreButton(
                                label = "-2",
                                isSelected = currentScore == SocialPreferenceScore.STRONG_REJECTION,
                                enabled = currentScore == SocialPreferenceScore.STRONG_REJECTION || minusTwoCount < 2,
                                activeColor = Color(0xFFEF4444),
                                onClick = { onVoteChanged(classmate.id, SocialPreferenceScore.STRONG_REJECTION) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(AppTheme.Space12))

        Button(
            onClick = onSubmitVotes,
            enabled = missingVotes == 0,
            shape = RoundedCornerShape(AppTheme.ButtonCornerRadius),
            colors = ButtonDefaults.buttonColors(containerColor = AppTheme.PrimaryBlue),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (missingVotes == 0) "Salva preferenze" else "Vota ancora $missingVotes ${if (missingVotes == 1) "compagno" else "compagni"}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun RowScope.SocialScoreButton(
    label: String,
    isSelected: Boolean,
    enabled: Boolean = true,
    activeColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetBgColor = when {
        isSelected -> activeColor
        !enabled -> AppTheme.TintSlate
        else -> AppTheme.SurfaceWhite
    }

    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(200),
        label = "socialBtnBg"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else AppTheme.Hairline,
        animationSpec = tween(200),
        label = "socialBtnBorder"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.93f else if (isSelected) 1.04f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "socialBtnScale"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .scale(animatedScale)
            .clip(RoundedCornerShape(AppTheme.SmallElementRadius))
            .background(animatedBgColor)
            .border(1.dp, animatedBorderColor, RoundedCornerShape(AppTheme.SmallElementRadius))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else if (!enabled) AppTheme.TextFaint else AppTheme.TextDark
        )
    }
}
