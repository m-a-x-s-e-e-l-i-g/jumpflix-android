// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VideoCreditsTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun `chapters are valid sorted and isolated to the requested episode`() {
        val reference = CreditReference.from("series:42", "abcdefghijk")!!
        assertEquals("series:42:ep:abcdefghijk", reference.playbackKey)
        assertNull(CreditReference.from("series:42"))
        assertNull(CreditReference.from("movie:0"))
        assertNull(CreditReference.from("movie:1:extra"))
        val chapters = JSONObject("""{"chapters":[
          {"spotId":"spot_abcdefgh","startSeconds":20,"endSeconds":35,"playbackKey":"series:42:ep:abcdefghijk","spot":{"name":"Central walls","countries":[{"name":"France"}]}},
          {"spotId":"spot_abcdefgh","startSeconds":0,"endSeconds":10,"playbackKey":"series:42:ep:other"},
          {"spotId":"spot_abcdefgh","startSeconds":-1,"endSeconds":10,"playbackKey":"series:42:ep:abcdefghijk"},
          {"spotId":"../unsafe","startSeconds":1,"endSeconds":10,"playbackKey":"series:42:ep:abcdefghijk"},
          {"spotId":"spot_abcdefgh","startSeconds":40,"endSeconds":40,"playbackKey":"series:42:ep:abcdefghijk"},
          {"spotId":"spot_ijklmnop","startSeconds":5,"endSeconds":10,"playbackKey":"series:42:ep:abcdefghijk"},null]}""")
        val result = parseSpotCredits(chapters, reference)
        assertEquals(listOf(5L, 20L), result.map { it.start })
        assertEquals("France", result.last().country)
        assertEquals("https://parkour.spot/spot/spot_ijklmnop", result.first().url)
        val data = VideoCredits(spots = result)
        assertEquals("Central walls", data.spotAt(20)?.name)
        assertNull(data.spotAt(35))
        assertNull(data.spotAt(10))
    }

    @Test fun `manual songs and repeated timestamps remain credits without guessed timing`() {
        val songs = parseSongCredits(JSONArray("""[
          {"id":1,"start_offset_seconds":0,"song":{"title":"First","artist":"A","spotify_url":"https://open.spotify.com.attacker/track/1234567890123456789012"}},
          {"id":2,"start_offset_seconds":0,"song":{"title":"Second","artist":"B","spotify_track_id":"1234567890123456789012"}},
          {"id":3,"start_offset_seconds":65,"song":{"title":"Third","artist":"C","explicit":true}},
          {"id":4,"start_offset_seconds":null,"song":{"title":"Untimed","artist":"D"}},
          {"id":5,"start_offset_seconds":-1,"song":{"title":"Invalid timing"}},
          {"id":6,"song":null}]"""))
        assertEquals(5, songs.size)
        assertNull(songs.first().spotifyUrl)
        assertEquals("https://open.spotify.com/track/1234567890123456789012", songs[1].spotifyUrl)
        val data = VideoCredits(songs = songs)
        assertNull(data.songAt(0))
        assertNull(data.songAt(64))
        assertEquals("Third", data.songAt(65)?.title)
        assertTrue(data.songAt(65)!!.explicit)
        assertEquals("01:05", creditTime(65))
        assertEquals("1:01:05", creditTime(3665))
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk&t=65", PlaybackSource("youtube", "https://www.youtube.com/watch?v=abcdefghijk").urlAt(65))
        assertEquals("https://vimeo.com/123/abc123#t=65s", PlaybackSource("vimeo", "https://vimeo.com/123/abc123").urlAt(65))
    }

    @Test fun `public queries cache independently and preserve credits on a partial failure`() = runBlocking {
        MockWebServer().use { server ->
            var failSongs = false
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    assertNull(request.getHeader("Authorization"))
                    return if (request.path!!.startsWith("/api/spot-chapters")) {
                        assertNull(request.getHeader("apikey"))
                        assertEquals("1", request.requestUrl!!.queryParameter("mediaId"))
                        MockResponse().setBody("""{"chapters":[{"spotId":"spot_abcdefgh","startSeconds":4,"endSeconds":9,"spot":{"name":"Walls"}}]}""")
                    } else {
                        assertEquals("public-test-key", request.getHeader("apikey"))
                        assertEquals("eq.1", request.requestUrl!!.queryParameter("video_id"))
                        if (failSongs) MockResponse().setResponseCode(503)
                        else MockResponse().setBody("""[{"id":1,"start_offset_seconds":10,"song":{"title":"Track","artist":"Artist"}}]""")
                    }
                }
            }
            val repository = VideoCreditsRepository(File(directory.root, "credits"), server.url("").toString().trimEnd('/'),
                server.url("").toString().trimEnd('/'), "public-test-key", OkHttpClient())
            val reference = CreditReference.from("movie:1")!!
            val first = repository.load(reference)
            assertEquals("Walls", first.spots.single().name)
            assertEquals("Track", first.songs.single().title)
            assertNull(first.songsError)
            repository.load(reference)
            assertEquals(2, server.requestCount)
            failSongs = true
            val partial = repository.load(reference, force = true)
            assertEquals(first.songs, partial.songs)
            assertNotNull(partial.songsError)
            assertNull(partial.spotsError)
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun `series chapter requests use canonical episode key and never query movie soundtracks`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"chapters":[]}"""))
            val repository = VideoCreditsRepository(directory.root, server.url("").toString().trimEnd('/'), server.url("").toString().trimEnd('/'), "public", OkHttpClient())
            val result = repository.load(CreditReference.from("series:42", "episode_id1")!!)
            assertTrue(result.songs.isEmpty())
            assertNull(result.songsError)
            val request = server.takeRequest()
            assertEquals("series:42:ep:episode_id1", request.requestUrl!!.queryParameter("playbackKey"))
            assertEquals(1, server.requestCount)
        }
    }
}
