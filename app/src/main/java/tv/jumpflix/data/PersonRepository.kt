// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Public profile links only; filmography comes from the same cached catalog as browsing. */
class PersonRepository internal constructor(private val directory: File, private val baseUrl: String,
    private val publicKey: String, private val client: OkHttpClient) {
    constructor(context: Context) : this(File(context.cacheDir, "people"), config(context).getString("url"),
        config(context).getString("publishableKey"), OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build())

    suspend fun links(slug: String, force: Boolean = false): PersonLinks = withContext(Dispatchers.IO) {
        if (!slug.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))) return@withContext PersonLinks()
        val digest = MessageDigest.getInstance("SHA-256").digest(slug.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(directory, "$digest.json")
        val cached = runCatching { parsePersonLinks(file.readText()) }.getOrNull()
        if (!force && cached != null && System.currentTimeMillis() - file.lastModified() in 0..600_000) return@withContext cached
        try {
            val url = "$baseUrl/rest/v1/person_profiles".toHttpUrl().newBuilder()
                .addQueryParameter("select", "instagram_handles").addQueryParameter("slug", "eq.$slug").addQueryParameter("limit", "1").build()
            val request = Request.Builder().url(url).header("apikey", publicKey).header("Accept", "application/json").build()
            val text = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Profile links unavailable.")
                response.body?.string() ?: throw IOException("Profile response was empty.")
            }
            val result = parsePersonLinks(text)
            runCatching { directory.mkdirs(); synchronized(cacheLock) { file.writeText(text) } }
            result
        } catch (error: CancellationException) { throw error
        } catch (_: Exception) { (cached ?: PersonLinks()).copy(error = "Social links could not be refreshed. Film credits are still available.") }
    }

    companion object {
        private val cacheLock = Any()
        private fun config(context: Context) = JSONObject(context.assets.open("auth-config.json").bufferedReader().use { it.readText() })
    }
}
