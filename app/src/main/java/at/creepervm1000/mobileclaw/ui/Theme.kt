package at.creepervm1000.mobileclaw.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF00E5A0)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00281C),
    secondary = Color(0xFF6FD3FF),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE6EAEE),
    surface = Color(0xFF171C22),
    onSurface = Color(0xFFE6EAEE),
    surfaceVariant = Color(0xFF232A32),
    onSurfaceVariant = Color(0xFFB6C0CB),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00875F),
    secondary = Color(0xFF00668B),
    background = Color(0xFFF7F9FB),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun MobileClawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
