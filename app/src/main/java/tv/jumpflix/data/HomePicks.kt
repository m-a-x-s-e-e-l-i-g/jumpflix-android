// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import java.time.LocalDate
import kotlin.random.Random

fun watchedTitleIds(watched: Set<String>): Set<String> = watched.mapNotNull { key ->
    val parts = key.split(':', limit = 3)
    if (parts.size >= 2 && (parts[0] == "movie" || (parts[0] == "series" && parts.size == 2))) parts.take(2).joinToString(":") else null
}.toSet()

fun dailyFeatured(items: List<CatalogItem>, day: LocalDate, watched: Set<String> = emptySet()): List<CatalogItem> {
    val watchedFilms = watchedTitleIds(watched)
    return items.filter { it.type == "movie" && it.available && !it.paid && !it.explicit && it.thumbnail != null && it.playback != null }
        .sortedBy { it.id }
        .shuffled(Random(day.toEpochDay()))
        .filterNot { it.id in watchedFilms }
        .take(6)
}
