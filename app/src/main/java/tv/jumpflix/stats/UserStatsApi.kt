// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.stats

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import tv.jumpflix.auth.AuthSession

class UserStatsFailure(val status: Int) : IOException(if (status == 401) "Sign in again to load your stats." else "Your stats could not be loaded. Please try again.")

/** Read-only; callers bind the token to the currently signed-in account. */
class UserStatsApi(private val baseUrl: String, private val publicKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()) {
    private fun read(session: AuthSession, path: String, filters: Map<String, String> = emptyMap(), rpc: Boolean = false): String {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegments("rest/v1/$path")
        filters.forEach { (key, value) -> url.addQueryParameter(key, value) }
        val request = Request.Builder().url(url.build()).header("apikey", publicKey).header("Authorization", "Bearer ${session.accessToken}").header("Accept", "application/json")
        if (rpc) request.post(JSONObject().put("target_user", session.profile.id).toString().toRequestBody("application/json".toMediaType()))
        return client.newCall(request.build()).execute().use { response ->
            if (!response.isSuccessful) throw UserStatsFailure(response.code)
            response.body?.string().orEmpty()
        }
    }
    private fun title(json: JSONObject, idKey: String = "id"): StatsTitle? {
        val id = json.optLong(idKey)
        val type = json.optString("type")
        if (id <= 0 || type !in setOf("movie", "series")) return null
        return StatsTitle(id, json.optString("title").ifBlank { "Untitled" }, type, json.optString("slug"))
    }

    fun load(session: AuthSession): UserStats {
        val json = JSONObject(read(session, "rpc/user_stats_overview", rpc = true))
        if (!json.optBoolean("found")) throw UserStatsFailure(401)
        val overview = StatsOverview.parse(json)
        val bins = MutableList(10) { 0L }
        val distribution = JSONArray(read(session, "rpc/user_ratings_distribution", rpc = true))
        for (index in 0 until distribution.length()) {
            val row = distribution.getJSONObject(index)
            val rating = row.optInt("rating")
            if (rating in 1..10) bins[rating - 1] += row.optLong("count").coerceAtLeast(0)
        }
        val rated = mutableListOf<RatedTitle>()
        var ratedOffset = 0
        do {
            val ratedRows = JSONArray(read(session, "rpc/user_rated_media", mapOf("limit" to "500", "offset" to ratedOffset.toString(), "order" to "rating.desc,updated_at.desc,media_id.asc"), rpc = true))
            for (index in 0 until ratedRows.length()) {
                val row = ratedRows.getJSONObject(index)
                val media = title(row, "media_id") ?: continue
                row.optInt("rating").takeIf { it in 1..10 }?.let { rated.add(RatedTitle(media, it)) }
            }
            ratedOffset += ratedRows.length()
        } while (ratedRows.length() == 500)
        val reviews = JSONArray(read(session, "reviews", mapOf("select" to "id,media_id,body,created_at", "user_id" to "eq.${session.profile.id}", "order" to "created_at.desc,id.desc", "limit" to "20")))
        val ids = (0 until reviews.length()).map { reviews.getJSONObject(it).optLong("media_id") }.filter { it > 0 }.distinct()
        val titles = if (ids.isEmpty()) emptyMap() else {
            val rows = JSONArray(read(session, "media_items", mapOf("select" to "id,title,type,slug", "id" to "in.(${ids.joinToString(",")})")))
            (0 until rows.length()).mapNotNull { title(rows.getJSONObject(it)) }.associateBy { it.id }
        }
        val recent = (0 until reviews.length()).map { index ->
            val row = reviews.getJSONObject(index)
            StatsReview(row.getLong("id"), titles[row.optLong("media_id")], row.optString("body"), row.optString("created_at"))
        }
        var approved = 0L; var pending = 0L; var declined = 0L; var units = 0L; var offset = 0
        do {
            val rows = JSONArray(read(session, "content_suggestions", mapOf("select" to "status,xp_units", "created_by" to "eq.${session.profile.id}", "order" to "id.asc", "limit" to "500", "offset" to offset.toString())))
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                when (row.optString("status")) {
                    "approved" -> { approved++; units += row.optLong("xp_units", 1).coerceAtLeast(1) }
                    "pending" -> pending++
                    "declined", "rejected" -> declined++
                }
            }
            offset += rows.length()
        } while (rows.length() == 500)
        return UserStats(overview, bins, rated, recent, ContributionStats(approved, pending, declined, units))
    }
}
