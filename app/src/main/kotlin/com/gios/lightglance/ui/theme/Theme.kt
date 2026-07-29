package com.gios.lightglance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** LightOS renders greyscale on a matte panel, so the palette is luminance only. */
private val MonoDark = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    background = Color.Black, onBackground = Color.White,
    surface = Color.Black, onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A), onSurfaceVariant = Color(0xFFBBBBBB),
)

val Dim = Color(0xFF9A9A9A)
val Faint = Color(0xFF5E5E5E)
val RuleGrey = Color(0xFF262626)

@Composable
fun LightGlanceTheme(content: @Composable () -> Unit) {
    val fam = remember { akkuratFamilyOrDefault() }
    val type = remember(fam) {
        Typography(
        displaySmall = TextStyle(fontFamily = fam, fontSize = 40.sp, fontWeight = FontWeight.Light),
        titleLarge = TextStyle(fontFamily = fam, fontSize = 26.sp, fontWeight = FontWeight.Light),
        titleMedium = TextStyle(fontFamily = fam, fontSize = 20.sp, fontWeight = FontWeight.Normal),
        bodyLarge = TextStyle(fontFamily = fam, fontSize = 17.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontFamily = fam, fontSize = 14.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontFamily = fam, fontSize = 12.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(
            fontFamily = fam, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            letterSpacing = 2.2.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = fam, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            letterSpacing = 1.4.sp,
        ),
        )
    }
    MaterialTheme(colorScheme = MonoDark, typography = type, content = content)
}
