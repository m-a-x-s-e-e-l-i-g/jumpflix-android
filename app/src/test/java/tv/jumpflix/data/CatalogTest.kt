// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CatalogTest {
    @Test fun `bundled real catalog follows the client contract`() {
        val catalog = Catalog.parse(java.io.File("src/main/assets/catalog-seed.json").readText())
        assertTrue(catalog.items.isNotEmpty())
        assertEquals(catalog.items.size, catalog.items.map { it.id }.distinct().size)
        assertTrue(catalog.items.all { it.url.startsWith("https://www.jumpflix.tv/") })
        assertTrue(catalog.items.filter { it.paid }.all { it.playback == null || it.playback.kind == "external" })
        assertTrue(catalog.items.filter { !it.available }.all { it.playback == null })
        catalog.items.firstOrNull { it.title == "KEVIN FRANZÉN" }?.let { assertTrue(it.matches("kevin franzen")) }
    }

    @Test fun `unsafe sources and spoofed provider hosts are rejected`() {
        fun source(kind: String, url: String) = PlaybackSource.fromJson(JSONObject().put("kind", kind).put("url", url))
        assertNull(source("direct", "file:///private"))
        assertNull(source("direct", "https://user:password@example.com/video.mp4"))
        assertNull(source("youtube", "https://youtube.com.attacker.example/watch?v=abcdefghijk"))
        assertNull(source("youtube", "https://www.youtube.com/watch?v=bad<script>"))
        assertEquals("abcdefghijk", source("youtube", "https://www.youtube.com/watch?v=abcdefghijk")?.providerId)
        assertEquals("123456", source("vimeo", "https://vimeo.com/123456")?.providerId)
        assertTrue(source("hls", "https://video.example/film.m3u8")!!.native)
    }

    @Test fun `unknown schema does not replace a usable catalog`() {
        assertThrows(IllegalArgumentException::class.java) { Catalog.parse("""{"schemaVersion":2,"generatedAt":"2026-11-01","items":[]}""") }
    }

    @Test fun `missing optionals and sparse episode positions are preserved`() {
        val item = CatalogItem.fromJson(JSONObject("""{"id":"series:1","slug":"series","type":"series","title":"Series","url":"https://www.jumpflix.tv/series/series","seasons":[{"number":2,"title":"Extras","episodes":[{"id":"abcdefghijk","title":"Third","position":3}]}]}"""))
        assertEquals(3, item.seasons.first().episodes.first().position)
        assertNull(item.playback)
        assertNull(item.thumbnail)
        assertTrue(item.matches("series"))
        assertFalse(item.matches("another title"))
    }

    @Test fun `only actual unfinished playback belongs in continue watching`() {
        assertFalse(Progress("a", "a", "A", 0, 100_000, 0).inProgress)
        assertTrue(Progress("a", "a", "A", 30_000, 100_000, 0).inProgress)
        assertFalse(Progress("a", "a", "A", 99_000, 100_000, 0).inProgress)
    }
}
