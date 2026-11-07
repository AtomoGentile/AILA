package circolareplus.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import circolareplus.data.remote.dto.AcaExperienceDto

/** Distinctive release landmark. The optional owner-produced film is never autoplayed. */
@Composable fun NewAilaHomeHero(experience:AcaExperienceDto,onOpenAca:()->Unit,onOpenForum:()->Unit,modifier:Modifier=Modifier){
 val uri=LocalUriHandler.current
 Box(modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
  .background(Brush.linearGradient(listOf(Color(0xFF10172C),AppTheme.AccentDeep,Color(0xFF17203B))))) {
  Canvas(Modifier.matchParentSize()){
   val center=Offset(size.width*.9f,size.height*.18f)
   listOf(.32f,.49f,.67f).forEachIndexed{i,f->drawCircle(AppTheme.PrimaryBlue.copy(alpha=.18f-i*.035f),size.width*f,center,style=Stroke(1.dp.toPx()))}
  }
  Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
   Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){
    Text("LA NUOVA AILA",style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold,color=Color(0xFFBECCFF))
    AilaAssistantMark(modifier=Modifier.size(38.dp))
   }
   Text(experience.title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold,color=Color.White)
   Text("Un assistente che conosce il contesto. Una classe che si aiuta.",style=MaterialTheme.typography.bodyLarge,color=Color(0xFFD3DDF7))
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
    Button(onClick=onOpenAca,colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=Color(0xFF17203B))){Text("Scopri ACA")}
    TextButton(onClick=onOpenForum,colors=ButtonDefaults.textButtonColors(contentColor=Color.White)){Text("Revisione tra pere")}
   }
   val policy = circolareplus.ai.AcaPolicyState.preferences
   if(policy.aiEnabled && policy.acaEnabled && policy.holdToOpen)
    Text("Tieni premuto per 2 secondi in un’area libera per aprire ACA.",style=MaterialTheme.typography.bodySmall,color=Color(0xFFD3DDF7))
   experience.videoUrl?.let{url->TextButton(onClick={uri.openUri(url)},colors=ButtonDefaults.textButtonColors(contentColor=Color(0xFFD8DFFF))){Text("▶  Guarda il video")}}
  }
 }
}
