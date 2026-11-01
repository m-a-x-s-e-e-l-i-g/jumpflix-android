// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ReviewsTest {
    private fun row(id: Int, media: Int = 9, author: String = "Reviewer", body: String = "A real review.") =
        """{"id":$id,"media_id":$media,"author_name":"$author","body":"$body","created_at":"2026-10-01T10:00:00+02:00","updated_at":"2026-10-02T10:00:00+02:00"}"""

    @Test fun `public review parsing isolates titles strips email domains and returns a stable cursor`() {
        val page = parseReviews("[${row(30, author = "name@example.com")},${row(29, author = "")},${row(28)},${row(27, media = 8)},null]", 9, 2)
        assertEquals(listOf(30L, 29L), page.reviews.map { it.id })
        assertEquals("name", page.reviews[0].author)
        assertEquals("Anonymous", page.reviews[1].author)
        assertEquals(29L, page.next!!.id)
        assertEquals("2026-10-02T08:00:00Z", page.next!!.updated.toString())
        assertEquals("2026-10-01T08:00:00Z", page.reviews.first().created.toString())
        assertThrows(IllegalArgumentException::class.java) { reviewMediaId("movie:9:ep:x") }
        assertThrows(IllegalArgumentException::class.java) { reviewMediaId("movie:0") }
        assertEquals(9L, reviewMediaId("series:9"))
    }

    @Test fun `malformed entries and blank bodies are skipped without inventing reviews`() {
        val page = parseReviews("[${row(0)},${row(2, body = " ")},{\"id\":3,\"media_id\":9,\"body\":\"Broken date\",\"updated_at\":\"invalid\"}]", 9, 10)
        assertTrue(page.reviews.isEmpty())
        assertNull(page.next)
        assertFalse(ReviewFeed(error = "Unavailable").loaded)
    }

    @Test fun `loading more deduplicates while a refresh replaces edited and deleted reviews`() {
        val first = parseReviews("[${row(30)},${row(29)},${row(28)}]", 9, 2)
        val feed = ReviewFeed().accept(first, true)
        val appended = feed.accept(parseReviews("[${row(29)},${row(28)}]", 9, 10), false)
        assertEquals(listOf(30L, 29L, 28L), appended.reviews.map { it.id })
        val refreshed = appended.accept(parseReviews("[${row(30, body = "Updated review.")}]", 9, 10), true)
        assertEquals(1, refreshed.reviews.size)
        assertEquals("Updated review.", refreshed.reviews.single().body)
        assertNull(refreshed.next)
    }

    @Test fun `anonymous public requests paginate by title timestamp and id without account data`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("[${row(30)},${row(29)},${row(28)}]"))
            server.enqueue(MockResponse().setBody("[${row(28)}]"))
            val repository = ReviewRepository(server.url("").toString().trimEnd('/'), "public-key", OkHttpClient())
            val first = repository.page("movie:9", pageSize = 2)
            val request = server.takeRequest()
            assertEquals("/rest/v1/reviews_with_author", request.requestUrl!!.encodedPath)
            assertEquals("eq.9", request.requestUrl!!.queryParameter("media_id"))
            assertEquals("updated_at.desc,id.desc", request.requestUrl!!.queryParameter("order"))
            assertEquals("3", request.requestUrl!!.queryParameter("limit"))
            assertNull(request.getHeader("Authorization"))
            assertEquals("public-key", request.getHeader("apikey"))
            assertFalse(request.requestUrl!!.queryParameter("select")!!.contains("user_id"))
            val second = repository.page("movie:9", first.next, pageSize = 2)
            assertEquals(28L, second.reviews.single().id)
            val nextRequest = server.takeRequest()
            assertEquals("(updated_at.lt.2026-10-02T08:00:00Z,and(updated_at.eq.2026-10-02T08:00:00Z,id.lt.29))", nextRequest.requestUrl!!.queryParameter("or"))
            assertNull(second.next)
        }
    }
}
