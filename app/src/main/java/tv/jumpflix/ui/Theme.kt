// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import tv.jumpflix.R

private val NotoSans = FontFamily(Font(R.font.noto_sans))

@Composable
fun JumpflixTheme(content: @Composable () -> Unit) {
    androidx.tv.material3.MaterialTheme(
        colorScheme = androidx.tv.material3.darkColorScheme(primary = Accent, background = Background, surface = Surface, onSurface = Ink),
    ) {
        JumpflixMaterialTheme(NotoSans, content)
    }
}
