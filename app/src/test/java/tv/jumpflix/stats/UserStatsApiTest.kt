// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.stats

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

class UserStatsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: UserStatsApi
    private val session = AuthSession("private-access-token", "private-refresh-token", Long.MAX_VALUE, AccountProfile("owner-a", "a@example.test", "A"))
    @Before fun setup() { server = MockWebServer(); server.start(); api = UserStatsApi(server.url("/").toString(), "public-key") }
    @After fun cleanup() { server.shutdown() }
    private fun response(body: String) { server.enqueue(MockResponse().setBody(body)) }

    @Test fun `stats requests are owner bound and hydrate reviews without privileged credentials`() {
        response("""{"found":true,"watched_items":5,"watched_movies":3,"watched_episodes":2,"ratings_count":3,"reviews_count":1,"average_rating":8.5,"total_position_seconds":7200,"catalog_movies":300,"catalog_episodes":250}""")
        response("""[{"rating":8,"count":2},{"rating":10,"count":1},{"rating":12,"count":99}]""")
        response("""[{"media_id":42,"title":"Film","type":"movie","slug":"film","rating":9}]""")
        response("""[{"id":7,"media_id":42,"body":"Existing public review","created_at":"2026-10-30T10:00:00Z"}]""")
        response("""[{"id":42,"title":"Film","type":"movie","slug":"film"}]""")
        response("""[{"status":"approved","xp_units":2},{"status":"approved"},{"status":"pending"},{"status":"declined"}]""")
        val stats = api.load(session)
        assertEquals(5, stats.overview.watched)
        assertEquals(8.5, stats.overview.averageRating!!, .001)
        assertEquals(2, stats.distribution[7])
        assertEquals(3, stats.distribution.sum())
        assertEquals("Film", stats.recentReviews.single().media!!.title)
        assertEquals(ContributionStats(2, 1, 1, 3), stats.contributions)
        assertEquals(125, stats.xpop.total)
        repeat(3) {
            val request = server.takeRequest()
            assertEquals("owner-a", JSONObject(request.body.readUtf8()).getString("target_user"))
            assertEquals("Bearer private-access-token", request.getHeader("Authorization"))
            assertEquals("public-key", request.getHeader("apikey"))
        }
        val reviews = server.takeRequest()
        assertEquals("eq.owner-a", reviews.requestUrl!!.queryParameter("user_id"))
        assertEquals("20", reviews.requestUrl!!.queryParameter("limit"))
        assertEquals("GET", reviews.method)
        assertEquals("in.(42)", server.takeRequest().requestUrl!!.queryParameter("id"))
        assertEquals("eq.owner-a", server.takeRequest().requestUrl!!.queryParameter("created_by"))
    }
    @Test fun `empty account has ten zero rating bins and no misleading average`() {
        response("""{"found":true}"""); repeat(4) { response("[]") }
        val stats = api.load(session)
        assertEquals(List(10) { 0L }, stats.distribution)
        assertNull(stats.overview.averageRating)
        assertTrue(stats.recentReviews.isEmpty())
        assertTrue(stats.ratedTitles.isEmpty())
        assertEquals(0, stats.xpop.total)
        assertEquals(5, server.requestCount)
    }
    @Test fun `rated titles and contribution status counts paginate beyond server page limits`() {
        response("""{"found":true,"ratings_count":501}"""); response("[]")
        response(JSONArray((1..500).map { JSONObject().put("media_id", it).put("title", "Film $it").put("type", "movie").put("rating", 8) }).toString())
        response("""[{"media_id":501,"title":"Last film","type":"movie","rating":7}]""")
        response("[]")
        response(JSONArray(List(500) { JSONObject().put("status", "pending") }).toString())
        response("""[{"status":"approved","xp_units":2}]""")
        val stats = api.load(session)
        assertEquals(501, stats.ratedTitles.size)
        assertEquals(500, stats.contributions.pending)
        assertEquals(2, stats.contributions.units)
        server.takeRequest(); server.takeRequest(); server.takeRequest()
        val secondPage = server.takeRequest()
        assertEquals("500", secondPage.requestUrl!!.queryParameter("offset"))
        assertEquals("owner-a", JSONObject(secondPage.body.readUtf8()).getString("target_user"))
        server.takeRequest(); server.takeRequest()
        assertEquals("500", server.takeRequest().requestUrl!!.queryParameter("offset"))
    }
    @Test fun `missing account and server failures do not become an empty successful profile`() {
        response("""{"found":false}""")
        assertEquals(401, assertThrows(UserStatsFailure::class.java) { api.load(session) }.status)
        server.enqueue(MockResponse().setResponseCode(403).setBody("private server diagnostic"))
        val failure = assertThrows(UserStatsFailure::class.java) { api.load(session) }
        assertFalse(failure.message!!.contains("private"))
    }
}
