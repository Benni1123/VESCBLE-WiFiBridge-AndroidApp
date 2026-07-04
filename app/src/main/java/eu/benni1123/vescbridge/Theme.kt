package eu.benni1123.vescbridge

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle

val AppFontFamily = FontFamily(
    Font(R.font.ndot47, FontWeight.Normal),
    Font(R.font.ndot47, FontWeight.Bold)
)

object AppTheme {
    val DarkColorScheme = darkColorScheme(
        primary = AppColors.Accent,
        background = Color.Black,
        surface = Color(0xFF1A1A1A),
        onPrimary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        onSurfaceVariant = Color(0xFFAAAAAA),
        error = AppColors.Err
    )

    val LightColorScheme = lightColorScheme(
        primary = AppColors.Accent,
        background = Color(0xFFF5F5F5),
        surface = Color.White,
        onPrimary = Color.White,
        onBackground = Color.Black,
        onSurface = Color.Black,
        onSurfaceVariant = Color(0xFF666666),
        error = AppColors.Err
    )
}

@Composable
fun VescBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AppTheme.DarkColorScheme else AppTheme.LightColorScheme

    val typography = Typography(
        displayLarge = TextStyle(fontFamily = AppFontFamily),
        displayMedium = TextStyle(fontFamily = AppFontFamily),
        displaySmall = TextStyle(fontFamily = AppFontFamily),
        headlineLarge = TextStyle(fontFamily = AppFontFamily),
        headlineMedium = TextStyle(fontFamily = AppFontFamily),
        headlineSmall = TextStyle(fontFamily = AppFontFamily),
        titleLarge = TextStyle(fontFamily = AppFontFamily),
        titleMedium = TextStyle(fontFamily = AppFontFamily),
        titleSmall = TextStyle(fontFamily = AppFontFamily),
        bodyLarge = TextStyle(fontFamily = AppFontFamily),
        bodyMedium = TextStyle(fontFamily = AppFontFamily),
        bodySmall = TextStyle(fontFamily = AppFontFamily),
        labelLarge = TextStyle(fontFamily = AppFontFamily),
        labelMedium = TextStyle(fontFamily = AppFontFamily),
        labelSmall = TextStyle(fontFamily = AppFontFamily)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}
