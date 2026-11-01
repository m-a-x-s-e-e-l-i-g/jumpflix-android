// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import tv.jumpflix.stats.*

@Composable
internal fun UserStatsScreen(owner: String, tv: Boolean, back: () -> Unit) {
    val model: UserStatsViewModel = viewModel(key = "account-user-stats")
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(owner) { model.load(owner) }
    DisposableEffect(model) { onDispose { model.close() } }
    BackHandler(onBack = back)
    UserStatsContent(state.takeIf { it.ownerId == owner } ?: UserStatsState(owner, loading = true), tv, { model.load(owner) }, back)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UserStatsContent(state: UserStatsState, tv: Boolean, refresh: () -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    val number = remember { NumberFormat.getIntegerInstance() }
    val focus = remember { FocusRequester() }
    var initialFocusSet by remember(state.ownerId) { mutableStateOf(false) }
    var page by rememberSaveable(state.ownerId) { mutableIntStateOf(0) }
    val data = state.data
    val list = rememberLazyListState()
    RefreshableScreen(list, state.loading, refresh, tv, "Refresh stats", "stats-refresh", Modifier.widthIn(max = 1040.dp).fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("stats-list"), state = list, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppAction("Back to account", tv, modifier = Modifier.focusRequester(focus), icon = Icons.Outlined.ArrowBack, action = back)
                    LaunchedEffect(Unit) { if (tv && !initialFocusSet) { focus.requestFocus(); initialFocusSet = true } }
                    PageHeading("Your stats", "Your JUMPFLIX activity, in one place.")
                }
            }
            state.error?.let { message -> item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    if (data != null) Text("Showing your last loaded stats.", color = Muted)
                }
            } }
            if (data != null) {
                item {
                    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(18.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        XpopMark("${number.format(data.xpop.total)} XPOP", true)
                        Text("Watching ${number.format(data.xpop.watchingPoints)} · Ratings ${number.format(data.xpop.ratingPoints)} · Reviews ${number.format(data.xpop.reviewPoints)} · Contributions ${number.format(data.xpop.contributionPoints)}", color = Muted)
                    }
                }
                item {
                    val overview = data.overview
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        StatsHeading("Watching")
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val wide = maxWidth >= 720.dp
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!wide) StatsKpi("Watched entries", number.format(overview.watched), "${number.format(overview.movies)} films · ${number.format(overview.series)} series · ${number.format(overview.episodes)} episodes", Modifier.fillMaxWidth())
                                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (wide) StatsKpi("Watched entries", number.format(overview.watched), "${number.format(overview.movies)} films · ${number.format(overview.series)} series · ${number.format(overview.episodes)} episodes", Modifier.weight(1f).fillMaxHeight())
                                    StatsKpi("Saved playback time", statsDuration(overview.positionSeconds), "Based on account playback positions", Modifier.weight(1f).fillMaxHeight())
                                    StatsKpi("Average rating", overview.averageRating?.let { String.format("%.1f / 10", it) } ?: "—", "${number.format(overview.ratings)} ratings · ${number.format(overview.reviews)} reviews", Modifier.weight(1f).fillMaxHeight())
                                }
                            }
                        }
                        Text("Manually marked watches can have no recorded time. In-progress resume positions from this app remain on this device.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(18.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatsHeading("Archive progress")
                        CatalogProgress("Films", data.overview.movies, data.overview.catalogMovies)
                        CatalogProgress("Episodes", data.overview.episodes, data.overview.catalogEpisodes)
                        Text("Counts follow your website watch-history entries and the current archive.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(18.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatsHeading("Rating distribution")
                        if (data.overview.ratings == 0L) Text("No ratings yet. Rate a title to start your chart.", color = Muted)
                        else {
                            val maximum = data.distribution.maxOrNull()?.coerceAtLeast(1) ?: 1
                            (1..10).forEach { rating ->
                                val count = data.distribution.getOrElse(rating - 1) { 0 }
                                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$rating out of 10: $count ratings" }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(rating.toString(), color = Muted, modifier = Modifier.width(24.dp))
                                    LinearProgressIndicator(progress = { (count.toDouble() / maximum).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.weight(1f).height(8.dp), color = Accent, trackColor = Background)
                                    Text(number.format(count), color = Ink, modifier = Modifier.widthIn(min = 24.dp))
                                }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatsHeading("Archive contributions")
                        Text("${number.format(data.contributions.approved)} approved · ${number.format(data.contributions.pending)} pending · ${number.format(data.contributions.declined)} declined", color = Ink)
                        Text("${number.format(data.contributions.units)} approved contribution units · ${number.format(data.xpop.contributionPoints)} XPOP", color = Muted)
                    }
                }
                item { StatsHeading("Your ratings") }
                if (data.ratedTitles.isEmpty()) item { Text("No rated titles yet.", color = Muted) }
                else {
                    val pages = (data.ratedTitles.size + 9) / 10
                    val currentPage = page.coerceIn(0, pages - 1)
                    data.ratedTitles.drop(currentPage * 10).take(10).forEach { rated ->
                        item(key = "rated-${rated.media.id}") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${rated.media.title} · ${rated.rating}/10", color = Ink, fontWeight = FontWeight.Bold)
                                StatsTitleLink(rated.media, tv) { openExternal(context, it) }
                            }
                        }
                    }
                    if (pages > 1) item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            XpopAction("Previous ratings", tv, currentPage > 0) { page = currentPage - 1 }
                            Text("${currentPage + 1} / $pages", color = Muted, modifier = Modifier.align(Alignment.CenterVertically))
                            XpopAction("Next ratings", tv, currentPage < pages - 1) { page = currentPage + 1 }
                        }
                    }
                }
                item { StatsHeading("Recent reviews") }
                if (data.recentReviews.isEmpty()) item { Text("No reviews yet.", color = Muted) }
                data.recentReviews.forEach { review ->
                    item(key = "review-${review.id}") {
                        Column(Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(18.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(review.media?.title ?: "Unavailable title", color = Ink, fontWeight = FontWeight.Bold)
                            val date = runCatching { Instant.parse(review.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrNull()
                            date?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                            Text(review.body, color = Ink)
                            review.media?.let { StatsTitleLink(it, tv) { url -> openExternal(context, url) } }
                        }
                    }
                }
            } else if (!state.loading && state.error == null) item { Text("Sign in to view your account stats.", color = Muted) }
        }
    }
}

@Composable
private fun StatsHeading(text: String) { Text(text, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) }

@Composable
private fun StatsKpi(label: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Surface, RoundedCornerShape(18.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelLarge)
        Text(value, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CatalogProgress(label: String, watched: Long, total: Long) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (total > 0) "$label · $watched / $total" else "$label · $watched watched", color = Ink)
        if (total > 0) LinearProgressIndicator(progress = { catalogFraction(watched, total) }, modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Background)
    }
}

@Composable
private fun StatsTitleLink(media: StatsTitle, tv: Boolean, open: (String) -> Unit) {
    if (media.slug.isNotBlank()) XpopAction("View on JUMPFLIX", tv) {
        open(Uri.Builder().scheme("https").authority("www.jumpflix.tv").appendPath(media.type).appendPath(media.slug).build().toString())
    }
}
