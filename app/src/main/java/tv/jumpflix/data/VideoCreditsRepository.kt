// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import tv.jumpflix.BuildConfig

/** Reads only approved public chapters and published song credits. No account token is sent. */
class VideoCreditsRepository internal constructor(private val directory: File, private val site: String,
    private val dataUrl: String, private val publicKey: String, private val client: OkHttpClient) {
    constructor(context: Context) : this(File(context.cacheDir, "video-credits"), BuildConfig.CATALOG_BASE_URL,
        config(context).getString("url"), config(context).getString("publishableKey"),
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build())

    suspend fun load(reference: CreditReference, force: Boolean = false): VideoCredits = withContext(Dispatchers.IO) {
        coroutineScope {
            val chapters = async {
                val url = "$site/api/spot-chapters".toHttpUrl().newBuilder()
                    .addQueryParameter("mediaId", reference.mediaId.toString()).addQueryParameter("mediaType", reference.type)
                    .apply { reference.playbackKey?.let { addQueryParameter("playbackKey", it) } }.build()
                part("spots:$reference", force, { get(Request.Builder().url(url).build()) }) { parseSpotCredits(JSONObject(it), reference) }
            }
            val songs = async {
                if (reference.type != "movie") return@async emptyList<SongCredit>() to null
                val url = "$dataUrl/rest/v1/video_songs".toHttpUrl().newBuilder()
                    .addQueryParameter("select", "id,start_offset_seconds,song:songs!video_songs_song_id_fkey(title,artist,spotify_url,spotify_track_id,explicit)")
                    .addQueryParameter("video_id", "eq.${reference.mediaId}").addQueryParameter("order", "start_offset_seconds.asc,id.asc")
                    .addQueryParameter("limit", "1000").build()
                part("songs:${reference.mediaId}", force, { get(Request.Builder().url(url).header("apikey", publicKey).build()) }) { parseSongCredits(JSONArray(it)) }
            }
            val spots = chapters.await()
            val tracks = songs.await()
            VideoCredits(spots.first, tracks.first, spots.second?.let { "Spots could not be refreshed. Check your connection and try again." },
                tracks.second?.let { "Songs could not be refreshed. Check your connection and try again." })
        }
    }

    private fun get(builder: Request): String = client.newCall(builder.newBuilder().header("Accept", "application/json")
        .header("User-Agent", "Jumpflix-Android/${BuildConfig.VERSION_NAME}").build()).execute().use {
        if (!it.isSuccessful) throw IOException("Credits unavailable (${it.code}).")
        it.body?.string() ?: throw IOException("Credits response was empty.")
    }

    private fun <T> part(key: String, force: Boolean, fetch: () -> String, parse: (String) -> List<T>): Pair<List<T>, Exception?> {
        val name = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(directory, "$name.json")
        val cached = runCatching { parse(file.readText()) }.getOrNull()
        if (!force && cached != null && System.currentTimeMillis() - file.lastModified() in 0..600_000) return cached to null
        return try {
            val body = fetch()
            val rows = parse(body)
            // Cache failures must never hide a successfully fetched credit list.
            runCatching { directory.mkdirs(); synchronized(cacheLock) { file.writeText(body) } }
            rows to null
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) { cached.orEmpty() to error }
    }

    companion object {
        private val cacheLock = Any()
        private fun config(context: Context) = JSONObject(context.assets.open("auth-config.json").bufferedReader().use { it.readText() })
    }
}
