package huedes.unraid.unraid_cleanarr.ui.theme // Passe dies an dein Package an

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Standard-Typografie (verwendet die moderne System-Schriftart)
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

// Eine wiederverwendbare Funktion, um den Neon-Glow-Effekt zu erzeugen
@Composable
fun neonTextStyle(color: Color): TextStyle {
    return TextStyle(
        shadow = Shadow(
            color = color.copy(alpha = 0.6f),
            offset = Offset.Zero,
            blurRadius = 15f // Der "Glow"-Radius
        )
    )
}