// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Background = Color(0xFF100F0F)
val Surface = Color(0xFF1E1B1A)
val SurfaceRaised = Color(0xFF292423)
val Outline = Color(0xFF49403C)
val Accent = Color(0xFFE63B28)
val ActionAccent = Color(0xFFC43223)
val Ink = Color(0xFFF5F2EE)
val Muted = Color(0xFFBFB5AE)

@Composable
fun JumpflixMaterialTheme(fontFamily: FontFamily, content: @Composable () -> Unit) {
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily, fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily, fontSize = 20.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily, fontSize = 16.sp, lineHeight = 25.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 22.sp),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily, fontSize = 12.sp, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily, fontSize = 12.sp, lineHeight = 17.sp),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily, fontSize = 11.sp, lineHeight = 16.sp),
    )
    MaterialTheme(
        colorScheme = darkColorScheme(primary = ActionAccent, onPrimary = Ink, primaryContainer = SurfaceRaised, onPrimaryContainer = Ink,
            background = Background, surface = Surface, surfaceVariant = SurfaceRaised, outline = Outline,
            onSurface = Ink, onBackground = Ink, onSurfaceVariant = Muted),
        typography = typography,
        content = content,
    )
}
