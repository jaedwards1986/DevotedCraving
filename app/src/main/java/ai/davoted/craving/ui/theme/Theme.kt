package ai.davoted.craving.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val UnBoundColors = darkColorScheme(
    primary = Color(0xFFC7A66A),
    onPrimary = Color(0xFF071321),
    secondary = Color(0xFF35A6A0),
    onSecondary = Color(0xFF06151C),
    tertiary = Color(0xFF6F8FD0),
    background = Color(0xFF071321),
    surface = Color(0xFF0D2030),
    surfaceVariant = Color(0xFF152D40),
    onBackground = Color(0xFFE7EEF2),
    onSurface = Color(0xFFE7EEF2),
    onSurfaceVariant = Color(0xFFAFC3D1),
    error = Color(0xFFE16D68)
)

@Composable
fun CravingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UnBoundColors,
        content = content
    )
}
