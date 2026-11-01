// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.player

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tv.jumpflix.data.PlaybackSource

class EmbedTest {
    @Test fun `Vimeo preserves official unlisted privacy hashes and resume offsets`() {
        val source = PlaybackSource.fromJson(JSONObject().put("kind", "vimeo").put("url", "https://vimeo.com/123456/abcdef1234"))!!
        assertEquals("123456", source.providerId)
        assertEquals("abcdef1234", source.vimeoPrivacyHash)
        val html = embedDocument(source, 42)
        assertTrue(html.contains("h=abcdef1234#t=42s"))
        assertTrue(html.indexOf("window.addEventListener") < html.indexOf("<iframe"))
        assertTrue(html.contains("player.ready().catch(jumpflixVimeoError)"))
    }
    @Test fun `Vimeo accepts provider embed links while rejecting spoofed domains and malformed hashes`() {
        val source = PlaybackSource.fromJson(JSONObject().put("kind", "vimeo").put("url", "https://player.vimeo.com/video/123456?h=abcdef1234"))!!
        assertEquals("123456", source.providerId)
        assertEquals("abcdef1234", source.vimeoPrivacyHash)
        assertNull(PlaybackSource.fromJson(JSONObject().put("kind", "vimeo").put("url", "https://vimeo.com.evil.test/123456")))
        assertNull(PlaybackSource("vimeo", "https://vimeo.com/123456?h=bad%22html").vimeoPrivacyHash)
    }
}
