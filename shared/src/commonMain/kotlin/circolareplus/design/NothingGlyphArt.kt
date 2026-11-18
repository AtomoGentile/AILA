package circolareplus.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import circolareplus.generated.resources.Res
import circolareplus.generated.resources.aila_dot
import org.jetbrains.compose.resources.Font

/** Dot text is reserved for titles and instruments. */
@Composable
fun nothingDisplayStyle(size: Int, lineHeight: Int = size + 8, dots: Boolean = NothingExperience.preferences.dotDisplay): TextStyle =
    TextStyle(
        fontFamily = if (dots) FontFamily(Font(Res.font.aila_dot)) else FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = 0.sp
    )
