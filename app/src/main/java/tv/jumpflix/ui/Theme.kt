// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import tv.jumpflix.R

val Background = Color(0xFF100E10)
val Surface = Color(0xFF201C20)
val Accent = Color(0xFFE50914)
val Ink = Color(0xFFF6F1F3)
val Muted = Color(0xFFC3B8BE)
private val NotoSans = FontFamily(Font(R.font.noto_sans))

@Composable
fun JumpflixTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = NotoSans),
        displayMedium = base.displayMedium.copy(fontFamily = NotoSans),
        displaySmall = base.displaySmall.copy(fontFamily = NotoSans),
        headlineLarge = base.headlineLarge.copy(fontFamily = NotoSans),
        headlineMedium = base.headlineMedium.copy(fontFamily = NotoSans),
        headlineSmall = base.headlineSmall.copy(fontFamily = NotoSans),
        titleLarge = base.titleLarge.copy(fontFamily = NotoSans),
        titleMedium = base.titleMedium.copy(fontFamily = NotoSans),
        titleSmall = base.titleSmall.copy(fontFamily = NotoSans),
        bodyLarge = base.bodyLarge.copy(fontFamily = NotoSans),
        bodyMedium = base.bodyMedium.copy(fontFamily = NotoSans),
        bodySmall = base.bodySmall.copy(fontFamily = NotoSans),
        labelLarge = base.labelLarge.copy(fontFamily = NotoSans),
        labelMedium = base.labelMedium.copy(fontFamily = NotoSans),
        labelSmall = base.labelSmall.copy(fontFamily = NotoSans),
    )
    androidx.tv.material3.MaterialTheme(
        colorScheme = androidx.tv.material3.darkColorScheme(primary = Accent, background = Background, surface = Surface, onSurface = Ink),
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(primary = Accent, onPrimary = Ink, background = Background, surface = Surface, onSurface = Ink, onBackground = Ink, onSurfaceVariant = Muted),
            typography = typography,
            content = content,
        )
    }
}
