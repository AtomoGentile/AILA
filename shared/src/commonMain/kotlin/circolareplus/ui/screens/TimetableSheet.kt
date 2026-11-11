package circolareplus.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import circolareplus.design.LocalAilaSheetReveal
import circolareplus.design.ailaSheetReveal
import circolareplus.design.ailaNavigationSpring
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
    val uriHandler = LocalUriHandler.current
    val setTabBarVisible = circolareplus.design.LocalFloatingTabBarVisibility.current
    DisposableEffect(Unit) {
        setTabBarVisible(false)
        onDispose { setTabBarVisible(true) }
    }
    LaunchedEffect(selectedDay) {
        // Keep the selected day visible even on narrow screens and with larger font sizes.
        if (AppTheme.reduceMotion) dayScroll.scrollToItem(selectedDay)
        else dayScroll.animateScrollToItem(selectedDay)
    }

    AilaOriginSheet(
        originKey = "class-timetable", onDismiss = onDismiss,
        fromBottom = true, useOrigin = false
    ) { close ->
        CompositionLocalProvider(LocalAilaSheetReveal provides true) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(Modifier.fillMaxWidth().ailaSheetReveal(0), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Orario", style = MaterialTheme.typography.titleLarge, color = AppTheme.TextDark)
                        Text("${SchoolTimetable.normalizeClass(classLabel)} · A.S. ${SchoolTimetable.SCHOOL_YEAR}",
                            style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                    }
                    AilaIconButton(contentDescription = "Chiudi orario", onClick = close) { tint ->
                        AppIcons.Close(Modifier.size(18.dp), color = tint)
                    }
                }
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    state = dayScroll,
                    modifier = Modifier.fillMaxWidth().ailaSheetReveal(1).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(ITALIAN_WEEKDAYS.size) { index ->
                        val isSelected = index == selectedDay
                        val dayBackground by animateColorAsState(
                            if (isSelected) AppTheme.TintBlue else AppTheme.FieldSurface,
                            animationSpec = circolareplus.design.ailaNavigationSpring(), label = "timetableDaySelection")
                        val fullName = ITALIAN_WEEKDAYS[index].replaceFirstChar { it.uppercase() }
                        Column(
                            Modifier.widthIn(min = 44.dp).clip(RoundedCornerShape(14.dp))
                                .background(dayBackground)
                                .selectable(selected = isSelected, role = Role.Tab, onClick = { selectedDay = index })
                                .semantics { contentDescription = if (index == currentDay) "$fullName, oggi" else fullName }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
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
                AnimatedContent(
                    targetState = selectedDay,
                    transitionSpec = {
                        val direction = if (targetState > initialState) 1 else -1
                        val enter = fadeIn(tween(180))
                        val exit = fadeOut(tween(120))
                        if (AppTheme.reduceMotion) {
                            (enter togetherWith exit).using(SizeTransform { _, _ -> snap() })
                        } else {
                            ((enter + slideInHorizontally(ailaNavigationSpring()) { direction * it / 8 }) togetherWith
                                (exit + slideOutHorizontally(ailaNavigationSpring()) { -direction * it / 8 }))
                                .using(SizeTransform { _, _ -> ailaNavigationSpring() })
                        }
                    },
                    modifier = Modifier.weight(1f, fill = false).fillMaxWidth().ailaSheetReveal(2),
                    label = "timetableDay"
                ) { day ->
                    val lessons = SchoolTimetable.day(classLabel, day)
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(ITALIAN_WEEKDAYS[day].replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                                    color = AppTheme.TextDark)
                                if (lessons.isNotEmpty()) Text(
                                    "${lessons.sumOf { it.toHour - it.fromHour + 1 }} ore",
                                    style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted)
                            }
                            if (day == currentDay) {
                                Text("Oggi", style = MaterialTheme.typography.labelMedium, color = AppTheme.PrimaryBlue,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(AppTheme.TintBlue)
                                        .padding(horizontal = 12.dp, vertical = 6.dp))
                            } else {
                                TextButton(onClick = { selectedDay = currentDay }) { Text("Oggi") }
                            }
                        }
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                            if (lessons.isEmpty()) {
                                Text("Nessuna lezione prevista.", style = MaterialTheme.typography.bodyMedium,
                                    color = AppTheme.TextMuted, modifier = Modifier.padding(vertical = 18.dp))
                            } else lessons.forEachIndexed { index, lesson ->
                                TimetableLessonRow(lesson)
                                if (index != lessons.lastIndex) HorizontalDivider(
                                    color = AppTheme.FieldOutline.copy(alpha = 0.55f), thickness = 0.7.dp)
                            }
                        }
                    }
                }
                HorizontalDivider(color = AppTheme.FieldOutline)
                Text("Orario di base · possibili variazioni nelle comunicazioni scolastiche.",
                    style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted,
                    modifier = Modifier.padding(top = 8.dp).ailaSheetReveal(3))
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
}

@Composable
private fun TimetableLessonRow(lesson: TimetableLesson) {
    val subject = if (lesson.subject.length <= 4) lesson.subject else
        lesson.subject.lowercase().replaceFirstChar { it.uppercase() }
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$subject, ${lesson.timeLabel}" }
            .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(if (lesson.fromHour == lesson.toHour) "${lesson.fromHour}ª" else "${lesson.fromHour}ª–${lesson.toHour}ª",
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            color = AppTheme.PrimaryBlue, modifier = Modifier.widthIn(min = 48.dp))
        Text(subject, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = AppTheme.TextDark, modifier = Modifier.weight(1f))
    }
}
