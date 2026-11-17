package circolareplus.design

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

@Composable
fun Modifier.nothingPressFeedback(source: MutableInteractionSource): Modifier {
    val pressed by source.collectIsPressedAsState()
    val opacity by animateFloatAsState(if (pressed) .76f else 1f,
        animationSpec = tween(if (AppTheme.reduceMotion) 0 else if(pressed) 70 else 150), label = "nothingPress")
    return graphicsLayer { alpha = opacity }
}

@Composable
private fun NothingButtonSurface(
    onClick: () -> Unit, modifier: Modifier, enabled: Boolean, outlined: Boolean,
    fill: Color, ink: Color, padding: PaddingValues, source: MutableInteractionSource?,
    content: @Composable RowScope.() -> Unit
) {
    val interactions = source ?: remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)
    Box(modifier.defaultMinSize(minWidth = 48.dp, minHeight = 44.dp).clip(shape)
        .background(fill).then(if(outlined) Modifier.border(1.dp,AppTheme.FieldOutline,shape) else Modifier)
        .nothingPressFeedback(interactions)
        .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(padding), contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides if(enabled) ink else AppTheme.TextMuted) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}

@Composable
fun AilaTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(AppTheme.ButtonCornerRadius), colors: ButtonColors? = null, elevation: ButtonElevation? = null,
    border: BorderStroke? = null, contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    if (!AppTheme.isNothing) {
        TextButton(onClick=onClick,modifier=modifier,enabled=enabled,shape=shape,colors=colors ?: ButtonDefaults.textButtonColors(),elevation=elevation,border=border,contentPadding=contentPadding,interactionSource=interactionSource,content=content)
    } else {
        val command=LocalNothingConfirmButton.current
        NothingButtonSurface(onClick,if(command)modifier.fillMaxWidth() else modifier,enabled,command,
            Color.Transparent,colors?.contentColor ?: AppTheme.TextDark,contentPadding,interactionSource) {
            if(command) { Box(Modifier.size(5.dp).background(AppTheme.PrimaryBlue,CircleShape));Spacer(Modifier.width(10.dp)) }
            content()
        }
    }
}

@Composable
fun AilaActionButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(AppTheme.ButtonCornerRadius), colors: ButtonColors? = null, elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null, contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    if (!AppTheme.isNothing) Button(onClick=onClick,modifier=modifier,enabled=enabled,shape=shape,colors=colors ?: ButtonDefaults.buttonColors(),elevation=elevation,border=border,contentPadding=contentPadding,interactionSource=interactionSource,content=content)
    else NothingButtonSurface(onClick,modifier,enabled,false,
        if(enabled) colors?.containerColor ?: AppTheme.TextDark else AppTheme.TrackFill,
        colors?.contentColor ?: AppTheme.BackgroundLight,contentPadding,interactionSource,content)
}

@Composable
fun AilaOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(AppTheme.ButtonCornerRadius), colors: ButtonColors? = null, elevation: ButtonElevation? = null,
    border: BorderStroke? = null, contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null, content: @Composable RowScope.() -> Unit) {
    if (!AppTheme.isNothing) OutlinedButton(onClick=onClick,modifier=modifier,enabled=enabled,shape=shape,colors=colors ?: ButtonDefaults.outlinedButtonColors(),elevation=elevation,border=border ?: ButtonDefaults.outlinedButtonBorder(enabled),contentPadding=contentPadding,interactionSource=interactionSource,content=content)
    else NothingButtonSurface(onClick,modifier,enabled,true,AppTheme.SurfaceWhite,colors?.contentColor ?: AppTheme.TextDark,contentPadding,interactionSource,content)
}

@Composable
fun AilaIconControl(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    colors: IconButtonColors? = null, interactionSource: MutableInteractionSource? = null, content: @Composable () -> Unit) {
    if (!AppTheme.isNothing) IconButton(onClick=onClick,modifier=modifier,enabled=enabled,colors=colors ?: IconButtonDefaults.iconButtonColors(),interactionSource=interactionSource,content=content)
    else {
        val source = interactionSource ?: remember { MutableInteractionSource() }
        Box(modifier.defaultMinSize(minWidth = 44.dp,minHeight = 44.dp).clip(CircleShape)
            .background(colors?.containerColor ?: Color.Transparent)
            .nothingPressFeedback(source).clickable(interactionSource = source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor provides if(enabled) colors?.contentColor ?: AppTheme.TextDark else AppTheme.TextMuted, content = content)
        }
    }
}

@Composable
fun NothingToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val position by animateDpAsState(if(checked) 28.dp else 4.dp, tween(if(AppTheme.reduceMotion) 0 else 160), label = "nothingToggle")
    Box(modifier.size(56.dp,48.dp).toggleable(checked,enabled = enabled,role = Role.Switch,onValueChange = onChange), contentAlignment = Alignment.Center) {
        Box(Modifier.size(56.dp,32.dp).clip(CircleShape).background(if(checked) AppTheme.TextDark else AppTheme.TrackFill)
            .border(1.dp,if(checked) AppTheme.TextDark else AppTheme.FieldOutline,CircleShape)) {
            Box(Modifier.offset(x = position,y = 4.dp).size(24.dp).background(if(checked) AppTheme.BackgroundLight else AppTheme.TextMuted,CircleShape), contentAlignment = Alignment.Center) {
                if(checked) Box(Modifier.size(4.dp).background(AppTheme.PrimaryBlue,CircleShape))
            }
        }
    }
}

@Composable
fun AilaCheckbox(checked: Boolean,onCheckedChange: ((Boolean) -> Unit)?,modifier: Modifier = Modifier,
    enabled: Boolean = true, colors: CheckboxColors? = null, interactionSource: MutableInteractionSource? = null) {
    if(!AppTheme.isNothing) Checkbox(checked,onCheckedChange,modifier,enabled,colors ?: CheckboxDefaults.colors(),interactionSource)
    else Box(modifier.size(48.dp).then(if(onCheckedChange != null) Modifier.toggleable(checked,enabled = enabled,role = Role.Checkbox,onValueChange = onCheckedChange) else Modifier.semantics { toggleableState = if(checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off }),contentAlignment = Alignment.Center) {
        Box(Modifier.size(22.dp).background(if(checked) AppTheme.TextDark else Color.Transparent,RoundedCornerShape(5.dp))
            .border(1.5.dp,if(checked) AppTheme.TextDark else AppTheme.TextMuted,RoundedCornerShape(5.dp)),contentAlignment = Alignment.Center) {
            if(checked) AppIcons.Check(Modifier.size(14.dp),AppTheme.BackgroundLight)
        }
    }
}

@Composable
fun AilaRadioButton(selected: Boolean,onClick: (() -> Unit)?,modifier: Modifier = Modifier,enabled: Boolean = true,
    colors: RadioButtonColors? = null, interactionSource: MutableInteractionSource? = null) {
    if(!AppTheme.isNothing) RadioButton(selected,onClick,modifier,enabled,colors ?: RadioButtonDefaults.colors(),interactionSource)
    else Box(modifier.size(48.dp).then(if(onClick != null) Modifier.selectable(selected,enabled = enabled,role = Role.RadioButton,onClick = onClick) else Modifier.semantics { this.selected = selected }),contentAlignment = Alignment.Center) {
        Box(Modifier.size(22.dp).border(1.5.dp,if(selected) AppTheme.TextDark else AppTheme.TextMuted,CircleShape),contentAlignment = Alignment.Center) {
            if(selected) Box(Modifier.size(10.dp).background(AppTheme.TextDark,CircleShape))
        }
    }
}

@Composable
fun AilaSlider(value: Float,onValueChange: (Float) -> Unit,modifier: Modifier = Modifier,enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,steps: Int = 0,onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors? = null,interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }) {
    if(!AppTheme.isNothing) Slider(value=value,onValueChange=onValueChange,modifier=modifier,enabled=enabled,valueRange=valueRange,steps=steps,onValueChangeFinished=onValueChangeFinished,colors=colors ?: SliderDefaults.colors(),interactionSource=interactionSource)
    else {
        val changed by rememberUpdatedState(onValueChange)
        val finished by rememberUpdatedState(onValueChangeFinished)
        val range = (valueRange.endInclusive-valueRange.start).coerceAtLeast(.0001f)
        fun snap(v:Float):Float {
            val t=((v-valueRange.start)/range).coerceIn(0f,1f)
            return valueRange.start+(if(steps>0) (t*(steps+1)).roundToInt().toFloat()/(steps+1) else t)*range
        }
        Canvas(modifier.fillMaxWidth().height(48.dp).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(value,valueRange,steps)
            if(enabled) setProgress { changed(snap(it)); finished?.invoke(); true }
        }.pointerInput(enabled,valueRange,steps) {
            if(enabled) detectTapGestures { pos -> changed(snap(valueRange.start+((pos.x-12.dp.toPx())/(size.width-24.dp.toPx()).coerceAtLeast(1f))*range)); finished?.invoke() }
        }.pointerInput(enabled,valueRange,steps) {
            if(enabled) detectHorizontalDragGestures(onHorizontalDrag = { change,_ ->
                change.consume();changed(snap(valueRange.start+((change.position.x-12.dp.toPx())/(size.width-24.dp.toPx()).coerceAtLeast(1f))*range))
            },onDragEnd = {finished?.invoke()})
        }) {
            val p=((value-valueRange.start)/range).coerceIn(0f,1f)
            val left=12.dp.toPx();val width=(size.width-left*2).coerceAtLeast(1f)
            val count=if(steps>0) steps+2 else 24
            repeat(count) { i -> drawCircle(if(i.toFloat()/(count-1)<=p)AppTheme.TextDark else AppTheme.FieldOutline,2.dp.toPx(),Offset(left+width*i/(count-1),size.height/2)) }
            drawCircle(AppTheme.PrimaryBlue,8.dp.toPx(),Offset(left+width*p,size.height/2))
            drawCircle(AppTheme.BackgroundLight,2.dp.toPx(),Offset(left+width*p,size.height/2))
        }
    }
}

@Composable
fun AilaAlertDialog(onDismissRequest: () -> Unit,confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,dismissButton: (@Composable () -> Unit)? = null,icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,text: (@Composable () -> Unit)? = null,shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = 0.dp,properties: DialogProperties = DialogProperties()) {
    if(!AppTheme.isNothing) AlertDialog(onDismissRequest,confirmButton,modifier,dismissButton,icon,title,text,shape,containerColor,iconContentColor,titleContentColor,textContentColor,tonalElevation,properties)
    else NothingPanelDialog(onDismissRequest,modifier,properties,title,icon,actions = {
        Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            CompositionLocalProvider(LocalNothingConfirmButton provides true) { confirmButton() }
            dismissButton?.invoke()
        }
    },body=text)
}

@Composable
fun AilaCircularProgress(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 4.dp, trackColor: Color = Color.Transparent,
    strokeCap: androidx.compose.ui.graphics.StrokeCap = androidx.compose.ui.graphics.StrokeCap.Round) {
    if (!AppTheme.isNothing) CircularProgressIndicator(modifier = modifier,color = color,strokeWidth = strokeWidth,trackColor = trackColor,strokeCap = strokeCap)
    else NothingMatrixLoader(modifier.size(24.dp).semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },color)
}
