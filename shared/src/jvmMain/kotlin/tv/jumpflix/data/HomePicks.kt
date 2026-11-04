// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import java.time.LocalDate
import kotlin.random.Random

fun watchedTitleIds(watched: Set<String>): Set<String> = watched.mapNotNull { key ->
    val parts = key.split(':', limit = 3)
    if (parts.size >= 2 && (parts[0] == "movie" || (parts[0] == "series" && parts.size == 2))) parts.take(2).joinToString(":") else null
}.toSet()

fun continueWatching(items: List<CatalogItem>, progress: List<Progress>, watched: Set<String>): List<CatalogItem> {
    val watchedTitles = watchedTitleIds(watched)
    val ids = progress.filter { entry ->
        val episodeId = entry.key.substringAfterLast(":ep:", "")
        entry.inProgress && entry.itemId !in watchedTitles &&
            (episodeId.isEmpty() || "${entry.itemId}:ep:$episodeId" !in watched)
    }.map { it.itemId }.distinct()
    return ids.mapNotNull { id -> items.find { it.id == id } }
}

fun dailyFeatured(items: List<CatalogItem>, day: LocalDate, watched: Set<String> = emptySet()): List<CatalogItem> {
    val watchedFilms = watchedTitleIds(watched)
    return items.filter { it.type == "movie" && it.available && !it.paid && !it.explicit && it.thumbnail != null && it.playback != null }
        .sortedBy { it.id }
        .shuffled(Random(day.toEpochDay()))
        .filterNot { it.id in watchedFilms }
        .take(6)
}
