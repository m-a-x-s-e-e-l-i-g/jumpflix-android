// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** Same catalog contract and parse-before-cache behavior on Android and desktop. */
class CatalogClient(private val cache: File, private val seed: () -> String,
    private val baseUrl: String, private val userAgent: String,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()) {
    init {
        require(baseUrl.matches(Regex("https?://[a-zA-Z0-9.:-]+"))) { "Catalog URL must be an HTTP(S) origin." }
    }

    suspend fun saved(): Catalog? = withContext(Dispatchers.IO) {
        runCatching { Catalog.parse(cache.readText()) }.getOrNull()
            ?: runCatching { Catalog.parse(seed()) }.getOrNull()
    }

    private fun get(path: String): JSONObject {
        val request = Request.Builder().url(baseUrl + path)
            .header("Accept", "application/json").header("User-Agent", userAgent).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(if (response.code == 404) "The app catalog API is not available on this server yet." else "Catalog server returned ${response.code}.")
            JSONObject(response.body?.string() ?: throw IOException("The catalog response was empty."))
        }
    }

    suspend fun refresh(): Catalog = withContext(Dispatchers.IO) {
        val entries = LinkedHashMap<String, JSONObject>()
        var offset = 0
        repeat(100) {
            val page = get("/api/mobile/v1/catalog?offset=$offset&limit=200")
            require(page.getInt("schemaVersion") == 1) { "This catalog needs a newer JUMPFLIX app." }
            page.objects("items").forEach { entries[it.getString("id")] = it }
            if (page.isNull("nextOffset")) {
                val text = JSONObject().put("schemaVersion", 1).put("generatedAt", page.getString("generatedAt"))
                    .put("items", JSONArray(entries.values.toList())).toString()
                val catalog = Catalog.parse(text)
                val directory = cache.absoluteFile.parentFile!!
                check(directory.isDirectory || directory.mkdirs()) { "Could not create the catalog cache directory." }
                val temporary = File.createTempFile("catalog-", ".tmp", directory)
                try {
                    temporary.writeText(text)
                    try { Files.move(temporary.toPath(), cache.toPath(), ATOMIC_MOVE, REPLACE_EXISTING) }
                    catch (_: AtomicMoveNotSupportedException) { Files.move(temporary.toPath(), cache.toPath(), REPLACE_EXISTING) }
                } finally { temporary.delete() }
                return@withContext catalog
            }
            val next = page.getInt("nextOffset")
            require(next > offset) { "Catalog pagination did not advance." }
            offset = next
        }
        throw IOException("Catalog pagination exceeded its limit.")
    }

    suspend fun series(slug: String): CatalogItem = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(slug, "UTF-8").replace("+", "%20")
        CatalogItem.fromJson(get("/api/mobile/v1/series/$encoded"))
    }
}
