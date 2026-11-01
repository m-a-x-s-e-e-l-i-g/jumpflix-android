// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import tv.jumpflix.BuildConfig

class CatalogRepository(private val context: Context) {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(40, TimeUnit.SECONDS).build()
    private val cache get() = File(context.filesDir, "catalog-v1.json")

    suspend fun saved(): Catalog? = withContext(Dispatchers.IO) {
        runCatching { Catalog.parse(cache.readText()) }.getOrNull()
            ?: runCatching { Catalog.parse(context.assets.open("catalog-seed.json").bufferedReader().use { it.readText() }) }.getOrNull()
    }

    private fun get(path: String): JSONObject {
        val request = Request.Builder().url(BuildConfig.CATALOG_BASE_URL + path)
            .header("Accept", "application/json").header("User-Agent", "Jumpflix-Android/${BuildConfig.VERSION_NAME}").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(if (response.code == 404) "The app catalog API is not available on this server yet." else "Catalog server returned ${response.code}.")
            JSONObject(response.body?.string() ?: throw IOException("The catalog response was empty."))
        }
    }

    suspend fun refresh(): Catalog = withContext(Dispatchers.IO) {
        val entries = LinkedHashMap<String, JSONObject>()
        var offset = 0
        var generatedAt = ""
        repeat(100) {
            val page = get("/api/mobile/v1/catalog?offset=$offset&limit=200")
            require(page.getInt("schemaVersion") == 1) { "This catalog needs a newer JUMPFLIX app." }
            page.objects("items").forEach { entries[it.getString("id")] = it }
            generatedAt = page.getString("generatedAt")
            if (page.isNull("nextOffset")) {
                val text = JSONObject().put("schemaVersion", 1).put("generatedAt", generatedAt)
                    .put("items", JSONArray(entries.values.toList())).toString()
                val catalog = Catalog.parse(text)
                // Parse the complete download before replacing a usable cache.
                val temp = File(context.filesDir, "catalog-v1.tmp")
                temp.writeText(text)
                if (!temp.renameTo(cache)) { cache.writeText(text); temp.delete() }
                return@withContext catalog
            }
            val next = page.getInt("nextOffset")
            require(next > offset) { "Catalog pagination did not advance." }
            offset = next
        }
        throw IOException("Catalog pagination exceeded its limit.")
    }

    suspend fun series(slug: String): CatalogItem = withContext(Dispatchers.IO) {
        // Slugs are a single URL path component, never an arbitrary network target.
        val encoded = java.net.URLEncoder.encode(slug, "UTF-8").replace("+", "%20")
        CatalogItem.fromJson(get("/api/mobile/v1/series/$encoded"))
    }
}
