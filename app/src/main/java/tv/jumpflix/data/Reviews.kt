// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import java.time.Instant
import java.time.OffsetDateTime
import org.json.JSONArray

data class MediaReview(val id: Long, val author: String, val body: String, val created: Instant, val updated: Instant)
data class ReviewCursor(val updated: Instant, val id: Long)
data class ReviewPage(val reviews: List<MediaReview>, val next: ReviewCursor?)
data class ReviewFeed(val reviews: List<MediaReview> = emptyList(), val next: ReviewCursor? = null,
    val loading: Boolean = false, val loaded: Boolean = false, val error: String? = null)

internal fun reviewMediaId(itemId: String): Long {
    require(itemId.matches(Regex("(?:movie|series):[1-9][0-9]*"))) { "Invalid review title." }
    return itemId.substringAfter(':').toLong()
}

internal fun parseReviews(text: String, mediaId: Long, pageSize: Int): ReviewPage {
    val rows = JSONArray(text)
    val reviews = (0 until rows.length()).mapNotNull { index ->
        val row = rows.optJSONObject(index) ?: return@mapNotNull null
        val id = row.optLong("id").takeIf { it > 0 } ?: return@mapNotNull null
        if (row.optLong("media_id") != mediaId) return@mapNotNull null
        val body = row.nullableString("body")?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val updated = runCatching { OffsetDateTime.parse(row.getString("updated_at")).toInstant() }.getOrNull() ?: return@mapNotNull null
        val created = runCatching { OffsetDateTime.parse(row.getString("created_at")).toInstant() }.getOrDefault(updated)
        val author = row.nullableString("author_name")?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() } ?: "Anonymous"
        MediaReview(id, author, body, created, updated)
    }.distinctBy { it.id }
    val page = reviews.take(pageSize)
    val cursor = page.lastOrNull()?.takeIf { reviews.size > pageSize }?.let { ReviewCursor(it.updated, it.id) }
    return ReviewPage(page, cursor)
}

internal fun ReviewFeed.accept(page: ReviewPage, reset: Boolean): ReviewFeed = ReviewFeed(
    reviews = (if (reset) page.reviews else reviews + page.reviews).distinctBy { it.id }, next = page.next, loaded = true)
