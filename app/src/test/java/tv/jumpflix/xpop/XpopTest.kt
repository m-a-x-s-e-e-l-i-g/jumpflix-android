// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.xpop

import org.junit.Assert.*
import org.junit.Test
import tv.jumpflix.data.PlaybackSource

class XpopTest {
    @Test fun `weights match website and contribution units are not suggestion counts`() {
        val result = XpopSummary(2, 3, 4, 7)
        assertEquals(10, result.watchingPoints)
        assertEquals(45, result.ratingPoints)
        assertEquals(100, result.reviewPoints)
        assertEquals(70, result.contributionPoints)
        assertEquals(225, result.total)
    }
    @Test fun `completed watching starts at exactly 85 percent and needs a known duration`() {
        assertNull(WatchedItem.completed("movie:172:hls:test", "movie", 84_999, 100_000))
        assertNotNull(WatchedItem.completed("movie:172:hls:test", "movie", 85_000, 100_000))
        assertNull(WatchedItem.completed("movie:172", "movie", 100_000, 0))
        assertNull(WatchedItem.completed("movie:172", "movie", -1, 100_000))
        assertEquals(100.0, WatchedItem("movie:172", "movie").json().getDouble("percent_watched"), 0.0)
    }
    @Test fun `cross device watch keys match website source and episode identities`() {
        assertEquals("movie:42:yt:dQw4w9WgXcQ", watchKey("movie:42", PlaybackSource("youtube", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")))
        assertEquals("movie:42:vimeo:123456", watchKey("movie:42", PlaybackSource("vimeo", "https://vimeo.com/123456/abcdef1234")))
        assertEquals("movie:42:hls:https://example.test/a.m3u8", watchKey("movie:42", PlaybackSource("hls", "https://example.test/a.m3u8")))
        assertEquals("series:18:ep:episode-one", watchKey("series:18", null, "episode-one"))
        assertThrows(IllegalArgumentException::class.java) { watchKey("movie:bad", null) }
    }
}
