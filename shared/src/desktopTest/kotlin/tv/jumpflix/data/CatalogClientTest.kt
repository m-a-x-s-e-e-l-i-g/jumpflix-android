// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.*

class CatalogClientTest {
    @get:Rule val directory = TemporaryFolder()
    private fun film(id: Int) = JSONObject().put("id", "movie:$id").put("slug", "film-$id").put("type", "movie")
        .put("title", "Film $id").put("url", "https://www.jumpflix.tv/video/film-$id")
    private fun page(vararg ids: Int, next: Int? = null) = JSONObject().put("schemaVersion", 1)
        .put("generatedAt", "2026-11-04").put("items", JSONArray(ids.map(::film))).put("nextOffset", next ?: JSONObject.NULL).toString()
    private fun client(server: MockWebServer, cache: File, seed: String = page(9)) =
        CatalogClient(cache, { seed }, server.url("").toString().trimEnd('/'), "Jumpflix-Test")

    @Test fun `pages merge before a complete validated catalog replaces the cache`() = runBlocking {
        MockWebServer().use { server ->
            val cache = File(directory.root, "nested/catalog.json")
            val client = client(server, cache)
            assertEquals("movie:9", client.saved()!!.items.single().id)
            server.enqueue(MockResponse().setBody(page(1, 2, next = 200)))
            server.enqueue(MockResponse().setBody(page(2, 3)))
            val result = client.refresh()
            assertEquals(listOf("movie:1", "movie:2", "movie:3"), result.items.map { it.id })
            assertEquals(result, client.saved())
            assertEquals("0", server.takeRequest().requestUrl!!.queryParameter("offset"))
            val request = server.takeRequest()
            assertEquals("200", request.requestUrl!!.queryParameter("offset"))
            assertEquals("Jumpflix-Test", request.getHeader("User-Agent"))
            assertNull(request.getHeader("Authorization"))
            assertEquals(listOf("catalog.json"), cache.parentFile.list()!!.toList())
        }
    }

    @Test fun `partial server failure cannot overwrite the usable catalog`() = runBlocking {
        MockWebServer().use { server ->
            val text = page(8)
            val cache = File(directory.root, "catalog.json").apply { writeText(text) }
            server.enqueue(MockResponse().setBody(page(1, next = 200)))
            server.enqueue(MockResponse().setResponseCode(503))
            assertFailsWith<java.io.IOException> { client(server, cache).refresh() }
            assertEquals(text, cache.readText())
        }
    }

    @Test fun `a complete refresh atomically replaces an existing saved catalog`() = runBlocking {
        MockWebServer().use { server ->
            val cache = File(directory.root, "catalog.json").apply { writeText(page(8)) }
            server.enqueue(MockResponse().setBody(page(1)))
            val client = client(server, cache)
            assertEquals("movie:1", client.refresh().items.single().id)
            assertEquals("movie:1", client.saved()!!.items.single().id)
        }
    }

    @Test fun `stalled pagination unknown schemas and malformed titles keep the saved cache`() = runBlocking {
        MockWebServer().use { server ->
            val text = page(8)
            val cache = File(directory.root, "catalog.json").apply { writeText(text) }
            for (response in listOf(page(1, next = 0), JSONObject(page(1)).put("schemaVersion", 2).toString(),
                JSONObject(page(1)).put("items", JSONArray().put(JSONObject().put("id", "broken"))).toString())) {
                server.enqueue(MockResponse().setBody(response))
                assertFails { client(server, cache).refresh() }
                assertEquals(text, cache.readText())
            }
        }
    }

    @Test fun `corrupt local cache falls back to the bundled catalog`() = runBlocking {
        MockWebServer().use { server ->
            val cache = File(directory.root, "catalog.json").apply { writeText("incomplete") }
            assertEquals("movie:9", client(server, cache).saved()!!.visibleItems.single().id)
            assertNull(client(server, cache, "invalid seed").saved())
        }
    }

    @Test fun `series slugs cannot change the request origin or path structure`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(film(1).toString()))
            client(server, File(directory.root, "catalog.json")).series("series/?injected=yes")
            val request = server.takeRequest()
            assertEquals("/api/mobile/v1/series/series%2F%3Finjected%3Dyes", request.path)
            assertNull(request.requestUrl!!.query)
        }
    }
}
