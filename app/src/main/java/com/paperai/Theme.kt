// ==================== app/src/main/java/com/paperai/Theme.kt ====================
package com.paperai

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6B5A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6EFD9),
    onPrimaryContainer = Color(0xFF00211A),
    secondary = Color(0xFF4A6358),
    secondaryContainer = Color(0xFFCFE9DA),
    onSecondaryContainer = Color(0xFF08201A),
    background = Color(0xFFF6FAF7),
    surface = Color(0xFFF6FAF7),
    surfaceVariant = Color(0xFFE8F0EA)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AD6BE),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF154D3E),
    onPrimaryContainer = Color(0xFFB6EFD9),
    secondary = Color(0xFFB1CCC0),
    secondaryContainer = Color(0xFF334B41),
    onSecondaryContainer = Color(0xFFCDE9DC),
    background = Color(0xFF0F1513),
    surface = Color(0xFF0F1513),
    surfaceVariant = Color(0xFF232D29)
)

@Composable
fun PaperAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

private val BOOK_COLORS = listOf(
    Color(0xFFBEE7D5), Color(0xFFF6D6C2), Color(0xFFCBDDF7),
    Color(0xFFE8D5F0), Color(0xFFF7E6A8), Color(0xFFD8E9C8)
)

fun bookCoverColor(i: Int): Color =
    BOOK_COLORS[((i % BOOK_COLORS.size) + BOOK_COLORS.size) % BOOK_COLORS.size]
