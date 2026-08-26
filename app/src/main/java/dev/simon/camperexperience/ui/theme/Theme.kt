package dev.simon.camperexperience.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ForestGreen = Color(0xFF2E5D3E)
private val Moss = Color(0xFF6B8F71)
private val Sand = Color(0xFFD9C5A0)

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    secondary = Moss,
    tertiary = Sand,
)

private val DarkColors = darkColorScheme(
    primary = Moss,
    secondary = Sand,
    tertiary = ForestGreen,
)

@Composable
fun CamperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
