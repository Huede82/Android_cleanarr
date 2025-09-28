package huedes.unraid.unraid_cleanarr.ui.theme // Passe dies an dein Package an

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Erstelle das Farbschema für unser dunkles Neon-Theme
private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonMagenta,
    background = DarkGreyBackground,
    surface = LightGreySurface,
    onPrimary = DarkGreyBackground, // Text auf primären Buttons
    onSecondary = DarkGreyBackground, // Text auf sekundären Elementen
    onBackground = PrimaryText, // Standard-Textfarbe
    onSurface = PrimaryText // Text auf Karten etc.
)

@Composable
fun UnraidControllerTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}