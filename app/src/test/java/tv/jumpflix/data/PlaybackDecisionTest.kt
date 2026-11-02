// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import org.junit.Assert.*
import org.junit.Test
import tv.jumpflix.xpop.watchKey

class PlaybackDecisionTest {
    private val film = Catalog.parse(java.io.File("src/main/assets/catalog-seed.json").readText()).items.first { it.slug == "kevin-franzen-2026" }
    private val progress = Progress(film.id, film.id, film.title, 90_000, 720_000, 0)

    @Test fun `watched status wins over a partial resume and rewatch explicitly starts from zero`() {
        val action = moviePlaybackAction(film, progress, setOf(watchKey(film.id, film.playback)))
        assertEquals("Rewatch", action.label)
        assertEquals(0L, action.startSeconds)
        assertEquals("Rewatch", moviePlaybackAction(film, progress, setOf(film.id)).label)
        assertEquals(MoviePlaybackAction("Rewatch", 0), moviePlaybackAction(film, progress, setOf("${film.id}:yt:previous-source")))
    }

    @Test fun `unwatched unfinished films resume and other titles do not affect the decision`() {
        val action = moviePlaybackAction(film, progress, setOf("movie:999"))
        assertEquals("Resume", action.label)
        assertNull(action.startSeconds)
        assertEquals("Watch film", moviePlaybackAction(film, null, emptySet()).label)
        assertEquals("Open official provider", moviePlaybackAction(film.copy(paid = true), progress, setOf(film.id)).label)
    }
}
