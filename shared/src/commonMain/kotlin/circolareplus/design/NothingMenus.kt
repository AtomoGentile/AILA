package circolareplus.design

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import circolareplus.util.*

internal val LocalNothingConfirmButton = staticCompositionLocalOf { false }

@Composable
internal fun NothingMenuRail(modifier: Modifier = Modifier) {
    Canvas(modifier.width(54.dp).height(10.dp)) {
        repeat(9) { i -> drawCircle(if(i == 0) AppTheme.PrimaryBlue else AppTheme.TextMuted,
            1.5.dp.toPx(), Offset(size.width*(i+.5f)/9,size.height/2)) }
    }
}

/** Bounded command panel; forms retain their own scrolling and keyboard behaviour. */
@Composable
internal fun NothingPanelDialog(onDismiss: () -> Unit, modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(), title: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null, actions: @Composable ColumnScope.() -> Unit,
    body: (@Composable () -> Unit)? = null) {
    val reveal=remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f,tween(if(AppTheme.reduceMotion)0 else 160)) }
    Dialog(onDismiss, properties = properties) {
        BoxWithConstraints(Modifier.fillMaxWidth().imePadding()) {
            Column(modifier.align(Alignment.Center).widthIn(max = 440.dp).fillMaxWidth().heightIn(max = (maxHeight-32.dp).coerceAtLeast(120.dp))
                .graphicsLayer { alpha=reveal.value;translationY=if(AppTheme.reduceMotion)0f else (1f-reveal.value)*12.dp.toPx() }
                .clip(RoundedCornerShape(28.dp)).background(AppTheme.SurfaceWhite)
                .border(1.dp,AppTheme.FieldOutline,RoundedCornerShape(28.dp)).padding(NothingSpacing.Edge),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                NothingMenuRail()
                val type=MaterialTheme.typography
                MaterialTheme(typography=type.copy(titleLarge=nothingDisplayStyle(24,32))) {
                CompositionLocalProvider(LocalContentColor provides AppTheme.TextDark) {
                    icon?.invoke()
                    if(title != null) ProvideTextStyle(nothingDisplayStyle(24,32),content = title)
                    if(body != null) Column(Modifier.weight(1f,fill=false)) {
                        ProvideTextStyle(MaterialTheme.typography.bodyLarge, content = body)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(AppTheme.Hairline))
                    actions()
                }
                }
            }
        }
    }
}

@Composable
fun NothingChoiceRow(label: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val ink = if(selected) AppTheme.BackgroundLight else AppTheme.TextDark
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(if(selected) AppTheme.TextDark else AppTheme.TrackFill)
        .selectable(selected,enabled=enabled,role=Role.RadioButton,onClick=onClick)
        .heightIn(min=56.dp).padding(horizontal=NothingSpacing.Content,vertical=NothingSpacing.Content),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(NothingSpacing.Item)) {
        Canvas(Modifier.size(16.dp)) {
            repeat(4) { i -> drawCircle(if(selected) AppTheme.PrimaryBlue else AppTheme.TextMuted,
                1.8.dp.toPx(),Offset(size.width*(if(i%2==0).25f else .75f),size.height*(if(i<2).25f else .75f))) }
        }
        Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,
            color=if(enabled)ink else AppTheme.TextFaint)
        if(selected) AppIcons.Check(Modifier.size(18.dp),ink)
    }
}

private object NothingMenuPosition : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect,windowSize: IntSize,
        layoutDirection: LayoutDirection,popupContentSize: IntSize): IntOffset {
        val x = if(layoutDirection == LayoutDirection.Ltr) anchorBounds.left else anchorBounds.right-popupContentSize.width
        val below=anchorBounds.bottom
        val y=if(below+popupContentSize.height<=windowSize.height) below else anchorBounds.top-popupContentSize.height
        return IntOffset(x.coerceIn(0,(windowSize.width-popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0,(windowSize.height-popupContentSize.height).coerceAtLeast(0)))
    }
}

@Composable
fun AilaDropdownMenu(expanded:Boolean,onDismissRequest:()->Unit,modifier:Modifier=Modifier,
    content:@Composable ColumnScope.()->Unit) {
    if(!AppTheme.isNothing) { DropdownMenu(expanded,onDismissRequest,modifier,content=content); return }
    val visible=remember { MutableTransitionState(false) }
    LaunchedEffect(expanded) { visible.targetState=expanded }
    if(visible.currentState || visible.targetState || !visible.isIdle) {
        Popup(popupPositionProvider=NothingMenuPosition,onDismissRequest=onDismissRequest,properties=PopupProperties(focusable=true)) {
            AnimatedVisibility(visible,enter=if(AppTheme.reduceMotion)fadeIn(tween(0)) else fadeIn(tween(130))+slideInVertically(tween(130)){ -it/15 },
                exit=fadeOut(tween(if(AppTheme.reduceMotion)0 else 100))) {
                Column(modifier.widthIn(min=224.dp,max=320.dp).clip(RoundedCornerShape(20.dp))
                    .background(AppTheme.SurfaceWhite).border(1.dp,AppTheme.FieldOutline,RoundedCornerShape(20.dp)).padding(8.dp)) {
                    NothingMenuRail(Modifier.padding(start=NothingSpacing.Item,top=NothingSpacing.Inline,bottom=NothingSpacing.Inline))
                    Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),content=content)
                }
            }
        }
    }
}

@Composable
fun AilaDropdownMenuItem(text:@Composable ()->Unit,onClick:()->Unit,modifier:Modifier=Modifier,
    leadingIcon:(@Composable ()->Unit)?=null,trailingIcon:(@Composable ()->Unit)?=null,enabled:Boolean=true) {
    if(!AppTheme.isNothing) { DropdownMenuItem(text,onClick,modifier,leadingIcon,trailingIcon,enabled); return }
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled=enabled,role=Role.Button,onClick=onClick)
        .heightIn(min=52.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(5.dp).background(AppTheme.PrimaryBlue,CircleShape))
        leadingIcon?.invoke()
        CompositionLocalProvider(LocalContentColor provides if(enabled)AppTheme.TextDark else AppTheme.TextFaint) {
            Box(Modifier.weight(1f)) { ProvideTextStyle(MaterialTheme.typography.bodyLarge,content=text) }
            trailingIcon?.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NothingDatePicker(state:DatePickerState,onDismiss:()->Unit,confirmLabel:String,onConfirm:()->Unit) {
    var years by remember { mutableStateOf(false) }
    val month=civilFromEpochMillis(state.displayedMonthMillis)
    val chosen=state.selectedDateMillis?.let(::civilFromEpochMillis)
    fun millis(d:CivilDate)=daysBetween(CivilDate(1970,1,1),d)*86_400_000L
    fun showMonth(year:Int,m:Int) { state.displayedMonthMillis=millis(CivilDate(year,m,1)); years=false }
    NothingPanelDialog(onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false),
        modifier=Modifier.padding(horizontal=16.dp),title={ Text("Scegli data") },actions={
            AilaActionButton(onClick=onConfirm,enabled=state.selectedDateMillis!=null,modifier=Modifier.fillMaxWidth(),
                colors=ButtonDefaults.buttonColors(containerColor=AppTheme.PrimaryBlue,contentColor=if(AppTheme.isDarkMode)androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White)) { Text(confirmLabel) }
            AilaSecondaryButton("Annulla",onDismiss,modifier=Modifier.fillMaxWidth())
        },body={
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(chosen?.let { "${it.day} ${ITALIAN_MONTHS[it.month]} ${it.year}" } ?: "Nessuna data selezionata",
                    style=MaterialTheme.typography.bodyLarge,color=AppTheme.TextMuted)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    AilaIconControl(onClick={val (y,m)=previousMonth(month.year,month.month);showMonth(y,m)},enabled=month.year>state.yearRange.first || month.month>1,modifier=Modifier.semantics { contentDescription="Mese precedente" }) {
                        AppIcons.ChevronLeft(Modifier.size(18.dp),AppTheme.TextDark)
                    }
                    Row(Modifier.weight(1f).clip(CircleShape)
                        .clickable(role=Role.Button){years=!years}.semantics { contentDescription="Scegli anno" }.padding(8.dp),
                        verticalAlignment=Alignment.CenterVertically) {
                        Text("${ITALIAN_MONTHS[month.month]} ${month.year}",style=MaterialTheme.typography.titleMedium,
                            modifier=Modifier.weight(1f),color=AppTheme.TextDark)
                        AppIcons.ChevronRight(Modifier.size(14.dp).rotate(90f),AppTheme.TextMuted)
                    }
                    AilaIconControl(onClick={val (y,m)=nextMonth(month.year,month.month);showMonth(y,m)},enabled=month.year<state.yearRange.last || month.month<12,modifier=Modifier.semantics { contentDescription="Mese successivo" }) {
                        AppIcons.ChevronRight(Modifier.size(18.dp),AppTheme.TextDark)
                    }
                }
                if(years) {
                    val rows=state.yearRange.toList().chunked(3)
                    val scroll=rememberLazyListState(((month.year-state.yearRange.first)/3-2).coerceAtLeast(0))
                    LazyColumn(Modifier.height(280.dp),state=scroll,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        items(rows) { row -> Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { row.forEach { year ->
                            Box(Modifier.weight(1f).height(48.dp).clip(CircleShape)
                                .background(if(month.year==year)AppTheme.TextDark else AppTheme.TrackFill)
                                .selectable(month.year==year,enabled=state.selectableDates.isSelectableYear(year),role=Role.RadioButton){showMonth(year,month.month)},contentAlignment=Alignment.Center) {
                                Text("$year",color=if(month.year==year)AppTheme.BackgroundLight else AppTheme.TextDark)
                            }
                        };repeat(3-row.size){Spacer(Modifier.weight(1f))} } }
                    }
                } else Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                    Row { ITALIAN_WEEKDAY_INITIALS.forEach { day -> Box(Modifier.weight(1f).height(32.dp),contentAlignment=Alignment.Center) {
                        Text(day,style=MaterialTheme.typography.labelMedium,color=AppTheme.TextMuted)
                    } } }
                    val start=firstWeekdayOfMonth(month.year,month.month)
                    val count=daysInMonth(month.year,month.month)
                    repeat(6) { week -> Row {
                        repeat(7) { col ->
                            val day=week*7+col-start+1
                            val date=CivilDate(month.year,month.month,day)
                            val valid=day in 1..count
                            val selected=valid && date==chosen
                            val enabled=valid && state.selectableDates.isSelectableYear(month.year) && state.selectableDates.isSelectableDate(millis(date))
                            Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(CircleShape)
                                .background(if(selected)AppTheme.TextDark else androidx.compose.ui.graphics.Color.Transparent)
                                .then(if(valid)Modifier.selectable(selected,enabled,Role.RadioButton){state.selectedDateMillis=millis(date)}
                                    .semantics { contentDescription="$day ${ITALIAN_MONTHS[month.month]} ${month.year}" } else Modifier),contentAlignment=Alignment.Center) {
                                if(valid) Text("$day",style=MaterialTheme.typography.bodyLarge,
                                    color=if(!enabled)AppTheme.TextFaint else if(selected)AppTheme.BackgroundLight else AppTheme.TextDark)
                            }
                        }
                    } }
                }
            }
        })
}
