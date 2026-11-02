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

    @Test fun `watched films are excluded across base and provider keys without reshuffling remaining picks`() {
        val original = dailyFeatured(films, day)
        val watched = setOf(original[0].id, "${original[1].id}:yt:previous-source", "${original[2].id}:vimeo:123")
        val picks = dailyFeatured(films, day, watched)
        assertEquals(original.drop(3), picks.take(3))
        assertEquals(6, picks.size)
        assertTrue(picks.none { film -> original.take(3).any { it.id == film.id } })
        assertEquals(original, dailyFeatured(films, day, setOf("${original[0].id}0:yt:neighbor", "series:1:ep:video")))
        assertTrue(dailyFeatured(films, day, films.map { it.id }.toSet()).isEmpty())
    }

    @Test fun `poster badges normalize movie providers without treating an episode as a watched series`() {
        assertEquals(setOf("movie:42", "movie:420", "series:2"), watchedTitleIds(setOf("movie:42", "movie:42:yt:old",
            "movie:420:hls:https://video.example/film.m3u8", "series:1:ep:video", "series:2")))
    }
}
