package circolareplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import circolareplus.design.AppTheme
import circolareplus.design.ailaGlassSurface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** Shared section language: an introduction, one clear action and the page's own controls. */
@Composable
internal fun SectionFeatureCard(title:String, description:String, modifier:Modifier=Modifier, badge:String?=null,
    icon:@Composable ()->Unit, actions:(@Composable RowScope.()->Unit)?=null) {
    val tint=MaterialTheme.colorScheme.tertiary
    if (circolareplus.design.isDesktopWorkspace()) {
        Row(modifier.fillMaxWidth().communitySurface(RoundedCornerShape(16.dp), tint).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).background(tint.copy(alpha=.12f), RoundedCornerShape(12.dp)), contentAlignment=Alignment.Center) { icon() }
            Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title, style=MaterialTheme.typography.titleMedium, fontWeight=FontWeight.SemiBold, color=AppTheme.TextDark)
                Text(description, style=MaterialTheme.typography.bodySmall, color=AppTheme.TextMuted)
                badge?.let { Text(it, style=MaterialTheme.typography.labelSmall, color=MaterialTheme.colorScheme.secondary) }
            }
            actions?.let { Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically, content=it) }
        }
        return
    }
    Column(modifier.fillMaxWidth().communitySurface(RoundedCornerShape(28.dp),tint).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(tint.copy(alpha=.16f),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center){icon()}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                badge?.let{Text(it,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.secondary)}
                Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=AppTheme.TextDark)
            }
        }
        Text(description,style=MaterialTheme.typography.bodyMedium,color=AppTheme.TextMuted)
        actions?.let { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically,content=it) }
    }
}

@Composable
internal fun SectionEmptyCard(title:String, description:String, modifier:Modifier=Modifier, icon:@Composable ()->Unit) {
    Column(modifier.fillMaxWidth().communitySurface(RoundedCornerShape(28.dp)).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(60.dp).background(MaterialTheme.colorScheme.secondaryContainer,RoundedCornerShape(22.dp)),contentAlignment=Alignment.Center){icon()}
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=AppTheme.TextDark,textAlign=TextAlign.Center)
        Text(description,style=MaterialTheme.typography.bodyMedium,color=AppTheme.TextMuted,textAlign=TextAlign.Center)
    }
}

/** Liquid Glass keeps its optical surface; tonal gradients belong to Material. */
internal fun Modifier.communitySurface(shape:Shape,tint:Color?=null):Modifier =
    if(AppTheme.isGlass) ailaGlassSurface(shape,tint=when {
        tint != null -> tint.copy(alpha=if(AppTheme.isDarkMode) .12f else .09f)
        AppTheme.isDarkMode -> Color.White.copy(alpha=.055f)
        else -> Color.White.copy(alpha=.52f)
    })
    else if(tint!=null) background(Brush.linearGradient(listOf(AppTheme.CardSurface,tint.copy(alpha=.18f))),shape).border(1.dp,tint.copy(alpha=.20f),shape)
    else background(AppTheme.CardSurface,shape)
