// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import tv.jumpflix.data.*

@Composable
internal fun VideoCreditsSection(reference: CreditReference, tv: Boolean, playable: Boolean, playAt: (Long) -> Unit) {
    val context = LocalContext.current
    val repository = remember { VideoCreditsRepository(context.applicationContext) }
    var credits by remember(reference) { mutableStateOf(VideoCredits()) }
    var loading by remember(reference) { mutableStateOf(true) }
    var attempt by remember(reference) { mutableIntStateOf(0) }
    LaunchedEffect(reference, attempt) {
        loading = true
        credits = repository.load(reference, force = attempt > 0)
        loading = false
    }
    VideoCreditsContent(credits, loading, tv, playable, { attempt++ }, playAt) { openExternal(context, it) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoCreditsContent(credits: VideoCredits, loading: Boolean, tv: Boolean, playable: Boolean,
    retry: () -> Unit, playAt: (Long) -> Unit, openLink: (String) -> Unit) {
    var spotLimit by remember(credits) { mutableIntStateOf(6) }
    var songLimit by remember(credits) { mutableIntStateOf(6) }
    var songsSelected by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Spots & songs", color = Ink, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val labels = listOf("Spots" to credits.spots.size, "Songs" to credits.songs.size)
            labels.forEachIndexed { index, (label, count) ->
                val selected = songsSelected == (index == 1)
                val title = if (loading) label else "$label ($count)"
                if (tv) AppAction(title, true, primary = selected) { songsSelected = index == 1 }
                else FilterChip(selected, { songsSelected = index == 1 }, { Text(title) }, modifier = Modifier.heightIn(min = 48.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SurfaceRaised, selectedLabelColor = Ink))
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        else if (!songsSelected) {
            credits.spotsError?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
            if (credits.spots.isEmpty() && credits.spotsError == null) Text("No spots linked yet.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            credits.spots.take(spotLimit).forEach { spot ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(spot.name, color = Ink, style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull("${creditTime(spot.start)} – ${creditTime(spot.end)}", spot.country).joinToString(" · "), color = Muted, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (playable) AppAction("Play from ${creditTime(spot.start)}", tv) { playAt(spot.start) }
                        AppAction("Open on parkour.spot", tv) { openLink(spot.url) }
                    }
                }
                HorizontalDivider(color = Outline.copy(alpha = .5f))
            }
            if (credits.spots.size > spotLimit) AppAction("More spots (${credits.spots.size - spotLimit})", tv) { spotLimit += 20 }
        }
        if (!loading && songsSelected) {
            credits.songsError?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
            if (credits.songs.isEmpty() && credits.songsError == null) Text("No songs credited yet.", color = Muted, style = MaterialTheme.typography.bodyMedium)
            credits.songs.take(songLimit).forEach { song ->
                // A shared 00:00 commonly means an untimed import, not a reliable cue.
                val timed = song.start?.takeIf { start -> credits.songs.count { it.start == start } == 1 }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(song.title, color = Ink, style = MaterialTheme.typography.titleMedium)
                    Text(song.artist, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    if (song.explicit) Text("Explicit lyrics", color = Muted, style = MaterialTheme.typography.labelMedium)
                    if (timed == null) Text("Timing unavailable", color = Muted, style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (timed != null && playable) AppAction("Play from ${creditTime(timed)}", tv) { playAt(timed) }
                        else if (timed != null) Text(creditTime(timed), color = Muted, style = MaterialTheme.typography.bodySmall)
                        song.spotifyUrl?.let { url -> AppAction("Open in Spotify", tv) { openLink(url) } }
                    }
                }
                HorizontalDivider(color = Outline.copy(alpha = .5f))
            }
            if (credits.songs.size > songLimit) AppAction("More songs (${credits.songs.size - songLimit})", tv) { songLimit += 20 }
        }
        if (!loading && (credits.spotsError != null || credits.songsError != null)) AppAction("Retry credits", tv, action = retry)
    }
}
