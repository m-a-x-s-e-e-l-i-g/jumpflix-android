// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import tv.jumpflix.data.CatalogItem

@Composable
internal fun FilmBackdrop(item: CatalogItem, modifier: Modifier = Modifier) {
    var usePoster by remember(item.id) { mutableStateOf(false) }
    val youtubeId = item.playback?.takeIf { it.kind == "youtube" }?.providerId
    val wideImage = youtubeId?.let { "https://i.ytimg.com/vi/$it/maxresdefault.jpg" }
    AsyncImage(if (usePoster || wideImage == null) item.thumbnail else wideImage, null, modifier,
        contentScale = ContentScale.Crop, onError = { usePoster = true })
}

@Composable
internal fun AppAction(label: String, tv: Boolean, primary: Boolean = false, enabled: Boolean = true,
    modifier: Modifier = Modifier, icon: ImageVector? = null, action: () -> Unit) {
    if (tv) {
        androidx.tv.material3.Button(action, modifier.heightIn(min = 48.dp), enabled = enabled,
            shape = androidx.tv.material3.ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
            colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = if (primary) ActionAccent else SurfaceRaised, contentColor = Ink),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
            if (icon != null) { androidx.tv.material3.Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            androidx.tv.material3.Text(label, style = MaterialTheme.typography.labelLarge)
        }
    } else if (primary) {
        Button(action, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ActionAccent, contentColor = Ink), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)) {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(label, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(action, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Outline), colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink, containerColor = Surface.copy(alpha = .55f)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
            if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(label)
        }
    }
}

@Composable
internal fun PageHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        subtitle?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodyMedium) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RefreshableScreen(list: LazyListState, loading: Boolean, refresh: () -> Unit, tv: Boolean,
    label: String, tag: String, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val refreshIfIdle = { if (!loading) refresh() }
    PullToRefreshBox(isRefreshing = loading, onRefresh = refreshIfIdle,
        modifier = modifier.testTag(tag).semantics {
            customActions = listOf(CustomAccessibilityAction(label) { refreshIfIdle(); true })
        }.onPreviewKeyEvent { event ->
            if (tv && event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown && !list.canScrollBackward && event.nativeKeyEvent.repeatCount == 0) {
                refreshIfIdle(); true
            } else false
        }, content = content)
}
