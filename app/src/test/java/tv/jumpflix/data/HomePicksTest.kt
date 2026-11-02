// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HomePicksTest {
    private val films = Catalog.parse(java.io.File("src/main/assets/catalog-seed.json").readText()).items
    private val day = LocalDate.of(2026, 11, 2)

    @Test fun `featured picks are stable for the day and change the next day`() {
        val picks = dailyFeatured(films, day)
        assertEquals(6, picks.size)
        assertEquals(picks, dailyFeatured(films.reversed(), day))
        assertNotEquals(picks, dailyFeatured(films, day.plusDays(1)))
        assertTrue(picks.all { it.type == "movie" && it.available && !it.paid && !it.explicit && it.thumbnail != null && it.playback != null })
    }

    @Test fun `featured selection handles an empty or small eligible archive`() {
        val film = films.first { it.type == "movie" && it.playback != null }.copy(available = true, paid = false, explicit = false, thumbnail = "poster")
        assertEquals(listOf(film), dailyFeatured(listOf(film, film.copy(id = "series:1", type = "series"),
            film.copy(id = "movie:2", paid = true), film.copy(id = "movie:3", available = false),
            film.copy(id = "movie:4", explicit = true), film.copy(id = "movie:5", thumbnail = null),
            film.copy(id = "movie:6", playback = null)), day))
        assertTrue(dailyFeatured(emptyList(), day).isEmpty())
    }
}
