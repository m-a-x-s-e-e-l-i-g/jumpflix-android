// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tv.jumpflix.data.*
import tv.jumpflix.ui.*
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import tv.jumpflix.desktop.resources.*

@Composable
internal fun DesktopApp(repository: CatalogClient, fullscreen: Boolean, toggleFullscreen: () -> Unit, openUrl: (String) -> Unit) {
    var catalog by remember { mutableStateOf<Catalog?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) {
        loading = true
        error = null
        if (catalog == null) catalog = repository.saved()
        try { catalog = repository.refresh() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Could not refresh the catalog." }
        finally { loading = false }
    }
    DesktopBrowse(catalog, loading, error, { if (!loading) refresh++ }, repository::series, fullscreen, toggleFullscreen, openUrl)
}

private enum class Destination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home), Search("Search", Icons.Outlined.Search), About("About", Icons.Outlined.Info),
}

@Composable
internal fun DesktopBrowse(catalog: Catalog?, loading: Boolean, error: String?, refresh: () -> Unit,
    series: suspend (String) -> CatalogItem, fullscreen: Boolean, toggleFullscreen: () -> Unit, openUrl: (String) -> Unit) {
    val font = FontFamily(Font(Res.font.noto_sans))
    val wordmark = FontFamily(Font(Res.font.bebas_neue))
    JumpflixMaterialTheme(font) {
        val focus = LocalFocusManager.current
        val navigationFocus = remember { Destination.entries.associateWith { FocusRequester() } }
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        var destination by remember { mutableStateOf(Destination.Home) }
        var selected by remember { mutableStateOf<CatalogItem?>(null) }
        var query by remember { mutableStateOf("") }
        var editingSearch by remember { mutableStateOf(false) }
        var detailLoading by remember { mutableStateOf(false) }
        var detailError by remember { mutableStateOf<String?>(null) }
        var detailAttempt by remember { mutableIntStateOf(0) }
        val visible = remember(catalog) { catalog?.visibleItems.orEmpty() }
        LaunchedEffect(selected?.id, destination) {
            if (selected == null) navigationFocus.getValue(destination).requestFocus()
        }
        // A refresh can remove a previously selected title, just as on Android.
        LaunchedEffect(visible) { if (selected != null && visible.none { it.id == selected?.id }) selected = null }
        LaunchedEffect(selected?.id, detailAttempt) {
            val item = selected
            detailError = null
            detailLoading = item?.type == "series"
            if (item?.type == "series") {
                try { selected = series(item.slug).takeIf { it.available } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { detailError = "Episodes could not be loaded." }
                finally { detailLoading = false }
            }
        }
        val open: (String) -> Unit = { url ->
            scope.launch {
                try { browserUri(url); openUrl(url) }
                catch (_: Exception) { snackbar.showSnackbar("Could not open your browser. Please check that a browser is installed.") }
            }
        }
        Box(Modifier.fillMaxSize().background(Background).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                Key.F11 -> { toggleFullscreen(); true }
                Key.Escape -> when {
                    selected != null -> { selected = null; true }
                    destination != Destination.Home -> { destination = Destination.Home; true }
                    fullscreen -> { toggleFullscreen(); true }
                    else -> false
                }
                Key.DirectionUp -> focus.moveFocus(FocusDirection.Up)
                Key.DirectionDown -> focus.moveFocus(FocusDirection.Down)
                Key.DirectionLeft -> if (editingSearch) false else focus.moveFocus(FocusDirection.Left)
                Key.DirectionRight -> if (editingSearch) false else focus.moveFocus(FocusDirection.Right)
                else -> false
            }
        }) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(178.dp).fillMaxHeight().background(Surface.copy(alpha = .5f)).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrandMark(painterResource(Res.drawable.mascot), wordmark, Modifier.padding(vertical = 20.dp), compact = true)
                    Destination.entries.forEach { entry ->
                        TvButton(entry.label, entry.icon, Modifier.fillMaxWidth().focusRequester(navigationFocus.getValue(entry)),
                            active = destination == entry && selected == null) { destination = entry; selected = null }
                    }
                    Spacer(Modifier.weight(1f))
                    TvButton(if (fullscreen) "Windowed" else "Fullscreen", if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        Modifier.fillMaxWidth(), onClick = toggleFullscreen)
                    Text("Desktop preview", style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val item = selected
                    when {
                        item != null -> Details(item, detailLoading, detailError, { selected = null }, { detailAttempt++ }, open)
                        destination == Destination.About -> Column(Modifier.padding(36.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            PageHeading("JUMPFLIX", "Parkour cinema, made for the big screen.")
                            Text("This desktop preview includes browsing, search and film/episode discovery. Playback opens in your browser. Account sign-in, XPOP and local watch tracking are currently available in the Android app.", color = Muted)
                            Text("Arrow keys move focus · Enter selects · Esc goes back · F11 toggles fullscreen", color = Ink)
                            Text("${visible.size} available titles · Catalog ${catalog?.generatedAt ?: "not loaded"}", color = Muted)
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            TvButton(if (loading) "Refreshing…" else "Refresh catalog", Icons.Outlined.Refresh, enabled = !loading, onClick = refresh)
                            Text("GPL-3.0-only · NuvioTV focus-card attribution and font licenses are bundled with this app. Artwork and videos belong to their respective owners.", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        catalog == null -> Column(Modifier.padding(36.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            PageHeading(if (loading) "Loading films…" else "The archive could not be loaded")
                            if (loading) CircularProgressIndicator(color = Accent)
                            else { Text(error ?: "Please check your connection.", color = Muted); TvButton("Retry", Icons.Outlined.Refresh, onClick = refresh) }
                        }
                        destination == Destination.Search -> Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                            PageHeading("Search")
                            OutlinedTextField(query, { query = it }, label = { Text("Film, filmmaker or athlete") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("desktop-search").onFocusChanged { editingSearch = it.isFocused })
                            val terms = remember(query) { catalogSearchTerms(query) }
                            val results = remember(visible, terms) { visible.filter { it.matches(terms) } }
                            Text("${results.size} titles", color = Muted, style = MaterialTheme.typography.bodySmall)
                            if (results.isEmpty()) Text("No films found. Try another name.", color = Muted)
                            LazyVerticalGrid(GridCells.Adaptive(150.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
                                contentPadding = PaddingValues(6.dp), modifier = Modifier.testTag("desktop-results")) {
                                items(results, key = { it.id }) { film -> DesktopPoster(film) { selected = film } }
                            }
                        }
                        else -> Home(visible, { selected = it })
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(24.dp))
        }
    }
}

@Composable
private fun Home(films: List<CatalogItem>, select: (CatalogItem) -> Unit) {
    val featured = remember(films, LocalDate.now()) { dailyFeatured(films, LocalDate.now()) }
    var feature by remember { mutableIntStateOf(0) }
    var shuffle by remember { mutableIntStateOf(0) }
    val fresh = remember(films) { films.sortedByDescending { it.addedAt }.take(24) }
    val explore = remember(films, shuffle) { films.shuffled() }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(28.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
        if (featured.isNotEmpty()) item {
            val film = featured[feature.coerceIn(0, featured.lastIndex)]
            Box(Modifier.fillMaxWidth().height(340.dp)) {
                Artwork(film, Modifier.fillMaxSize(), wide = true)
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Background, Background.copy(alpha = .7f), Color.Transparent))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Background))))
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(.75f).padding(30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("FEATURED FILM", color = Muted, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
                    Text(film.title, color = Ink, fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(film.metadata, color = Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvButton("Discover film", Icons.Outlined.PlayArrow, active = true) { select(film) }
                        TvButton("Next pick", Icons.AutoMirrored.Outlined.ArrowForward) { feature = (feature + 1) % featured.size }
                    }
                }
            }
        }
        item { FilmRow("New", fresh, select) }
        item { FilmRow("Explore", explore, select, shuffle) { shuffle++ } }
        if (films.isEmpty()) item { Column(Modifier.padding(30.dp)) { PageHeading("No films available", "Check About for catalog status.") } }
    }
}

@Composable
private fun FilmRow(title: String, films: List<CatalogItem>, select: (CatalogItem) -> Unit, shuffle: Int = 0, randomize: (() -> Unit)? = null) {
    val row = rememberLazyListState()
    LaunchedEffect(shuffle) { if (shuffle > 0) row.scrollToItem(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.weight(1f))
            randomize?.let { TvButton("Randomize", Icons.Outlined.Shuffle, onClick = it) }
        }
        LazyRow(state = row, contentPadding = PaddingValues(horizontal = 30.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(films, key = { it.id }) { film -> DesktopPoster(film, Modifier.width(154.dp)) { select(film) } }
        }
    }
}

@Composable
private fun DesktopPoster(film: CatalogItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(onClick, modifier.onFocusChanged { focused = it.isFocused }.testTag("poster-${film.id}"), shape = RoundedCornerShape(10.dp), color = Background,
        border = BorderStroke(2.dp, if (focused) Ink else Color.Transparent)) {
        Column { FilmPosterContent(film.title, film.metadata.ifBlank { if (film.type == "series") "Series" else "Film" }, film.paid) { Artwork(film, it) } }
    }
}

@Composable
private fun Artwork(film: CatalogItem, modifier: Modifier, wide: Boolean = false) {
    var fallback by remember(film.id) { mutableStateOf(false) }
    val youtube = film.playback?.takeIf { it.kind == "youtube" }?.providerId
    val url = if (wide && youtube != null && !fallback) "https://i.ytimg.com/vi/$youtube/maxresdefault.jpg" else film.thumbnail
    AsyncImage(url, null, modifier.background(Surface), contentScale = ContentScale.Crop, onError = { fallback = true })
}

@Composable
private fun Details(film: CatalogItem, loading: Boolean, error: String?, back: () -> Unit, retry: () -> Unit, open: (String) -> Unit) {
    val first = remember(film.id) { FocusRequester() }
    var season by remember(film.id) { mutableIntStateOf(0) }
    val seasons = film.availableSeasons
    LazyColumn(contentPadding = PaddingValues(30.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            TvButton("Back", Icons.AutoMirrored.Outlined.ArrowBack, Modifier.focusRequester(first), onClick = back)
            LaunchedEffect(film.id) { first.requestFocus() }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Artwork(film, Modifier.width(185.dp).aspectRatio(2f / 3f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    PageHeading(film.title, film.metadata)
                    if (film.creators.isNotEmpty()) Text(film.creators.joinToString(" · "), color = Ink)
                    if (film.starring.isNotEmpty()) Text(film.starring.joinToString(" · "), color = Muted)
                    Text(film.description, color = Muted, style = MaterialTheme.typography.bodyLarge)
                    if (film.type != "series" || film.paid) {
                        TvButton(if (film.paid) "View access options" else "Watch in browser", Icons.Outlined.PlayArrow, active = true) {
                            open(playbackUrl(film))
                        }
                    }
                    Text("Playback opens in your browser in this desktop preview.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (loading) item { CircularProgressIndicator(color = Accent) }
        if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error); TvButton("Retry episodes", Icons.Outlined.Refresh, onClick = retry) }
        if (film.type == "series" && !film.paid && !loading && error == null) {
            if (seasons.isEmpty()) item { Text("No episodes available.", color = Muted) }
            else {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(4.dp)) {
                        items(seasons.size) { index -> TvButton(seasons[index].title, active = season == index) { season = index } }
                    }
                }
                items(seasons[season.coerceIn(0, seasons.lastIndex)].episodes, key = { it.id }) { episode ->
                    TvButton("${episode.position}. ${episode.title}", Icons.Outlined.PlayArrow, Modifier.fillMaxWidth()) { episode.playback?.let { open(it.url) } }
                }
            }
        }
    }
}

internal fun playbackUrl(film: CatalogItem): String {
    val source = film.playback
    return if (film.paid || source == null || source.native) film.url else source.url
}

@Composable
private fun TvButton(label: String, icon: ImageVector? = null, modifier: Modifier = Modifier, active: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    OutlinedButton(onClick, modifier.heightIn(min = 52.dp).onFocusChanged { focused = it.isFocused }, enabled = enabled,
        shape = RoundedCornerShape(10.dp), border = BorderStroke(2.dp, if (focused) Ink else if (active) Accent else Outline),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (active) ActionAccent else Surface, contentColor = Ink),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, maxLines = 2, style = MaterialTheme.typography.labelLarge)
    }
}
