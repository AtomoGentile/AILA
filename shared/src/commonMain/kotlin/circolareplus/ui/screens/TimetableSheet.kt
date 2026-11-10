package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import circolareplus.data.SchoolTimetable
import circolareplus.data.TimetableLesson
import circolareplus.design.AilaIconButton
import circolareplus.design.AilaOriginSheet
import circolareplus.design.AppIcons
import circolareplus.design.AppTheme
import circolareplus.util.ITALIAN_WEEKDAYS
import circolareplus.util.today
import circolareplus.util.weekdayOf

/** Read-only weekly baseline, one day at a time; never infers clock times or substitutions. */
@Composable
internal fun TimetableSheet(classLabel: String, onDismiss: () -> Unit, initialDay: Int? = null) {
    val currentDay = remember { weekdayOf(today()) }
    var selectedDay by rememberSaveable(classLabel, initialDay) { mutableStateOf(initialDay ?: currentDay) }
    val dayScroll = rememberLazyListState(initialFirstVisibleItemIndex = initialDay ?: currentDay)
    val lessonScroll = rememberLazyListState()
    val uriHandler = LocalUriHandler.current
    val lessons = SchoolTimetable.day(classLabel, selectedDay)
    LaunchedEffect(selectedDay) {
        lessonScroll.scrollToItem(0)
        // Keep the selected day visible even on narrow screens and with larger font sizes.
        dayScroll.scrollToItem(selectedDay)
    }

    AilaOriginSheet(originKey = "class-timetable", onDismiss = onDismiss) { close ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Orario", style = MaterialTheme.typography.titleLarge, color = AppTheme.TextDark)
                    Text("${SchoolTimetable.normalizeClass(classLabel)} · A.S. ${SchoolTimetable.SCHOOL_YEAR}",
                        style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                }
                AilaIconButton(contentDescription = "Chiudi orario", onClick = close) { tint ->
                    AppIcons.Close(Modifier.size(18.dp), color = tint)
                }
            }
            Spacer(Modifier.height(20.dp))
            LazyRow(
                state = dayScroll,
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(ITALIAN_WEEKDAYS.size) { index ->
                    val isSelected = index == selectedDay
                    val fullName = ITALIAN_WEEKDAYS[index].replaceFirstChar { it.uppercase() }
                    Column(
                        Modifier.widthIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) AppTheme.TintBlue else AppTheme.FieldSurface)
                            .selectable(selected = isSelected, role = Role.Tab, onClick = { selectedDay = index })
                            .semantics { contentDescription = if (index == currentDay) "$fullName, oggi" else fullName }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(fullName.take(3), style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) AppTheme.PrimaryBlue else AppTheme.TextMuted)
                        Box(Modifier.size(4.dp).clip(RoundedCornerShape(50))
                            .background(if (index == currentDay) AppTheme.PrimaryBlue else androidx.compose.ui.graphics.Color.Transparent))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(ITALIAN_WEEKDAYS[selectedDay].replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge, color = AppTheme.TextDark)
                    Text(if (lessons.isEmpty()) "Nessuna lezione prevista" else
                        "${lessons.sumOf { it.toHour - it.fromHour + 1 }} ore di lezione",
                        style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                }
                if (selectedDay == currentDay) {
                    Text("Oggi", style = MaterialTheme.typography.labelMedium, color = AppTheme.PrimaryBlue,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(AppTheme.TintBlue)
                            .padding(horizontal = 12.dp, vertical = 6.dp))
                } else {
                    TextButton(onClick = { selectedDay = currentDay }) { Text("Oggi") }
                }
            }
            LazyColumn(
                state = lessonScroll,
                modifier = Modifier.weight(1f, fill = false).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (lessons.isEmpty()) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppTheme.FieldSurface)
                                .padding(horizontal = 20.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AppIcons.Calendar(Modifier.size(28.dp), color = AppTheme.TextMuted)
                            Text("Il prospetto settimanale non prevede lezioni per questo giorno.",
                                style = MaterialTheme.typography.bodyMedium, color = AppTheme.TextMuted)
                        }
                    }
                } else items(lessons, key = { it.fromHour }) { lesson -> TimetableLessonRow(lesson) }
            }
            HorizontalDivider(color = AppTheme.FieldOutline)
            Text("Schema di base: verifica festività e variazioni nelle comunicazioni della scuola.",
                style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted,
                modifier = Modifier.padding(top = 12.dp))
            TextButton(
                onClick = { uriHandler.openUri(SchoolTimetable.SOURCE_URL) },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text("Apri orario ufficiale")
                Spacer(Modifier.width(8.dp))
                AppIcons.ExternalLink(Modifier.size(14.dp), color = AppTheme.PrimaryBlue)
            }
        }
    }
}

@Composable
private fun TimetableLessonRow(lesson: TimetableLesson) {
    val subject = if (lesson.subject.length <= 4) lesson.subject else
        lesson.subject.lowercase().replaceFirstChar { it.uppercase() }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppTheme.FieldSurface)
            .clearAndSetSemantics { contentDescription = "$subject, ${lesson.timeLabel}" }
            .heightIn(min = 76.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            Modifier.width(56.dp).clip(RoundedCornerShape(12.dp)).background(AppTheme.TintBlue)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(if (lesson.fromHour == lesson.toHour) "${lesson.fromHour}ª" else "${lesson.fromHour}–${lesson.toHour}",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = AppTheme.PrimaryBlue)
            Text(if (lesson.fromHour == lesson.toHour) "ORA" else "ORE", style = MaterialTheme.typography.labelSmall,
                color = AppTheme.TextMuted)
        }
        Text(subject, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
            color = AppTheme.TextDark, modifier = Modifier.weight(1f))
    }
}
