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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
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
import kotlinx.coroutines.launch
import tv.jumpflix.player.PlaybackActivity
import tv.jumpflix.xpop.*
import tv.jumpflix.ui.nuvio.LightweightCard

private enum class Destination(val title: String, val icon: ImageVector) {
    Home("Home", Icons.Outlined.Home), Search("Search", Icons.Outlined.Search),
    Library("My list", Icons.Outlined.Bookmarks), Account("Account", Icons.Outlined.PersonOutline), About("About", Icons.Outlined.Info),
}

private val BrowseTrailSaver = listSaver<BrowseTrail, String>(save = { it.routes }, restore = { BrowseTrail(it) })

@Composable
fun JumpflixApp(model: CatalogViewModel, television: Boolean, initialSlug: String?) {
    val state by model.state.collectAsStateWithLifecycle()
    val account by model.account.collectAsStateWithLifecycle()
    val xpop by model.xpop.collectAsStateWithLifecycle()
    val interaction by model.interaction.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    var trail by rememberSaveable(stateSaver = BrowseTrailSaver) { mutableStateOf(BrowseTrail()) }
    val profileStates = rememberSaveableStateHolder()
    val people = remember(state.catalog?.items) { buildPeopleIndex(state.catalog?.items.orEmpty()) }
    var accountNavigationVersion by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var peopleMode by rememberSaveable { mutableStateOf(false) }
    var deepLinkHandled by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val phoneRail = !television && configuration.screenWidthDp >= 600 && configuration.screenWidthDp > configuration.screenHeightDp
    fun navigate(entry: Destination) {
        trail = BrowseTrail()
        model.closeDetail()
        if (entry == Destination.Account) accountNavigationVersion++
        destination = entry
    }
    fun selectFilm(item: CatalogItem) {
        trail = if (trail.person != null) trail.openFilm(item.id) else BrowseTrail()
        model.select(item)
    }
    fun openProfile(key: String) { trail = trail.openPerson(state.selected?.id, key) }
    fun browseBack() {
        if (trail.routes.isEmpty()) { model.closeDetail(); return }
        trail = trail.back()
        val film = trail.film
        if (film != null) {
            val parent = state.catalog?.items?.firstOrNull { it.id == film }
            if (parent != null) model.select(parent) else { trail = BrowseTrail(); model.closeDetail() }
        } else if (trail.routes.isEmpty()) model.closeDetail()
    }
    LaunchedEffect(state.selected?.id, account.profile?.id, xpop.summary?.watching) { state.selected?.let(model::loadInteractions) }
    LaunchedEffect(xpop.reward?.id) {
        xpop.reward?.id?.let { id -> kotlinx.coroutines.delay(3000); model.dismissXpop(id) }
    }
    LaunchedEffect(account.googleUrl) {
        account.googleUrl?.let { url ->
            try { CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url)) }
            catch (_: android.content.ActivityNotFoundException) { model.cancelGoogle(); Toast.makeText(context, "Install a browser to continue with Google.", Toast.LENGTH_LONG).show() }
            finally { model.googleOpened() }
        }
    }
    LaunchedEffect(account.oauthResult) {
        if (account.oauthResult > 0) navigate(Destination.Account)
    }
    LaunchedEffect(state.catalog, initialSlug) {
        if (!deepLinkHandled && initialSlug != null) {
            state.catalog?.items?.firstOrNull { it.slug == initialSlug }?.let {
                model.select(it)
                deepLinkHandled = true
            }
        }
    }
    BackHandler(trail.routes.isNotEmpty() || state.selected != null || destination != Destination.Home) {
        if (trail.routes.isNotEmpty() || state.selected != null) browseBack() else destination = Destination.Home
    }
    JumpflixTheme {
        Row(Modifier.fillMaxSize().background(Background).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            if (television) {
                Column(Modifier.width(96.dp).fillMaxHeight().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(painterResource(R.drawable.mascot), "JUMPFLIX popcorn character", Modifier.size(48.dp, 62.dp))
                    Spacer(Modifier.height(6.dp))
                    Destination.entries.forEach { entry ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            androidx.tv.material3.Button(
                                onClick = { navigate(entry) },
                                modifier = Modifier.size(48.dp).semantics { contentDescription = entry.title },
                                contentPadding = PaddingValues(14.dp),
                                colors = androidx.tv.material3.ButtonDefaults.colors(containerColor = if (destination == entry) Surface else Background),
                            ) { androidx.tv.material3.Icon(entry.icon, contentDescription = null) }
                            Text(entry.title, color = if (destination == entry) Ink else Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else if (phoneRail) {
                NavigationRail(Modifier.fillMaxHeight().width(80.dp), containerColor = Background, windowInsets = WindowInsets(0, 0, 0, 0)) {
                    Spacer(Modifier.height(16.dp))
                    Destination.entries.filter { it != Destination.About }.forEach { entry ->
                        NavigationRailItem(selected = destination == entry, onClick = { navigate(entry) },
                            icon = { Icon(entry.icon, null) }, label = { Text(entry.title) },
                            colors = NavigationRailItemDefaults.colors(indicatorColor = SurfaceRaised, selectedIconColor = Accent, selectedTextColor = Ink, unselectedIconColor = Muted, unselectedTextColor = Muted))
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (!television) PhoneMasthead(xpop.summary?.takeIf { xpop.ownerId == account.profile?.id }) { navigate(Destination.Account) }
                Box(Modifier.weight(1f)) {
                    val selected = state.selected
                    val personKey = trail.person
                    if (personKey != null) {
                        val person = people.firstOrNull { it.key == personKey }
                        if (person != null) profileStates.SaveableStateProvider(person.key) {
                            PersonProfileScreen(person, state.catalog?.items.orEmpty(), television, state.cached, ::browseBack, ::selectFilm)
                        }
                        else Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            AppAction("Back", television, action = ::browseBack)
                            Text(if (state.loading) "Loading profile…" else "This profile is unavailable in the current catalog.", color = Muted)
                        }
                    } else if (selected != null) {
                        Details(selected, state, television, ::browseBack, { model.toggleSaved(selected.id) },
                            interaction.takeIf { it.itemId == selected.id && it.ownerId == account.profile?.id } ?: MediaAccountState(), account.profile != null,
                            { model.loadInteractions(selected) }, { navigate(Destination.Account) },
                            { model.rate(selected, it) }, { model.review(selected, it) }, { model.markWatched(selected, it) }, ::openProfile) { source, key, title ->
                            PlaybackActivity.open(context, selected.id, key, title, source)
                        }
                    } else when (destination) {
                        Destination.Home -> Home(state, television, ::selectFilm, model::refresh)
                        Destination.Search -> Search(state.catalog?.items.orEmpty(), people, query, { query = it }, peopleMode, { peopleMode = it }, television, ::selectFilm, ::openProfile)
                        Destination.Library -> Library(state, television, ::selectFilm)
                        Destination.Account -> AccountScreen(account, television, model::signIn, model::signOut, model::restoreAccount, model::beginGoogle, model::cancelGoogle, xpop, model::refreshXpop, navigationVersion = accountNavigationVersion) { destination = Destination.About }
                        Destination.About -> About(state, television, model::refresh)
                    }
                    xpop.reward?.takeIf { xpop.ownerId == account.profile?.id }?.let { XpopNotice(it, Modifier.align(Alignment.TopCenter).padding(16.dp)) }
                }
                if (!television && !phoneRail) {
                    HorizontalDivider(color = Outline.copy(alpha = .5f))
                    NavigationBar(containerColor = Background, tonalElevation = 0.dp) {
                        Destination.entries.filter { it != Destination.About }.forEach { entry ->
                            NavigationBarItem(
                                selected = destination == entry, onClick = { navigate(entry) },
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
internal fun Brand(modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        Image(painterResource(R.drawable.mascot), "JUMPFLIX popcorn character", Modifier.size(if (compact) 32.dp else 49.dp, if (compact) 42.dp else 64.dp))
        Column {
            Text("JUMPFLIX", color = Accent, fontSize = if (compact) 30.sp else 39.sp, letterSpacing = if (compact) 2.sp else 3.sp,
                fontFamily = FontFamily(Font(R.font.bebas_neue)), modifier = Modifier.graphicsLayer { rotationX = 12f; scaleX = 1.04f })
            if (!compact) Text("PARKOUR CINEMA", color = Muted, fontSize = 8.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable
internal fun PhoneMasthead(summary: XpopSummary?, account: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Brand(compact = true)
        if (summary != null) {
            TextButton(account, modifier = Modifier.heightIn(min = 48.dp).widthIn(max = 164.dp).semantics { contentDescription = "Account, ${summary.total} XPOP" }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                XpopMark("${java.text.NumberFormat.getIntegerInstance().format(summary.total)} XPOP")
            }
        } else IconButton(account, Modifier.size(48.dp)) { Icon(Icons.Outlined.PersonOutline, "Open account", tint = Muted) }
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item(key = "hero") {
            if (featured.isNotEmpty()) Hero(featured, tv, select) else Brand(Modifier.padding(24.dp))
        }
        if (state.cached) item(key = "cache") {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.CloudOff, null, tint = Muted, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (state.loading) "Refreshing saved catalog…" else "Browsing saved catalog", color = Muted, style = MaterialTheme.typography.labelLarge)
                }
                if (!state.loading) {
                    if (tv) Action("Retry", false, true, onClick = refresh)
                    else TextButton(refresh, Modifier.heightIn(min = 48.dp)) { Text("Retry", color = Muted) }
                }
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
    val shortScreen = LocalConfiguration.current.screenHeightDp < 500
    Box(Modifier.fillMaxWidth().height(if (tv) 310.dp else if (shortScreen) 280.dp else 350.dp)) {
        FilmBackdrop(item, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Background.copy(alpha = .05f), .3f to Background.copy(alpha = .15f), .65f to Background.copy(alpha = .85f), 1f to Background)))
        if (tv) Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Background.copy(alpha = 0.96f), Background.copy(alpha = 0.6f), Color.Transparent))))
        if (tv) Brand(Modifier.align(Alignment.TopStart).padding(24.dp))
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth(if (tv) 0.68f else 1f).padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!tv) Text(if (item.type == "series") "FEATURED SERIES" else "FEATURED FILM", color = Muted, style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp)
            Text(item.title, color = Ink, fontWeight = FontWeight.Bold, fontSize = if (tv) 34.sp else 30.sp, lineHeight = if (tv) 41.sp else 37.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
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
internal fun FilmCard(item: CatalogItem, tv: Boolean, modifier: Modifier = Modifier, progress: Progress? = null, onClick: () -> Unit) {
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
            Text(item.metadata.ifBlank { if (item.type == "series") "Series" else "Film" }, color = Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun Search(all: List<CatalogItem>, people: List<Person>, query: String, changeQuery: (String) -> Unit, peopleMode: Boolean, changePeopleMode: (Boolean) -> Unit, tv: Boolean, select: (CatalogItem) -> Unit, profile: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    var filter by rememberSaveable { mutableStateOf("All") }
    val matchingPeople = remember(people, query) { people.filter { it.matches(query) } }
    val results = remember(all, query, filter) { all.filter { it.matches(query) && when (filter) { "Films" -> it.type == "movie"; "Series" -> it.type == "series"; "Free" -> !it.paid; else -> true } } }
    LazyVerticalGrid(columns = GridCells.Adaptive(if (tv) 136.dp else 146.dp), modifier = Modifier.fillMaxSize().testTag(if (peopleMode) "people-results" else "film-grid"),
        contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "search-controls", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                PageHeading(if (peopleMode) "Creators & athletes" else "Find your next film", "Search films, creators, and athletes.")
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(query, changeQuery, label = { Text("Title, creator, athlete") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    PersonFilter("Films & series", !peopleMode, tv) { changePeopleMode(false) }
                    PersonFilter("People", peopleMode, tv) { changePeopleMode(true) }
                }
                if (!peopleMode) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                        items(listOf("All", "Films", "Series", "Free")) { name ->
                            if (tv) Action(name, name == filter, true) { filter = name }
                            else FilterChip(selected = name == filter, onClick = { filter = name }, label = { Text(name) }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SurfaceRaised, selectedLabelColor = Ink))
                        }
                    }
                }
                Text(if (peopleMode) "${matchingPeople.size} people" else "${results.size} titles", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = if (peopleMode) 14.dp else 0.dp))
            }
        }
        if (peopleMode) {
            gridItems(matchingPeople, key = { "person:${it.key}" }, span = { GridItemSpan(maxLineSpan) }) { person -> PersonSearchResult(person, tv, profile) }
        } else gridItems(results, key = { it.id }) { film -> FilmCard(film, tv) { select(film) } }
        if ((peopleMode && matchingPeople.isEmpty()) || (!peopleMode && results.isEmpty())) item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (peopleMode) "No people found" else "No films found", color = Ink, style = MaterialTheme.typography.titleLarge)
                Text(if (peopleMode) "Try a creator or athlete's name." else "Try a different title, creator, or athlete.", color = Muted)
            }
        }
    }
}

@Composable
private fun Library(state: CatalogState, tv: Boolean, select: (CatalogItem) -> Unit) {
    val films = state.catalog?.items.orEmpty().filter { it.id in state.saved }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 24.dp)) {
        PageHeading("Your next movie night", "Your list is saved on this device.")
        Spacer(Modifier.height(24.dp))
        if (films.isEmpty()) EmptyState("Make room for a good film", "Open a film and choose Save to my list. It will be waiting here.")
        else FilmGrid(films, tv, select)
    }
}

@Composable
private fun FilmGrid(items: List<CatalogItem>, tv: Boolean, select: (CatalogItem) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(if (tv) 136.dp else 146.dp), modifier = Modifier.testTag("film-grid"), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        gridItems(items, key = { it.id }) { item -> FilmCard(item, tv) { select(item) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Details(item: CatalogItem, state: CatalogState, tv: Boolean, back: () -> Unit, toggleSaved: () -> Unit,
    account: MediaAccountState, signedIn: Boolean, retry: () -> Unit, signIn: () -> Unit, rate: (Int) -> Unit, review: (String) -> Unit,
    markWatched: (Episode?) -> Unit, profile: (String) -> Unit, play: (PlaybackSource, String, String) -> Unit) {
    val context = LocalContext.current
    val focusRequester = remember(item.id) { FocusRequester() }
    val creditsFocus = remember(item.id) { FocusRequester() }
    val reviewsFocus = remember(item.id) { FocusRequester() }
    val detailList = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(item.id) { if (tv) focusRequester.requestFocus() }
    LazyColumn(Modifier.fillMaxSize(), state = detailList, contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(if (tv) 300.dp else 310.dp)) {
                FilmBackdrop(item, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Background.copy(alpha = 0.15f), Background))))
                Box(Modifier.padding(20.dp)) { Action("Back", false, tv, Modifier.focusRequester(focusRequester), back) }
                Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    AsyncImage(item.thumbnail, null, Modifier.width(if (tv) 108.dp else 90.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(Surface), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (item.type == "series") "SERIES" else "FILM", color = Muted, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
                        Text(item.title, color = Ink, fontWeight = FontWeight.Bold, style = if (tv) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        Text(item.metadata, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (item.explicit) Text("Contains mature themes or strong language", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (item.rating != null && item.ratingCount > 0) Text("Bangerometer  ${"%.1f".format(item.rating)}  ·  ${item.ratingCount} ratings", color = Muted, style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.playback?.takeIf { item.available }?.let { source ->
                        val progress = state.progress.firstOrNull { it.key == item.id }
                        Action(if (item.paid) "Open official provider" else if (progress?.inProgress == true) "Resume film" else "Watch film", true, tv) { play(source, item.id, item.title) }
                    }
                    Action(if (item.id in state.saved) "Saved to my list" else "Save to my list", false, tv, onClick = toggleSaved)
                    Action("Reviews", false, tv) {
                        scope.launch { detailList.scrollToItem(1); if (tv) { withFrameNanos { }; reviewsFocus.requestFocus() } }
                    }
                    if (CreditReference.from(item.id) != null) Action("Spots & songs", false, tv) {
                        scope.launch { detailList.scrollToItem(2); if (tv) { withFrameNanos { }; creditsFocus.requestFocus() } }
                    }
                }
                if (!item.available) Text("This title is currently unavailable.", color = Muted)
                if (item.paid) Text("This release stays with its official paid provider.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("About this ${if (item.type == "series") "series" else "film"}", color = Ink, style = MaterialTheme.typography.titleMedium)
                Text(item.description, color = Muted, style = MaterialTheme.typography.bodyLarge)
                PeopleCredits("Created by", item.creators, tv, profile)
                PeopleCredits("Featuring", item.starring, tv, profile)
                if (state.detailLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
                state.detailError?.let { Text(it, color = Muted) }
            }
        }
        item(key = "reviews") { ReviewsSection(item.id, tv, account.review, reviewsFocus) }
        if (item.type == "movie") item(key = "credits") {
            CreditReference.from(item.id)?.let { reference ->
                Box(Modifier.focusRequester(creditsFocus).focusGroup()) {
                    VideoCreditsSection(reference, tv, item.available && !item.paid && item.playback != null) { seconds ->
                        item.playback?.let { PlaybackActivity.open(context, item.id, item.id, item.title, it, seconds) }
                    }
                }
            }
        }
        item { MediaAccountControls(item, account, signedIn, tv, retry, signIn, rate, review) { markWatched(null) } }
        item.seasons.forEach { season ->
            item(key = "season-${season.number}") { Text(season.title, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(24.dp)) }
            items(season.episodes, key = { "${season.number}:${it.id}" }) { episode ->
                var showCredits by remember(item.id, episode.id) { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${episode.position.toString().padStart(2, '0')}  ${episode.title}", color = Ink)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        episode.playback?.let { source -> Action("Play", true, tv) { play(source, "${item.id}:season:${season.number}:ep:${episode.id}", episode.title) } }
                        Action(if (showCredits) "Hide spots & songs" else "Spots & songs", false, tv) { showCredits = !showCredits }
                        if (signedIn && account.loaded) {
                            val watched = watchKey(item.id, null, episode.id) in account.watched
                            XpopAction(if (watched) "Watched" else "Mark watched · +5 XPOP", tv, !account.busy && !watched) { markWatched(episode) }
                        }
                    }
                }
                if (showCredits) CreditReference.from(item.id, episode.id)?.let { reference ->
                    VideoCreditsSection(reference, tv, item.available && !item.paid && episode.playback != null) { seconds ->
                        episode.playback?.let { PlaybackActivity.open(context, item.id, "${item.id}:season:${season.number}:ep:${episode.id}", episode.title, it, seconds) }
                    }
                }
            }
        }
    }
}

@Composable
private fun About(state: CatalogState, tv: Boolean, refresh: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { Brand() }
        item { Text("Parkour cinema,\nwherever you watch.", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineLarge) }
        item { Text("Films, documentaries, and movement stories from the global parkour community. Creators keep their credit, their views, and control of their work.", color = Muted, style = MaterialTheme.typography.bodyLarge) }
        item { Text("${state.catalog?.items?.size ?: 0} titles  ·  ${if (state.cached) "Saved catalog" else "Live catalog"}\n${if (tv) "Android TV" else "Android"}  ·  ${BuildConfig.VERSION_NAME}", color = Muted) }
        item { Action(if (state.loading) "Refreshing…" else "Refresh catalog", true, tv, onClick = refresh) }
        state.error?.let { error -> item { Text(error, color = Muted) } }
        item { Text("Sign in to earn XPOP, rate titles, and post reviews with your existing JUMPFLIX account. Completed watches sync to the website. Saved titles and resume positions stay on this device; casting is still to come.", color = Muted) }
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
        retry?.let { AppAction("Try again", false, primary = true, action = it) }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Action(label: String, primary: Boolean, tv: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppAction(label, tv, primary = primary, modifier = modifier, action = onClick)
}

fun openExternal(context: Context, url: String) {
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    if (uri?.scheme != "https") return
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "No app is available to open this provider.", Toast.LENGTH_LONG).show() }
}
