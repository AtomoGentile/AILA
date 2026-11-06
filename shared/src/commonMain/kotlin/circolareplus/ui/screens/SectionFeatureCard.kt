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

/** Shared section language: an introduction, one clear action and the page's own controls. */
@Composable
internal fun SectionFeatureCard(title:String, description:String, modifier:Modifier=Modifier, badge:String?=null,
    icon:@Composable ()->Unit, actions:(@Composable RowScope.()->Unit)?=null) {
    val tint=MaterialTheme.colorScheme.tertiary
    Column(modifier.fillMaxWidth().background(Brush.linearGradient(listOf(AppTheme.CardSurface,tint.copy(alpha=.18f))),RoundedCornerShape(28.dp))
        .border(1.dp,tint.copy(alpha=.20f),RoundedCornerShape(28.dp)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
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
    Column(modifier.fillMaxWidth().background(AppTheme.CardSurface,RoundedCornerShape(28.dp)).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(60.dp).background(MaterialTheme.colorScheme.secondaryContainer,RoundedCornerShape(22.dp)),contentAlignment=Alignment.Center){icon()}
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=AppTheme.TextDark,textAlign=TextAlign.Center)
        Text(description,style=MaterialTheme.typography.bodyMedium,color=AppTheme.TextMuted,textAlign=TextAlign.Center)
    }
}
