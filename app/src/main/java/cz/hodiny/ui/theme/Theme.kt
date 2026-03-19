package cz.hodiny.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Primary = Color(0xFF1565C0)
private val OnPrimary = Color.White
private val Background = Color(0xFFF5F5F5)
private val Surface = Color.White

private val ColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    background = Background,
    surface = Surface,
    secondary = Color(0xFF388E3C),
)

@Composable
fun HodinyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography(),
        content = content
    )
}
