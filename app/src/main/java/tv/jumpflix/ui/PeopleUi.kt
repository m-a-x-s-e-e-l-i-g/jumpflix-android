// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
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
import androidx.compose.ui.unit.dp
import java.util.Locale
import tv.jumpflix.data.*

@Composable
internal fun PersonProfileScreen(person: Person, films: List<CatalogItem>, tv: Boolean, cached: Boolean,
    back: () -> Unit, select: (CatalogItem) -> Unit) {
    val context = LocalContext.current
    val repository = remember { PersonRepository(context.applicationContext) }
    var links by remember(person.key) { mutableStateOf(PersonLinks()) }
    var loading by remember(person.key) { mutableStateOf(true) }
    var attempt by remember(person.key) { mutableIntStateOf(0) }
    LaunchedEffect(person.key, attempt) {
        loading = true
        links = repository.links(person.key, force = attempt > 0)
        loading = false
    }
    PersonProfileContent(person, films, tv, cached, links, loading, { attempt++ }, back, select) { openExternal(context, it) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonProfileContent(person: Person, films: List<CatalogItem>, tv: Boolean, cached: Boolean,
    links: PersonLinks, loading: Boolean, retry: () -> Unit, back: () -> Unit, select: (CatalogItem) -> Unit, open: (String) -> Unit) {
    var filter by rememberSaveable(person.key) { mutableStateOf("All credits") }
    val backFocus = remember(person.key) { FocusRequester() }
    val results = remember(person, films, filter) {
        val ids = when (filter) { "Created by" -> person.created; "Featuring" -> person.featured; else -> person.titles }
        films.filter { it.id in ids }.distinctBy { it.id }.sortedByDescending { it.addedAt }
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(if (tv) 136.dp else 146.dp), modifier = Modifier.fillMaxSize().testTag("person-filmography"),
        contentPadding = PaddingValues(24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "profile-header", span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                AppAction("Back", tv, modifier = Modifier.focusRequester(backFocus), action = back)
                LaunchedEffect(person.key) { if (tv) backFocus.requestFocus() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val initials = person.name.trim().split(Regex("\\s+")).take(2).joinToString("") { it.take(1).uppercase(Locale.ROOT) }
                    Box(Modifier.size(64.dp).background(SurfaceRaised, CircleShape), contentAlignment = Alignment.Center) {
                        Text(initials, color = Ink, style = MaterialTheme.typography.headlineSmall)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PageHeading(person.name)
                        Text(person.role, color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("${person.titles.size} ${if (person.titles.size == 1) "title" else "titles"} in the archive", color = Muted, style = MaterialTheme.typography.bodyMedium)
                if (cached) Text("Credits from the saved catalog", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (loading) Text("Loading profile links…", color = Muted, style = MaterialTheme.typography.bodySmall)
                if (links.instagram.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    links.instagram.forEach { handle -> AppAction("Instagram · @$handle", tv) { open("https://www.instagram.com/$handle/") } }
                }
                links.error?.let {
                    Text(it, color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (!loading) AppAction("Retry profile links", tv, action = retry)
                }
                Text("Filmography", color = Ink, style = MaterialTheme.typography.titleLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val filters = listOf("All credits" to person.titles.size, "Created by" to person.created.size, "Featuring" to person.featured.size).filter { it.second > 0 }
                    items(filters, key = { it.first }) { (label, count) ->
                        PersonFilter("$label ($count)", filter == label, tv) { filter = label }
                    }
                }
            }
        }
        gridItems(results, key = { it.id }) { film -> FilmCard(film, tv) { select(film) } }
        if (results.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("No matching titles in this catalog.", color = Muted) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeopleCredits(title: String, names: List<String>, tv: Boolean, profile: (String) -> Unit) {
    val people = remember(names) { names.map(String::trim).filter(String::isNotBlank).distinctBy(::personKey) }
    if (people.isEmpty()) return
    var expanded by remember(names) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (if (expanded) people else people.take(6)).forEach { name ->
                AppAction(name, tv, modifier = Modifier.semantics { contentDescription = "Open profile: $name" }) { profile(personKey(name)) }
            }
            if (people.size > 6) AppAction(if (expanded) "Show fewer" else "Show all ${people.size}", tv) { expanded = !expanded }
        }
    }
}

@Composable
internal fun PersonSearchResult(person: Person, tv: Boolean, profile: (String) -> Unit) {
    Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AppAction(person.name, tv, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Open profile: ${person.name}" }) { profile(person.key) }
        Text("${person.role} · ${person.titles.size} ${if (person.titles.size == 1) "title" else "titles"}", color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
internal fun PersonFilter(label: String, selected: Boolean, tv: Boolean, action: () -> Unit) {
    if (tv) AppAction(label, true, primary = selected, action = action)
    else FilterChip(selected, action, { Text(label) }, modifier = Modifier.heightIn(min = 48.dp),
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SurfaceRaised, selectedLabelColor = Ink))
}
