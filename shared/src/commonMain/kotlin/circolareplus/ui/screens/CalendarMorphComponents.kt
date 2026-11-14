package circolareplus.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import circolareplus.data.SchoolTimetable
import circolareplus.design.*
import circolareplus.util.*

/** Only this 48dp strip claims vertical drags; the grid and event scroll never do. */
@Composable
internal fun CalendarMorphHandle(state: CalendarMorphState) {
    val scope = rememberCoroutineScope()
    val distance = with(LocalDensity.current) { 220.dp.toPx() }
    val haptic = LocalHapticFeedback.current
    val reduced by rememberUpdatedState(AppTheme.reduceMotion)
    val description = if (state.timetable) "Torna al calendario" else "Apri orario della classe"
    Box(
        Modifier.fillMaxWidth().height(48.dp)
            .semantics {
                contentDescription = description
                stateDescription = if (state.timetable) "Orario" else "Calendario"
                role = Role.Button
            }
            .clickable { state.settle(scope, !state.timetable, reduced) }
            .pointerInput(state, distance) {
                val tracker = VelocityTracker()
                var crossed = false
                var draggedY = 0f
                detectVerticalDragGestures(
                    onDragStart = {
                        state.beginDrag()
                        tracker.resetTracking()
                        draggedY = 0f
                        crossed = state.progress >= 0.5f
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        draggedY += amount
                        tracker.addPosition(change.uptimeMillis, Offset(0f, draggedY))
                        state.drag(amount / distance)
                        val above = state.progress >= 0.5f
                        if (above != crossed) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            crossed = above
                        }
                    },
                    onDragEnd = { state.finish(scope, tracker.calculateVelocity().y / distance, reduced) },
                    onDragCancel = { state.finish(scope, 0f, reduced, cancelled = true) }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.width((34f + 8f * state.progress).dp).height(4.dp).clip(RoundedCornerShape(50)).background(androidx.compose.ui.graphics.lerp(AppTheme.PrimaryBlue, AppTheme.AccentCyan, state.progress)))
    }
}

/** Measure at full size, then progressively reclaim space without scaling text. */
internal fun Modifier.morphCollapse(visible: Float): Modifier =
    clipToBounds().graphicsLayer { alpha = visible.coerceIn(0f, 1f) }
        .morphInactive(visible < 1f)
        .layout { measurable, constraints ->
            val child = measurable.measure(constraints.copy(minHeight = 0))
            layout(child.width, (child.height * visible.coerceIn(0f, 1f)).toInt()) { child.place(0, 0) }
        }

internal fun Modifier.morphInactive(inactive: Boolean): Modifier = if (!inactive) this else
    clearAndSetSemantics { }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

@Composable
internal fun TimetableTimeline(classLabel: String?, date: CivilDate, progress: Float, subjectColorsRevision: Int) {
    val uri = LocalUriHandler.current
    val subjectColorsEnabled = remember(subjectColorsRevision) { circolareplus.data.AppContainer.settings.subjectColorsEnabled }
    val subjectColorOverrides = remember(subjectColorsRevision) { circolareplus.data.AppContainer.settings.subjectColorOverrides }
    AnimatedContent(
        targetState = date.toIso(),
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            val enter = fadeIn(ailaFadeSpec(160))
            val exit = fadeOut(ailaFadeSpec(100))
            if (AppTheme.reduceMotion) enter togetherWith exit
            else (enter + slideInHorizontally(ailaNavigationSpring()) { direction * it / 10 }) togetherWith
                (exit + slideOutHorizontally(ailaNavigationSpring()) { -direction * it / 10 })
        }, label = "timetable-date"
    ) { iso ->
        val lessons = remember(classLabel, iso) { SchoolTimetable.lessons(classLabel, iso) }
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (lessons.isEmpty()) {
                Text(if (SchoolTimetable.week(classLabel) == null) "Orario non disponibile per questa classe"
                    else "Nessuna lezione prevista per questa data",
                    style = MaterialTheme.typography.bodyLarge, color = AppTheme.TextMuted,
                    modifier = Modifier.padding(vertical = 24.dp))
            }
            lessons.forEachIndexed { index, lesson ->
                val subjectColor = if (subjectColorsEnabled) SubjectColors.choiceForSubject(lesson.subject, subjectColorOverrides) else null
                val markerBrush = subjectColor?.let { Brush.verticalGradient(it.colors) }
                    ?: Brush.verticalGradient(listOf(AppTheme.AccentCyan))
                val lineBrush = subjectColor?.let { Brush.verticalGradient(it.colors.map { color -> color.copy(alpha = 0.42f) }) }
                    ?: Brush.verticalGradient(listOf(AppTheme.AccentCyan.copy(alpha = 0.35f)))
                val reveal = ((progress - 0.18f - index * 0.055f) / 0.38f).coerceIn(0f, 1f)
                Row(
                    Modifier.fillMaxWidth().graphicsLayer {
                        alpha = reveal
                        translationY = if (AppTheme.reduceMotion) 0f else (1f - reveal) * 18.dp.toPx()
                    }.heightIn(min = 76.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (lesson.fromHour == lesson.toHour) "${lesson.fromHour}ª" else "${lesson.fromHour}ª–${lesson.toHour}ª",
                        style = MaterialTheme.typography.labelLarge, color = AppTheme.AccentCyan,
                        modifier = Modifier.widthIn(min = 52.dp))
                    Box(Modifier.width(16.dp).height(76.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(lineBrush))
                        Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(markerBrush).border(1.dp, AppTheme.Hairline, RoundedCornerShape(50)))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp)) {
                        Text(if (lesson.subject.length <= 4) lesson.subject else lesson.subject.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium, color = AppTheme.TextDark)
                        Text(lesson.timeLabel, style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted)
                    }
                }
            }
            Text("${classLabel.orEmpty()} · A.S. ${SchoolTimetable.SCHOOL_YEAR}",
                style = MaterialTheme.typography.labelSmall, color = AppTheme.TextMuted,
                modifier = Modifier.padding(top = 12.dp))
            Text("Orario di base · possibili variazioni nelle comunicazioni scolastiche. Le ore indicate non sono fasce dell’orologio.",
                style = MaterialTheme.typography.bodySmall, color = AppTheme.TextMuted,
                modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = { uri.openUri(SchoolTimetable.SOURCE_URL) }) { Text("Apri orario ufficiale", color = AppTheme.AccentCyan) }
        }
    }
}

/** The outgoing event pane retains its width while the calendar grows across the tablet. */
@Composable
internal fun CalendarMorphWideLayout(progress: Float, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.clipToBounds()) { measurables, constraints ->
        val gap = 4.dp.roundToPx()
        val half = (constraints.maxWidth - gap) / 2
        val mainWidth = (half + (constraints.maxWidth - half) * progress).toInt()
        val main = measurables[0].measure(constraints.copy(minWidth = mainWidth, maxWidth = mainWidth))
        val events = measurables[1].measure(constraints.copy(minWidth = half, maxWidth = half))
        layout(constraints.maxWidth, constraints.maxHeight) {
            main.placeRelative(0, 0)
            events.placeRelative(mainWidth + gap, 0)
        }
    }
}

/** Preserve the existing Material/Glass surface at rest; its background alone recedes. */
@Composable
internal fun CalendarMorphSurface(progress: Float, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(AppTheme.CardCornerRadius * (1f - progress))
    val tint = if (AppTheme.isDarkMode) androidx.compose.ui.graphics.Color(0xFFB8C8E8).copy(alpha = 0.075f)
        else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.13f)
    Box(Modifier.fillMaxWidth().clip(shape)) {
        Box(Modifier.matchParentSize().graphicsLayer { alpha = 1f - progress }
            .then(if (AppTheme.isGlass) Modifier.ailaGlassSurface(shape, tint = tint)
                else Modifier.background(AppTheme.CardSurface)))
        Column(Modifier.fillMaxWidth(), content = content)
    }
}
