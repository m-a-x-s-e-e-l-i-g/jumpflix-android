// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.desktop

import java.io.File
import java.net.URI
import org.junit.Test
import kotlin.test.*
import tv.jumpflix.data.*

class DesktopTest {
    @Test fun `browser handoff rejects local files credentials and executable schemes`() {
        for (url in listOf("file:///private", "javascript:alert(1)", "http://example.com/video", "https://user:password@example.com/video", "https:///missing-host")) {
            assertFails { browserUri(url) }
        }
        assertEquals("vimeo.com", browserUri("https://vimeo.com/123456/abc123").host)
    }

    @Test fun `paid titles use access page and direct streams use the existing web player`() {
        val film = fixture().items.first { it.type == "movie" }
        val youtube = PlaybackSource("youtube", "https://www.youtube.com/watch?v=abcdefghijk")
        assertEquals(film.url, playbackUrl(film.copy(paid = true, playback = youtube)))
        assertEquals(film.url, playbackUrl(film.copy(paid = false, playback = PlaybackSource("hls", "https://video.example/film.m3u8"))))
        assertEquals(youtube.url, playbackUrl(film.copy(paid = false, playback = youtube)))
    }

    @Test fun `cache paths follow the platform and ignore relative environment paths`() {
        val home = System.getProperty("user.home")
        assertEquals(File(home, "Library/Caches/Jumpflix"), cacheDirectory("Mac OS X", home, emptyMap()))
        assertEquals(File(home, ".cache/Jumpflix"), cacheDirectory("Linux", home, mapOf("XDG_CACHE_HOME" to "relative")))
        assertEquals(File(home, "AppData/Local/Jumpflix"), cacheDirectory("Windows 11", home, emptyMap()))
        assertEquals(File(home, "custom-cache/Jumpflix"), cacheDirectory("Linux", home, mapOf("XDG_CACHE_HOME" to File(home, "custom-cache").path)))
    }

    @Test fun `player preserves Vimeo privacy hash and confines player navigation`() {
        val vimeo = PlaybackSource("vimeo", "https://vimeo.com/123456/abc123")
        PlayerPage(vimeo).use { page ->
            val html = URI(page.url).toURL().readText()
            assertContains(html, "https://player.vimeo.com/video/123456?autoplay=1&h=abc123")
            assertTrue(page.allowsNavigation(requireNotNull(providerEmbed(vimeo))))
            assertFalse(page.allowsNavigation("https://player.vimeo.com/video/654321"))
            assertFalse(page.allowsNavigation("file:///private"))
        }
        PlayerPage(PlaybackSource("hls", "https://example.com/film.m3u8")).use { page ->
            assertContains(URI(page.url + "hls.js").toURL().readText(), "Hls")
        }
    }
}

internal fun fixture(): Catalog = Catalog.parse(requireNotNull(DesktopTest::class.java.getResourceAsStream("/catalog-seed.json")).bufferedReader().use { it.readText() })
