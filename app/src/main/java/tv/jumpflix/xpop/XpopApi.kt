// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.xpop

import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tv.jumpflix.auth.AuthSession

class XpopFailure(val status: Int) : IOException(if (status == 401) "Sign in again to sync XPOP." else "Could not sync with JUMPFLIX. Please try again.")

/** Uses only the public key and the current user's bearer token, under existing RLS. */
class XpopApi(private val baseUrl: String, private val publicKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()) {
    private fun request(session: AuthSession, path: String, filters: Map<String, String> = emptyMap(), body: JSONObject? = null,
        method: String = "GET", prefer: String? = null): String {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments("rest/v1/$path")
        filters.forEach { (key, value) -> url.addQueryParameter(key, value) }
        val builder = Request.Builder().url(url.build()).header("apikey", publicKey)
            .header("Authorization", "Bearer ${session.accessToken}").header("Accept", "application/json")
        if (prefer != null) builder.header("Prefer", prefer)
        if (method != "GET") builder.method(method, if (method == "DELETE") null else (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()))
        return client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw XpopFailure(response.code)
            response.body?.string().orEmpty()
        }
    }
    private fun own(session: AuthSession, mediaId: Long? = null): Map<String, String> = buildMap {
        put("user_id", "eq.${session.profile.id}")
        if (mediaId != null) { require(mediaId > 0); put("media_id", "eq.$mediaId") }
    }

    fun summary(session: AuthSession): XpopSummary {
        val overview = JSONObject(request(session, "rpc/user_stats_overview", body = JSONObject().put("target_user", session.profile.id), method = "POST"))
        if (!overview.optBoolean("found")) throw XpopFailure(401)
        var units = 0L
        var offset = 0
        do {
            val rows = JSONArray(request(session, "content_suggestions", mapOf("select" to "xp_units", "created_by" to "eq.${session.profile.id}", "status" to "eq.approved", "order" to "id.asc", "offset" to offset.toString(), "limit" to "500")))
            for (index in 0 until rows.length()) units += rows.getJSONObject(index).optLong("xp_units", 1).coerceAtLeast(1)
            offset += rows.length()
        } while (rows.length() == 500)
        return XpopSummary(overview.optLong("watched_items").coerceAtLeast(0), overview.optLong("ratings_count").coerceAtLeast(0), overview.optLong("reviews_count").coerceAtLeast(0), units)
    }

    fun details(session: AuthSession, itemId: String): MediaAccountState {
        val numeric = itemId.substringAfter(':').toLong().also { require(it > 0) }
        val ratings = JSONArray(request(session, "ratings", own(session, numeric) + ("select" to "rating")))
        val reviews = JSONArray(request(session, "reviews", own(session, numeric) + ("select" to "body")))
        val watches = JSONArray(request(session, "watch_history", own(session) + mapOf("select" to "media_id", "media_id" to "like.$itemId*", "is_watched" to "eq.true", "status" to "eq.active")))
        val watched = (0 until watches.length()).map { watches.getJSONObject(it).getString("media_id") }.filter { it == itemId || it.startsWith("$itemId:") }.toSet()
        return MediaAccountState(session.profile.id, itemId, loaded = true, rating = ratings.optJSONObject(0)?.getInt("rating"), review = reviews.optJSONObject(0)?.optString("body").orEmpty(), watched = watched)
    }

    /** Idempotent upserts; editing an existing rating/review never adds another row. */
    fun saveRating(session: AuthSession, mediaId: Long, rating: Int?) {
        require(rating == null || rating in 1..10)
        if (rating == null) request(session, "ratings", own(session, mediaId), method = "DELETE")
        else {
            require(mediaId > 0)
            request(session, "ratings", mapOf("on_conflict" to "user_id,media_id"), JSONObject().put("user_id", session.profile.id).put("media_id", mediaId).put("rating", rating), "POST", "resolution=merge-duplicates,return=minimal")
        }
    }
    fun saveReview(session: AuthSession, mediaId: Long, body: String?) {
        if (body == null) request(session, "reviews", own(session, mediaId), method = "DELETE")
        else {
            require(mediaId > 0 && body.trim().length in 1..2000)
            request(session, "reviews", mapOf("on_conflict" to "user_id,media_id"), JSONObject().put("user_id", session.profile.id).put("media_id", mediaId).put("body", body.trim()), "POST", "resolution=merge-duplicates,return=minimal")
        }
    }
    fun markWatched(session: AuthSession, item: WatchedItem): Boolean {
        val existing = JSONArray(request(session, "watch_history", own(session) + mapOf("media_id" to "eq.${item.mediaId}", "select" to "is_watched,status"))).optJSONObject(0)
        if (existing?.optBoolean("is_watched") == true && existing.optString("status") == "active") return false
        if (item.type == "movie" && item.mediaId.count { it == ':' } > 1) {
            val base = item.mediaId.split(':').take(2).joinToString(":")
            val legacy = JSONArray(request(session, "watch_history", own(session) + mapOf("media_id" to "eq.$base", "select" to "is_watched,status"))).optJSONObject(0)
            if (legacy?.optBoolean("is_watched") == true && legacy.optString("status") == "active") return false
        }
        request(session, "watch_history", mapOf("on_conflict" to "user_id,media_id"), item.json().put("user_id", session.profile.id).put("watched_at", Instant.now().toString()), "POST", "resolution=merge-duplicates,return=minimal")
        return true
    }
}
