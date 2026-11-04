// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal fun musicSearchIndex(rows: JSONArray): Map<String, List<MusicCredit>> {
    val credits = linkedMapOf<String, MutableList<MusicCredit>>()
    for (index in 0 until rows.length()) {
        val row = rows.optJSONObject(index) ?: continue
        val id = row.optLong("video_id").takeIf { it > 0 } ?: continue
        val song = row.optJSONObject("song") ?: continue
        val title = song.nullableString("title") ?: continue
        credits.getOrPut("movie:$id") { mutableListOf() }.add(MusicCredit(title, song.nullableString("artist").orEmpty()))
    }
    return credits.mapValues { it.value.distinct() }
}

/** One paginated public soundtrack index, shared by all catalog search queries. */
class MusicSearchRepository internal constructor(private val cache: File, private val baseUrl: String,
    private val publicKey: String, private val client: OkHttpClient) {
    constructor(context: Context) : this(File(context.filesDir, "music-search-v1.json"), config(context).getString("url"),
        config(context).getString("publishableKey"), OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build())

    suspend fun saved(): Map<String, List<MusicCredit>>? = withContext(Dispatchers.IO) {
        synchronized(cacheLock) { runCatching { musicSearchIndex(JSONArray(cache.readText())) }.getOrNull() }
    }

    suspend fun refresh(): Map<String, List<MusicCredit>> = withContext(Dispatchers.IO) {
        val entries = JSONArray()
        var after = 0L
        repeat(100) {
            val url = "$baseUrl/rest/v1/video_songs".toHttpUrl().newBuilder()
                .addQueryParameter("select", "id,video_id,song:songs!video_songs_song_id_fkey(title,artist)")
                .addQueryParameter("order", "id.asc").addQueryParameter("id", "gt.$after").addQueryParameter("limit", "500").build()
            val request = Request.Builder().url(url).header("apikey", publicKey).header("Accept", "application/json").build()
            val page = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Song credits could not be refreshed.")
                JSONArray(response.body?.string() ?: throw IOException("Song credits response was empty."))
            }
            for (index in 0 until page.length()) entries.put(page.getJSONObject(index))
            if (page.length() < 500) {
                val result = musicSearchIndex(entries)
                synchronized(cacheLock) {
                    val temporary = File(cache.parentFile, "${cache.name}.tmp")
                    temporary.writeText(entries.toString())
                    if (!temporary.renameTo(cache)) { cache.writeText(entries.toString()); temporary.delete() }
                }
                return@withContext result
            }
            val next = page.getJSONObject(page.length() - 1).getLong("id")
            require(next > after) { "Song credit pagination did not advance." }
            after = next
        }
        throw IOException("Song credit pagination exceeded its limit.")
    }

    companion object {
        private val cacheLock = Any()
        private fun config(context: Context) = JSONObject(context.assets.open("auth-config.json").bufferedReader().use { it.readText() })
    }
}
