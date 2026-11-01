// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tv.jumpflix.data.*

@Composable
internal fun SeasonSelector(seasons: List<Season>, selected: Season?, tv: Boolean, modifier: Modifier = Modifier,
    select: (Int) -> Unit) {
    Column(modifier.fillMaxWidth().background(Background).then(if (tv && seasons.size <= 1) Modifier.focusable() else Modifier)
        .padding(top = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Episodes", color = Ink, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            selected?.let { Text("${it.episodes.size} ${if (it.episodes.size == 1) "episode" else "episodes"}", color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
        if (seasons.size > 1) LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(seasons, key = { it.number }) { season ->
                AppAction(season.title, tv, primary = season.number == selected?.number,
                    modifier = Modifier.semantics { this.selected = season.number == selected?.number }.testTag("season-${season.number}")) { select(season.number) }
            }
        } else selected?.let { Text(it.title, color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp)) }
    }
}

@Composable
internal fun EpisodeRow(episode: Episode, progress: Progress?, watched: Boolean, available: Boolean, tv: Boolean,
    expanded: Boolean, expand: () -> Unit, play: (Long?) -> Unit, details: @Composable () -> Unit) {
    val playable = available && episode.playback != null
    val action = when { !playable -> "Unavailable"; watched -> "Rewatch"; progress?.inProgress == true -> "Resume"; else -> "Play" }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("episode-${episode.id}")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).heightIn(min = 88.dp)
                .background(if (focused) SurfaceRaised else Color.Transparent, RoundedCornerShape(12.dp))
                .border(2.dp, if (focused) Ink else Color.Transparent, RoundedCornerShape(12.dp))
                .clickable(interactionSource = interaction, indication = null, onClickLabel = "$action episode ${episode.position}") {
                    if (playable) play(if (watched) 0L else null) else expand()
                }
                .semantics { contentDescription = "$action episode ${episode.position}: ${episode.title}" }
                .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(episode.position.toString().padStart(2, '0'), color = Muted, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.widthIn(min = 28.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(episode.title, color = Ink, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (watched) Icon(Icons.Outlined.CheckCircle, null, Modifier.size(14.dp), tint = Muted)
                        Text(if (watched && playable) "Watched · Rewatch" else action, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!watched && progress?.inProgress == true && progress.durationMs > 0) {
                        LinearProgressIndicator(progress = { (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) },
                            modifier = Modifier.widthIn(max = 160.dp).fillMaxWidth().height(2.dp), color = Accent, trackColor = Outline)
                    }
                }
                if (playable) Icon(Icons.Outlined.PlayArrow, null, Modifier.size(24.dp), tint = Ink)
            }
            AppIconAction(if (expanded) "Hide episode ${episode.position} details" else "Episode ${episode.position} details",
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, tv, selected = expanded, action = expand)
        }
        if (expanded) details()
        HorizontalDivider(color = Outline.copy(alpha = .45f))
    }
}
