// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.xpop

import org.json.JSONObject
import tv.jumpflix.data.PlaybackSource

/** Same weights as jumpflix.tv/src/lib/xp.ts. Totals are derived from account rows. */
data class XpopSummary(val watching: Long, val ratings: Long, val reviews: Long, val contributions: Long) {
    val watchingPoints get() = watching * 5
    val ratingPoints get() = ratings * 15
    val reviewPoints get() = reviews * 25
    val contributionPoints get() = contributions * 10
    val total get() = watchingPoints + ratingPoints + reviewPoints + contributionPoints
}

data class XpopReward(val id: Long, val amount: Int, val label: String)
data class XpopState(val ownerId: String? = null, val summary: XpopSummary? = null, val loading: Boolean = false,
    val error: String? = null, val reward: XpopReward? = null)
data class MediaAccountState(val ownerId: String? = null, val itemId: String = "", val loaded: Boolean = false,
    val busy: Boolean = false, val rating: Int? = null, val review: String = "", val watched: Set<String> = emptySet(), val error: String? = null)

/** Keys deliberately match the website, including provider suffixes and episode IDs. */
fun watchKey(itemId: String, source: PlaybackSource?, episodeId: String? = null): String {
    require(itemId.matches(Regex("(movie|series):[1-9][0-9]*")))
    if (episodeId != null) {
        require(itemId.startsWith("series:") && episodeId.isNotBlank())
        return "$itemId:ep:$episodeId"
    }
    if (itemId.startsWith("series:") || source == null || source.kind == "external") return itemId
    val suffix = when (source.kind) {
        "youtube" -> "yt:${requireNotNull(source.providerId)}"
        "vimeo" -> "vimeo:${requireNotNull(source.providerId)}"
        "hls" -> "hls:${source.url}"
        else -> "src:${source.url}"
    }
    return "$itemId:$suffix"
}

data class WatchedItem(val mediaId: String, val type: String, val position: Int = 0, val duration: Int = 0) {
    init {
        require(type in setOf("movie", "episode"))
        require(mediaId.startsWith(if (type == "episode") "series:" else "movie:"))
        require(position >= 0 && duration >= 0)
    }
    fun json() = JSONObject().put("media_id", mediaId).put("media_type", type).put("position_seconds", position)
        .put("duration_seconds", duration).put("percent_watched", if (duration > 0) (position.toDouble() / duration * 100).coerceIn(85.0, 100.0) else 100.0)
        .put("is_watched", true).put("status", "active")
    companion object {
        fun completed(mediaId: String, type: String, positionMs: Long, durationMs: Long): WatchedItem? {
            if (durationMs <= 0 || positionMs < 0 || positionMs.toDouble() / durationMs < .85) return null
            return WatchedItem(mediaId, type, (positionMs / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), (durationMs / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        fun parse(json: JSONObject) = WatchedItem(json.getString("media_id"), json.getString("media_type"), json.optInt("position_seconds"), json.optInt("duration_seconds"))
    }
}
