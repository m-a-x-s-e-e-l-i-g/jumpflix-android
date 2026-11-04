// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrandMark(mascot: Painter, wordmark: FontFamily, modifier: Modifier = Modifier, compact: Boolean = false, showMascot: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        if (showMascot) Image(mascot, "JUMPFLIX popcorn character", Modifier.size(if (compact) 26.dp else 49.dp, if (compact) 34.dp else 64.dp))
        Column {
            Text("JUMPFLIX", color = Accent, fontSize = if (compact) 24.sp else 39.sp, letterSpacing = if (compact) 1.5.sp else 3.sp,
                fontFamily = wordmark, modifier = Modifier.graphicsLayer { rotationX = 12f; scaleX = 1.04f })
            if (!compact) Text("PARKOUR CINEMA", color = Muted, fontSize = 8.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
fun PageHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Shared poster composition; the host supplies its image loader and input container. */
@Composable
fun FilmPosterContent(title: String, metadata: String, paid: Boolean, available: Boolean = true,
    watched: Boolean = false, progress: Float? = null, image: @Composable (Modifier) -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Surface)) {
        image(Modifier.fillMaxSize())
        if (paid || !available) Text(if (!available) "UNAVAILABLE" else "PAID", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Background.copy(alpha = .85f), RoundedCornerShape(4.dp)).padding(5.dp))
        if (watched) Row(Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Background.copy(alpha = .95f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(14.dp), tint = Ink)
            Text("Watched", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        if (!watched && progress != null) LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), color = Accent, trackColor = Surface)
    }
    Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        Text(metadata, color = Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
