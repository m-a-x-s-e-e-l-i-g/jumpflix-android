// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import java.net.URI
import java.text.Normalizer
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackSource(val kind: String, val url: String) {
    val native: Boolean get() = kind == "hls" || kind == "direct"
    val providerId: String? get() = runCatching {
        val uri = URI(url)
        when (kind) {
            "youtube" -> if (uri.host in setOf("www.youtube.com", "youtube.com")) {
                uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.substring(2)
                    ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
            } else null
            "vimeo" -> if (uri.host in setOf("vimeo.com", "www.vimeo.com", "player.vimeo.com")) {
                uri.path.trim('/').split('/').let { parts -> if (parts.firstOrNull() == "video") parts.getOrNull(1) else parts.firstOrNull() }
                    ?.takeIf { it.matches(Regex("[0-9]+")) }
            } else null
            else -> null
        }
    }.getOrNull()

    val vimeoPrivacyHash: String? get() = runCatching {
        if (kind != "vimeo" || providerId == null) return@runCatching null
        val uri = URI(url)
        val hash = uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("h=") }?.substring(2)
            ?: uri.path.trim('/').split('/').getOrNull(1)?.takeIf { uri.host != "player.vimeo.com" }
        hash?.takeIf { it.matches(Regex("[a-fA-F0-9]{6,64}")) }
    }.getOrNull()

    companion object {
        fun fromJson(value: JSONObject?): PlaybackSource? {
            if (value == null) return null
            val kind = value.optString("kind")
            val url = value.optString("url")
            val uri = runCatching { URI(url) }.getOrNull() ?: return null
            if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null) return null
            if (kind !in setOf("youtube", "vimeo", "hls", "direct", "external")) return null
            val source = PlaybackSource(kind, url)
            if (kind in setOf("youtube", "vimeo") && source.providerId == null) return null
            return source
        }
    }
}

data class Episode(val id: String, val title: String, val position: Int, val playback: PlaybackSource?)
data class Season(val number: Int, val title: String, val episodes: List<Episode>)

data class CatalogItem(
    val id: String,
    val slug: String,
    val type: String,
    val title: String,
    val description: String,
    val thumbnail: String?,
    val year: String?,
    val duration: String?,
    val creators: List<String>,
    val starring: List<String>,
    val category: String?,
    val movement: List<String>,
    val explicit: Boolean,
    val paid: Boolean,
    val available: Boolean,
    val rating: Double?,
    val ratingCount: Int,
    val addedAt: String,
    val url: String,
    val playback: PlaybackSource?,
    val seasons: List<Season>,
) {
    val metadata: String get() = listOfNotNull(year, duration, if (type == "series") "Series" else category?.replace('-', ' ')?.replaceFirstChar { it.uppercase() }).joinToString("  ·  ")
    private val searchText = normalizedSearch(listOf(title, description, creators.joinToString(" "), starring.joinToString(" ")).joinToString(" "))
    fun matches(query: String, music: List<MusicCredit> = emptyList()): Boolean = matches(catalogSearchTerms(query), music)
    internal fun matches(terms: List<String>, music: List<MusicCredit> = emptyList()): Boolean = terms.all { word ->
        searchText.contains(word) || music.any { it.searchText.contains(word) }
    }

    companion object {
        fun fromJson(json: JSONObject): CatalogItem = CatalogItem(
            id = json.getString("id"), slug = json.getString("slug"), type = json.getString("type"),
            title = json.getString("title"), description = json.optString("description"),
            thumbnail = json.nullableString("thumbnail"), year = json.nullableString("year"),
            duration = json.nullableString("duration"), creators = json.strings("creators"),
            starring = json.strings("starring"), category = json.nullableString("category"),
            movement = json.strings("movement"), explicit = json.optBoolean("explicit"),
            paid = json.optBoolean("paid"), available = json.optBoolean("available", true),
            rating = if (json.isNull("averageRating")) null else json.optDouble("averageRating").takeIf { it.isFinite() },
            ratingCount = json.optInt("ratingCount"), addedAt = json.nullableString("addedAt") ?: "",
            url = json.getString("url"), playback = PlaybackSource.fromJson(json.optJSONObject("playback")),
            seasons = json.objects("seasons").map { season ->
                Season(season.getInt("number"), season.getString("title"), season.objects("episodes").mapIndexed { index, episode ->
                    Episode(episode.getString("id"), episode.getString("title"), episode.optInt("position", index + 1), PlaybackSource.fromJson(episode.optJSONObject("playback")))
                })
            },
        )
    }
}

private val searchMarks = Regex("\\p{M}+")
private val searchWords = Regex("\\s+")
internal fun catalogSearchTerms(query: String): List<String> = normalizedSearch(query).trim().split(searchWords)
internal fun normalizedSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
    .replace(searchMarks, "").lowercase(java.util.Locale.ROOT)

fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList()
fun JSONObject.objects(key: String): List<JSONObject> = optJSONArray(key)?.let { array -> (0 until array.length()).map { array.getJSONObject(it) } } ?: emptyList()

data class Catalog(val items: List<CatalogItem>, val generatedAt: String) {
    companion object {
        fun parse(text: String): Catalog {
            val json = JSONObject(text)
            require(json.getInt("schemaVersion") == 1) { "This catalog needs a newer JUMPFLIX app." }
            return Catalog(json.objects("items").map(CatalogItem::fromJson), json.getString("generatedAt"))
        }
    }
}
