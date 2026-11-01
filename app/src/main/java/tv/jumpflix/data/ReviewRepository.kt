// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import android.content.Context
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Same public author view as the website; never sends a session or reads account tables. */
class ReviewRepository internal constructor(private val baseUrl: String, private val publicKey: String,
    private val client: OkHttpClient) {
    constructor(context: Context) : this(config(context).getString("url"), config(context).getString("publishableKey"),
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build())

    suspend fun page(itemId: String, cursor: ReviewCursor? = null, pageSize: Int = 10): ReviewPage = withContext(Dispatchers.IO) {
        val mediaId = reviewMediaId(itemId)
        require(pageSize in 1..50)
        require(cursor == null || cursor.id > 0)
        val url = "$baseUrl/rest/v1/reviews_with_author".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,media_id,author_name,body,created_at,updated_at")
            .addQueryParameter("media_id", "eq.$mediaId").addQueryParameter("order", "updated_at.desc,id.desc")
            .addQueryParameter("limit", (pageSize + 1).toString())
        cursor?.let { url.addQueryParameter("or", "(updated_at.lt.${it.updated},and(updated_at.eq.${it.updated},id.lt.${it.id}))") }
        val request = Request.Builder().url(url.build()).header("apikey", publicKey).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Reviews could not be loaded.")
            parseReviews(response.body?.string() ?: throw IOException("Review response was empty."), mediaId, pageSize)
        }
    }
    companion object {
        private fun config(context: Context) = JSONObject(context.assets.open("auth-config.json").bufferedReader().use { it.readText() })
    }
}
