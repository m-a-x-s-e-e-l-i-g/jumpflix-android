// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.xpop

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import tv.jumpflix.auth.AccountProfile
import tv.jumpflix.auth.AuthSession

class XpopApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: XpopApi
    private val session = AuthSession("private-access-token", "private-refresh-token", Long.MAX_VALUE, AccountProfile("user-a", "a@example.test", "A"))
    @Before fun setup() { server = MockWebServer(); server.start(); api = XpopApi(server.url("/").toString(), "public-key") }
    @After fun cleanup() { server.shutdown() }

    @Test fun `summary targets signed in user and sums approved contribution units with pagination`() {
        server.enqueue(MockResponse().setBody("""{"found":true,"watched_items":2,"ratings_count":3,"reviews_count":4}"""))
        server.enqueue(MockResponse().setBody(JSONArray(List(500) { JSONObject().put("xp_units", 2) }).toString()))
        server.enqueue(MockResponse().setBody("""[{"xp_units":3},{}]"""))
        val result = api.summary(session)
        assertEquals(1004, result.contributions)
        assertEquals(10195, result.total)
        val rpc = server.takeRequest()
        assertEquals("/rest/v1/rpc/user_stats_overview", rpc.path)
        assertEquals("user-a", JSONObject(rpc.body.readUtf8()).getString("target_user"))
        assertEquals("Bearer private-access-token", rpc.getHeader("Authorization"))
        assertEquals("public-key", rpc.getHeader("apikey"))
        val first = server.takeRequest().requestUrl!!
        assertEquals("eq.user-a", first.queryParameter("created_by"))
        assertEquals("eq.approved", first.queryParameter("status"))
        assertEquals("500", server.takeRequest().requestUrl!!.queryParameter("offset"))
    }
    @Test fun `already completed watches do not write again or announce another award`() {
        server.enqueue(MockResponse().setBody("""[{"is_watched":true,"status":"active"}]"""))
        assertFalse(api.markWatched(session, WatchedItem("series:18:ep:video", "episode")))
        assertEquals(1, server.requestCount)
        val url = server.takeRequest().requestUrl!!
        assertEquals("eq.user-a", url.queryParameter("user_id"))
        assertEquals("eq.series:18:ep:video", url.queryParameter("media_id"))
    }
    @Test fun `legacy base watch does not create a second provider watch award`() {
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("""[{"is_watched":true,"status":"active"}]"""))
        assertFalse(api.markWatched(session, WatchedItem("movie:42:yt:video", "movie")))
        server.takeRequest()
        assertEquals("eq.movie:42", server.takeRequest().requestUrl!!.queryParameter("media_id"))
        assertEquals(2, server.requestCount)
    }
    @Test fun `completed watch upsert uses account owned unique key and real progress`() {
        server.enqueue(MockResponse().setBody("[]")); server.enqueue(MockResponse().setResponseCode(201))
        assertTrue(api.markWatched(session, WatchedItem("series:18:ep:video", "episode", 85, 100)))
        server.takeRequest()
        val write = server.takeRequest()
        assertEquals("user_id,media_id", write.requestUrl!!.queryParameter("on_conflict"))
        assertTrue(write.getHeader("Prefer")!!.contains("merge-duplicates"))
        val body = JSONObject(write.body.readUtf8())
        assertEquals("user-a", body.getString("user_id"))
        assertTrue(body.getBoolean("is_watched"))
        assertEquals(85, body.getInt("position_seconds"))
        assertFalse(body.toString().contains("token"))
    }
    @Test fun `rating and review edits upsert the same row instead of adding points locally`() {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(201)) }
        api.saveRating(session, 42, 8)
        api.saveReview(session, 42, " A public review. ")
        repeat(2) { index ->
            val request = server.takeRequest()
            assertEquals("user_id,media_id", request.requestUrl!!.queryParameter("on_conflict"))
            val body = JSONObject(request.body.readUtf8())
            assertEquals("user-a", body.getString("user_id"))
            assertEquals(42, body.getLong("media_id"))
            if (index == 1) assertEquals("A public review.", body.getString("body"))
        }
        assertThrows(IllegalArgumentException::class.java) { api.saveRating(session, 42, 11) }
        assertThrows(IllegalArgumentException::class.java) { api.saveReview(session, 42, " ") }
        assertThrows(IllegalArgumentException::class.java) { api.saveReview(session, 42, "a".repeat(2001)) }
        assertEquals(2, server.requestCount)
    }
    @Test fun `failed writes cannot return success and do not expose server diagnostics`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("private diagnostic"))
        val error = assertThrows(XpopFailure::class.java) { api.saveRating(session, 42, 5) }
        assertEquals(403, error.status)
        assertFalse(error.message!!.contains("private"))
    }
    @Test fun `detail reads are scoped and reject neighboring numeric watch prefixes`() {
        server.enqueue(MockResponse().setBody("""[{"rating":8}]"""))
        server.enqueue(MockResponse().setBody("""[{"body":"Existing review"}]"""))
        server.enqueue(MockResponse().setBody("""[{"media_id":"movie:42:yt:video"},{"media_id":"movie:420:yt:other"}]"""))
        val result = api.details(session, "movie:42")
        assertEquals(setOf("movie:42:yt:video"), result.watched)
        assertEquals(8, result.rating)
        assertEquals("Existing review", result.review)
        repeat(3) { assertEquals("eq.user-a", server.takeRequest().requestUrl!!.queryParameter("user_id")) }
    }
}
