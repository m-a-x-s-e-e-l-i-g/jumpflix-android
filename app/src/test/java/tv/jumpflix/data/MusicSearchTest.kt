// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MusicSearchTest {
    @get:Rule val directory = TemporaryFolder()
    private fun row(id: Int, title: String = "O Mar Apaga") = JSONObject().put("id", id).put("video_id", 314)
        .put("song", JSONObject().put("title", title).put("artist", "Banda Eddie"))

    @Test fun `song titles and artists participate in the existing accent insensitive token search`() {
        val film = Catalog.parse(java.io.File("src/main/assets/catalog-seed.json").readText()).items.first { it.id == "movie:314" }
        val music = listOf(MusicCredit("Été à Paris", "Banda Eddie"))
        assertTrue(film.matches("ete paris", music))
        assertTrue(film.matches("peanutbutter eddie", music))
        assertFalse(film.matches("ete paris"))
        assertFalse(film.matches("ete unrelated", music))
        val renamed = film.copy(title = "Fresh replacement", description = "", creators = emptyList(), starring = emptyList())
        assertTrue(renamed.matches("fresh replacement"))
        assertFalse(renamed.matches("peanutbutter"))
        val changedCredit = listOf(music.single().copy(title = "New song", artist = "Other artist"))
        assertTrue(film.matches("new song", changedCredit))
        assertFalse(film.matches("ete paris", changedCredit))
        assertTrue(musicSearchIndex(JSONArray().put(row(1)).put(row(2))).getValue("movie:314").size == 1)
    }

    @Test fun `full public index paginates once and is usable offline after reopening`() = runBlocking {
        MockWebServer().use { server ->
            val first = JSONArray().apply { (1..500).forEach { put(row(it)) } }
            server.enqueue(MockResponse().setBody(first.toString()))
            server.enqueue(MockResponse().setBody(JSONArray().put(row(501, "Another song")).toString()))
            val cache = File(directory.root, "music.json")
            val repository = MusicSearchRepository(cache, server.url("").toString().trimEnd('/'), "public-key", OkHttpClient())
            val result = repository.refresh()
            assertEquals(2, result.getValue("movie:314").size)
            val request = server.takeRequest()
            assertEquals("gt.0", request.requestUrl!!.queryParameter("id"))
            assertEquals("500", request.requestUrl!!.queryParameter("limit"))
            assertNull(request.getHeader("Authorization"))
            assertEquals("public-key", request.getHeader("apikey"))
            assertEquals("gt.500", server.takeRequest().requestUrl!!.queryParameter("id"))
            assertEquals(result, MusicSearchRepository(cache, "https://offline.example", "public-key", OkHttpClient()).saved())
        }
    }

    @Test fun `a failed partial download cannot replace the working song cache`() = runBlocking {
        MockWebServer().use { server ->
            val cache = File(directory.root, "music.json").apply { writeText(JSONArray().put(row(1, "Saved song")).toString()) }
            server.enqueue(MockResponse().setBody(JSONArray().apply { (1..500).forEach { put(row(it)) } }.toString()))
            server.enqueue(MockResponse().setResponseCode(503))
            val repository = MusicSearchRepository(cache, server.url("").toString().trimEnd('/'), "public-key", OkHttpClient())
            var failed = false
            try { repository.refresh() } catch (_: java.io.IOException) { failed = true }
            assertTrue(failed)
            assertEquals("Saved song", repository.saved()!!.getValue("movie:314").single().title)
        }
    }
}
