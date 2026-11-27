package ai.davoted.craving.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DavotedColors = darkColorScheme(
    primary = Color(0xFFC9A227),
    onPrimary = Color(0xFF0B0708),
    secondary = Color(0xFFB98C83),
    background = Color(0xFF0B0708),
    surface = Color(0xFF140D0F),
    onBackground = Color(0xFFE8DCC8),
    onSurface = Color(0xFFE8DCC8),
    error = Color(0xFFE2571F)
)

@Composable
fun CravingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DavotedColors,
        content = content
    )
}
