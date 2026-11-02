// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import java.time.LocalDate
import kotlin.random.Random

fun dailyFeatured(items: List<CatalogItem>, day: LocalDate): List<CatalogItem> = items
    .filter { it.type == "movie" && it.available && !it.paid && !it.explicit && it.thumbnail != null && it.playback != null }
    .sortedBy { it.id }
    .shuffled(Random(day.toEpochDay()))
    .take(6)
