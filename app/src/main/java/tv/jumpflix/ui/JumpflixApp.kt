// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import tv.jumpflix.BuildConfig
import tv.jumpflix.R
import tv.jumpflix.CatalogState
import tv.jumpflix.CatalogViewModel
import tv.jumpflix.data.*
import tv.jumpflix.player.PlaybackActivity
import tv.jumpflix.ui.nuvio.LightweightCard

private enum class Destination(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home), Search("Search", Icons.Outlined.Search),
    Library("My list", Icons.Outlined.Bookmarks), Account("Account", Icons.Outlined.PersonOutline), About("About", Icons.Outlined.Info),
}

@Composable
fun JumpflixApp(model: CatalogViewModel, television: Boolean, initialSlug: String?) {
    val state by model.state.collectAsStateWithLifecycle()
    val account by model.account.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var query by rememberSaveable { mutableStateOf("") }
    var deepLinkHandled by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(account.googleUrl) {
        account.googleUrl?.let { url ->
            try { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url)) }
            catch (_: android.content.ActivityNotFoundException) { model.cancelGoogle(); Toast.makeText(context, "Install a browser to continue with Google.", Toast.LENGTH_LONG).show() }
            finally { model.googleOpened() }
        }
    }
    LaunchedEffect(account.oauthResult) {
        if (account.oauthResult > 0) { model.closeDetail(); destination = Destination.Account }
    }
    LaunchedEffect(state.catalog, initialSlug) {
        if (!deepLinkHandled && initialSlug != null) {
            state.catalog?.items?.firstOrNull { it.slug == initialSlug }?.let {
                model.select(it)
                deepLinkHandled = true
            }
        }
    }
    BackHandler(state.selected != null || destination != Destination.Home) {
        if (state.selected != null) model.closeDetail() else destination = Destination.Home
    }
    JumpflixTheme {
        Row(Modifier.fillMaxSize().background(Background).windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (television) {
                Column(Modifier.width(92.dp).fillMaxHeight().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Image(painterResource(R.drawable.mascot), "JUMPFLIX popcorn character", Modifier.size(48.dp, 62.dp))
                    Spacer(Modifier.height(6.dp))
                    Destination.entries.forEach { entry ->
                        androidx.tv.material3.Button(
                            onClick = { model.closeDetail(); destination = entry },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = entry.title },
                            contentPadding = PaddingValues(14.dp),
                            colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = if (destination == entry) Surface else Background),
                        ) { androidx.tv.material3.Icon(entry.icon, contentDescription = entry.title) }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Box(Modifier.weight(1f)) {
                    val selected = state.selected
                    if (selected != null) {
                        Details(selected, state, television, model::closeDetail, { model.toggleSaved(selected.id) }) { source, key, title ->
                            PlaybackActivity.open(context, selected.id, key, title, source)
                        }
                    } else when (destination) {
                        Destination.Home -> Home(state, television, model::select, model::refresh)
                        Destination.Search -> Search(state.catalog?.items.orEmpty(), query, { query = it }, television, model::select)
                        Destination.Library -> Library(state, television, model::select)
                        Destination.Account -> AccountScreen(account, television, model::signIn, model::signOut, model::restoreAccount, model::beginGoogle, model::cancelGoogle)
                        Destination.About -> About(state, television, model::refresh)
                    }
                }
                if (!television) {
                    NavigationBar(containerColor = Background, tonalElevation = 0.dp) {
                        Destination.entries.forEach { entry ->
                            NavigationBarItem(
                                selected = destination == entry, onClick = { model.closeDetail(); destination = entry },
                                icon = { Icon(entry.icon, contentDescription = null) }, label = { Text(entry.title) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = Surface, selectedIconColor = Accent, selectedTextColor = Ink, unselectedIconColor = Muted),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.mascot), "JUMPFLIX popcorn character", Modifier.size(49.dp, 64.dp))
        Column {
            Text("JUMPFLIX", color = Color(0xFFE63B28), fontSize = 39.sp, letterSpacing = 3.sp,
                fontFamily = FontFamily(Font(R.font.bebas_neue)), modifier = Modifier.graphicsLayer { rotationX = 12f; scaleX = 1.04f })
            Text("PARKOUR CINEMA", color = Muted, fontSize = 8.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
private fun Home(state: CatalogState, tv: Boolean, select: (CatalogItem) -> Unit, refresh: () -> Unit) {
    val all = state.catalog?.items.orEmpty()
    val featured = remember(all) {
        all.filter { it.available && !it.paid && !it.explicit && it.thumbnail != null }
            .sortedWith(compareByDescending<CatalogItem> { it.ratingCount >= 3 }.thenByDescending { it.rating ?: 0.0 }.thenByDescending { it.duration.orEmpty().contains('h') })
            .take(6)
    }
    if (all.isEmpty()) {
        EmptyState(if (state.loading) "Loading the archive…" else "The archive is taking a break", state.error ?: "Finding films and the people behind them.", if (state.loading) null else refresh)
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item(key = "hero") {
            if (featured.isNotEmpty()) Hero(featured, tv, select) else Brand(Modifier.padding(24.dp))
        }
        if (state.cached) item(key = "cache") {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (state.loading) "Refreshing saved catalog…" else "Browsing saved catalog", color = Muted, style = MaterialTheme.typography.labelLarge)
                    Text("Saved ${state.catalog?.generatedAt?.take(10).orEmpty()}", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                if (!state.loading) Action("Retry", false, tv, onClick = refresh)
            }
        }
        val continueIds = state.progress.filter { it.inProgress }.map { it.itemId }.distinct()
        val continuing = continueIds.mapNotNull { id -> all.find { it.id == id } }
        if (continuing.isNotEmpty()) item(key = "continue") { FilmRow("Continue watching", continuing, tv, state.progress, select) }
        item(key = "recent") { FilmRow("New to the archive", all.sortedByDescending { it.addedAt }.take(24), tv, state.progress, select) }
        val docs = all.filter { it.category == "documentary" }
        if (docs.isNotEmpty()) item(key = "docs") { FilmRow("Stories behind the movement", docs, tv, state.progress, select) }
        val series = all.filter { it.type == "series" }
        if (series.isNotEmpty()) item(key = "series") { FilmRow("One more episode", series, tv, state.progress, select) }
        val classics = all.filter { (it.year?.toIntOrNull() ?: Int.MAX_VALUE) <= 2015 }
        if (classics.isNotEmpty()) item(key = "classics") { FilmRow("Where it all began", classics, tv, state.progress, select) }
        item(key = "all") { FilmRow("Explore the archive", all, tv, state.progress, select) }
    }
}

@Composable
private fun Hero(items: List<CatalogItem>, tv: Boolean, select: (CatalogItem) -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val item = items[index.coerceIn(0, items.lastIndex)]
    Box(Modifier.fillMaxWidth().height(if (tv) 310.dp else 470.dp)) {
        AsyncImage(item.thumbnail, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Background.copy(alpha = 0.15f), Background.copy(alpha = 0.25f), Background))))
        if (tv) Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Background.copy(alpha = 0.96f), Background.copy(alpha = 0.6f), Color.Transparent))))
        Brand(Modifier.align(Alignment.TopStart).padding(24.dp))
        Column(
            Modifier.align(if (tv) Alignment.BottomStart else Alignment.BottomCenter).fillMaxWidth(if (tv) 0.68f else 1f).padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = if (tv) Alignment.Start else Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!tv) Text("FROM THE PARKOUR ARCHIVE", color = Ink, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
            Text(item.title, color = Ink, fontWeight = FontWeight.Black, fontSize = if (tv) 34.sp else 32.sp, lineHeight = if (tv) 41.sp else 38.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = if (tv) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.Center)
            Text(item.metadata, color = Muted, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Action("Discover film", true, tv) { select(item) }
                Action("Next pick", false, tv) { index = (index + 1) % items.size }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items.indices.forEach { dot -> Box(Modifier.size(if (index == dot) 18.dp else 6.dp, 4.dp).clip(RoundedCornerShape(4.dp)).background(if (index == dot) Ink else Muted.copy(alpha = 0.35f))) }
            }
        }
    }
}

@Composable
private fun FilmRow(title: String, films: List<CatalogItem>, tv: Boolean, progress: List<Progress>, select: (CatalogItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Ink, fontSize = if (tv) 19.sp else 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(films, key = { it.id }) { item ->
                FilmCard(item, tv, Modifier.width(if (tv) 136.dp else 128.dp), progress.firstOrNull { it.itemId == item.id }) { select(item) }
            }
        }
    }
}

@Composable
private fun FilmCard(item: CatalogItem, tv: Boolean, modifier: Modifier = Modifier, progress: Progress? = null, onClick: () -> Unit) {
    val content: @Composable ColumnScope.() -> Unit = {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Surface)) {
            AsyncImage(item.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (item.paid || !item.available) Text(if (!item.available) "UNAVAILABLE" else "PAID", color = Ink, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).background(Background.copy(alpha = 0.85f), RoundedCornerShape(4.dp)).padding(5.dp))
            if (progress?.inProgress == true && progress.durationMs > 0) {
                LinearProgressIndicator(progress = { (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) }, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), color = Accent, trackColor = Surface)
            }
        }
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.metadata.ifBlank { if (item.type == "series") "Series" else "Film" }, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (tv) {
        LightweightCard(onClick, RoundedCornerShape(10.dp), Background, modifier.semantics { contentDescription = item.title },
            focusedBorder = androidx.tv.material3.Border(BorderStroke(2.dp, Ink), shape = RoundedCornerShape(10.dp)), content = content)
    } else {
        Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).semantics { contentDescription = item.title }, content = content)
    }
}

@Composable
private fun Search(all: List<CatalogItem>, query: String, changeQuery: (String) -> Unit, tv: Boolean, select: (CatalogItem) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    var filter by rememberSaveable { mutableStateOf("All") }
    val results = remember(all, query, filter) { all.filter { it.matches(query) && when (filter) { "Films" -> it.type == "movie"; "Series" -> it.type == "series"; "Free" -> !it.paid; else -> true } } }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 24.dp)) {
        Text("Find your next film", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(query, changeQuery, label = { Text("Title, creator, athlete") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 12.dp)) {
            listOf("All", "Films", "Series", "Free").forEach { name -> Action(name, name == filter, tv) { filter = name } }
        }
        Text("${results.size} titles", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 14.dp))
        if (results.isEmpty()) EmptyState("No films found", "Try a different title, creator, or athlete.")
        else FilmGrid(results, tv, select)
    }
}

@Composable
private fun Library(state: CatalogState, tv: Boolean, select: (CatalogItem) -> Unit) {
    val films = state.catalog?.items.orEmpty().filter { it.id in state.saved }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 24.dp)) {
        Text("Your next movie night", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
        Text("Your list is saved on this device.", color = Muted, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        if (films.isEmpty()) EmptyState("Make room for a good film", "Open a film and choose Save to my list. It will be waiting here.")
        else FilmGrid(films, tv, select)
    }
}

@Composable
private fun FilmGrid(items: List<CatalogItem>, tv: Boolean, select: (CatalogItem) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(if (tv) 136.dp else 146.dp), contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        gridItems(items, key = { it.id }) { item -> FilmCard(item, tv) { select(item) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(item: CatalogItem, state: CatalogState, tv: Boolean, back: () -> Unit, toggleSaved: () -> Unit, play: (PlaybackSource, String, String) -> Unit) {
    val context = LocalContext.current
    val focusRequester = remember(item.id) { FocusRequester() }
    LaunchedEffect(item.id) { if (tv) focusRequester.requestFocus() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(if (tv) 270.dp else 260.dp)) {
                AsyncImage(item.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Background.copy(alpha = 0.15f), Background))))
                Box(Modifier.padding(20.dp)) { Action("Back", false, tv, Modifier.focusRequester(focusRequester), back) }
            }
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(item.title, color = Ink, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineLarge)
                Text(item.metadata, color = Muted)
                if (item.explicit) Text("Contains mature themes or strong language", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (item.rating != null && item.ratingCount > 0) Text("Bangerometer  ${"%.1f".format(item.rating)}  ·  ${item.ratingCount} ratings", color = Muted, style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.playback?.takeIf { item.available }?.let { source ->
                        val progress = state.progress.firstOrNull { it.key == item.id }
                        Action(if (item.paid) "Open official provider" else if (progress?.inProgress == true) "Resume film" else "Watch film", true, tv) { play(source, item.id, item.title) }
                    }
                    Action(if (item.id in state.saved) "Saved to my list" else "Save to my list", false, tv, onClick = toggleSaved)
                    Action("Open on JUMPFLIX", false, tv) { openExternal(context, item.url) }
                }
                if (!item.available) Text("This title is currently unavailable.", color = Muted)
                if (item.paid) Text("This release stays with its official paid provider.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(item.description, color = Ink, style = MaterialTheme.typography.bodyLarge)
                if (item.creators.isNotEmpty()) Text("Created by  ${item.creators.joinToString(", ")}", color = Muted)
                if (item.starring.isNotEmpty()) Text("Featuring  ${item.starring.joinToString(", ")}", color = Muted)
                if (state.detailLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
                state.detailError?.let { Text(it, color = Muted) }
            }
        }
        item.seasons.forEach { season ->
            item(key = "season-${season.number}") { Text(season.title, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(24.dp)) }
            items(season.episodes, key = { "${season.number}:${it.id}" }) { episode ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(episode.position.toString().padStart(2, '0'), color = Muted, fontSize = 20.sp)
                    Text(episode.title, color = Ink, modifier = Modifier.weight(1f))
                    episode.playback?.let { source -> Action("Play", true, tv) { play(source, "${item.id}:season:${season.number}:ep:${episode.id}", episode.title) } }
                }
            }
        }
    }
}

@Composable
private fun About(state: CatalogState, tv: Boolean, refresh: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { Brand() }
        item { Text("Parkour cinema,\nwherever you watch.", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineLarge) }
        item { Text("Films, documentaries, and movement stories from the global parkour community. Creators keep their credit, their views, and control of their work.", color = Muted, style = MaterialTheme.typography.bodyLarge) }
        item { Text("${state.catalog?.items?.size ?: 0} titles  ·  ${if (state.cached) "Saved catalog" else "Live catalog"}\n${if (tv) "Android TV" else "Android"}  ·  ${BuildConfig.VERSION_NAME}", color = Muted) }
        item { Action(if (state.loading) "Refreshing…" else "Refresh catalog", true, tv, onClick = refresh) }
        state.error?.let { error -> item { Text(error, color = Muted) } }
        item { Text("Sign in to your existing JUMPFLIX account in the Account tab. Saved titles and watch progress stay on this device in this build; account history sync, ratings, reviews, maps, and casting are still to come.", color = Muted) }
        item { HorizontalDivider(color = Surface) }
        item { Text("Open source & acknowledgments", color = Ink, fontWeight = FontWeight.Bold) }
        item { Text("This client is licensed under GNU GPL v3. Its TV focus card is adapted from NuvioTV by the Nuvio contributors. The interface follows Nuvio’s cinema-first browsing, with JUMPFLIX branding and catalog. Noto Sans is licensed under the SIL Open Font License. Gradle wrapper: Apache License 2.0.", color = Muted) }
        item { Action("NuvioTV source", false, tv) { openExternal(context, "https://github.com/NuvioMedia/NuvioTV") } }
        item { Text("Video, artwork, and provider assets remain with their respective owners. Source references and licenses are included in this app’s source repository.", color = Muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun EmptyState(title: String, body: String, retry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.Start) {
        Spacer(Modifier.weight(1f))
        Image(painterResource(if (retry != null) R.drawable.mascot_sad else R.drawable.mascot_search), "JUMPFLIX popcorn character", Modifier.size(92.dp, 112.dp))
        Text(title, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
        Text(body, color = Muted)
        retry?.let { Button(it) { Text("Try again") } }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Action(label: String, primary: Boolean, tv: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (tv) {
        androidx.tv.material3.Button(onClick, modifier, colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = if (primary) Accent else Surface, contentColor = Ink)) { androidx.tv.material3.Text(label) }
    } else {
        Button(onClick, modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = if (primary) Accent else Surface, contentColor = Ink)) { Text(label) }
    }
}

fun openExternal(context: Context, url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    if (uri?.scheme != "https") return
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "No app is available to open this provider.", Toast.LENGTH_LONG).show() }
}
