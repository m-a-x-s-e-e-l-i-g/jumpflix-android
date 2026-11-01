// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import java.net.URI
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Series chapter keys intentionally omit the device-local season component. */
data class CreditReference(val type: String, val mediaId: Long, val episodeId: String? = null) {
    val playbackKey: String? get() = episodeId?.let { "series:$mediaId:ep:$it" }
    companion object {
        fun from(itemId: String, episodeId: String? = null): CreditReference? {
            val parts = itemId.split(':')
            if (parts.size != 2 || parts[0] !in setOf("movie", "series")) return null
            val id = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
            if (parts[0] == "series" && episodeId.isNullOrBlank()) return null
            return CreditReference(parts[0], id, if (parts[0] == "series") episodeId else null)
        }
    }
}

data class SpotCredit(val id: String, val name: String, val start: Long, val end: Long, val country: String?) {
    val url: String get() = "https://parkour.spot/spot/$id"
}

data class SongCredit(val id: String, val title: String, val artist: String, val start: Long?,
    val spotifyUrl: String?, val explicit: Boolean = false)

data class VideoCredits(val spots: List<SpotCredit> = emptyList(), val songs: List<SongCredit> = emptyList(),
    val spotsError: String? = null, val songsError: String? = null) {
    fun spotAt(seconds: Long): SpotCredit? = spots.firstOrNull { seconds >= it.start && seconds < it.end }
    // Several untimed soundtrack imports share 00:00. Do not guess which song is playing.
    fun songAt(seconds: Long): SongCredit? {
        val candidates = songs.filter { it.start != null && it.start <= seconds }
        val latest = candidates.maxOfOrNull { it.start!! } ?: return null
        return candidates.filter { it.start == latest }.singleOrNull()
    }
}

internal fun parseSpotCredits(json: JSONObject, reference: CreditReference): List<SpotCredit> {
    val chapters = requireNotNull(json.optJSONArray("chapters")) { "Missing chapter list" }
    return (0 until chapters.length()).mapNotNull { index ->
        val row = chapters.optJSONObject(index) ?: return@mapNotNull null
        val key = row.nullableString("playbackKey")
        if (key.orEmpty() != reference.playbackKey.orEmpty()) return@mapNotNull null
        val id = row.nullableString("spotId")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{8,}")) } ?: return@mapNotNull null
        val start = row.seconds("startSeconds") ?: return@mapNotNull null
        val end = row.seconds("endSeconds")?.takeIf { it > start } ?: return@mapNotNull null
        val spot = row.optJSONObject("spot")
        val country = spot?.objects("countries")?.mapNotNull { it.nullableString("name") ?: it.nullableString("code") }?.joinToString(" · ")?.takeIf { it.isNotBlank() }
        SpotCredit(id, spot?.nullableString("name") ?: "Parkour spot", start, end, country)
    }.distinct().sortedWith(compareBy({ it.start }, { it.end }))
}

internal fun parseSongCredits(array: JSONArray): List<SongCredit> = (0 until array.length()).mapNotNull { index ->
    val row = array.optJSONObject(index) ?: return@mapNotNull null
    val song = row.optJSONObject("song") ?: return@mapNotNull null
    val title = song.nullableString("title") ?: return@mapNotNull null
    val spotifyId = song.nullableString("spotify_track_id")?.takeIf { it.matches(Regex("[A-Za-z0-9]{22}")) }
    val direct = song.nullableString("spotify_url")?.takeIf { value ->
        runCatching { val uri = URI(value); uri.scheme == "https" && uri.host == "open.spotify.com" && uri.userInfo == null && uri.path.matches(Regex("/track/[A-Za-z0-9]{22}")) }.getOrDefault(false)
    }
    SongCredit(row.optString("id", index.toString()), title, song.nullableString("artist") ?: "Unknown artist",
        row.seconds("start_offset_seconds"), direct ?: spotifyId?.let { "https://open.spotify.com/track/$it" }, song.optBoolean("explicit"))
}.sortedWith(compareBy(nullsLast()) { it.start })

private fun JSONObject.seconds(key: String): Long? = if (isNull(key)) null else optDouble(key, Double.NaN)
    .takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE }?.toLong()

fun creditTime(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60)
}

/** Official apps receive their own timestamp syntax, without changing access controls. */
fun PlaybackSource.urlAt(seconds: Long?): String {
    if (seconds == null) return url
    val time = seconds.coerceAtLeast(0)
    return when (kind) {
        "youtube" -> "$url&t=$time"
        "vimeo" -> "${url.substringBefore('#')}#t=${time}s"
        else -> url
    }
}
