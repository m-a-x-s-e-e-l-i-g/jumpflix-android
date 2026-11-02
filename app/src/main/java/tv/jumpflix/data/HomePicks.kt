// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import java.time.LocalDate
import kotlin.random.Random

fun dailyFeatured(items: List<CatalogItem>, day: LocalDate, watched: Set<String> = emptySet()): List<CatalogItem> {
    val watchedFilms = watched.map { it.split(':', limit = 3).take(2).joinToString(":") }.toSet()
    return items.filter { it.type == "movie" && it.available && !it.paid && !it.explicit && it.thumbnail != null && it.playback != null }
        .sortedBy { it.id }
        .shuffled(Random(day.toEpochDay()))
        .filterNot { it.id in watchedFilms }
        .take(6)
}
