// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.stats

import org.json.JSONObject
import tv.jumpflix.xpop.XpopSummary

data class StatsOverview(
    val watched: Long = 0, val movies: Long = 0, val series: Long = 0, val episodes: Long = 0,
    val positionSeconds: Long = 0, val durationSeconds: Long = 0, val catalogMovies: Long = 0,
    val catalogEpisodes: Long = 0, val ratings: Long = 0, val reviews: Long = 0, val averageRating: Double? = null
) {
    companion object {
        fun parse(json: JSONObject): StatsOverview {
            fun count(key: String) = json.optLong(key).coerceAtLeast(0)
            val ratings = count("ratings_count")
            return StatsOverview(count("watched_items"), count("watched_movies"), count("watched_series"), count("watched_episodes"),
                count("total_position_seconds"), count("total_duration_seconds"), count("catalog_movies"), count("catalog_episodes"),
                ratings, count("reviews_count"), json.optDouble("average_rating").takeIf { ratings > 0 && it.isFinite() && it in 1.0..10.0 })
        }
    }
}
data class StatsTitle(val id: Long, val title: String, val type: String, val slug: String)
data class RatedTitle(val media: StatsTitle, val rating: Int)
data class StatsReview(val id: Long, val media: StatsTitle?, val body: String, val createdAt: String)
data class ContributionStats(val approved: Long = 0, val pending: Long = 0, val declined: Long = 0, val units: Long = 0)
data class UserStats(val overview: StatsOverview, val distribution: List<Long>, val ratedTitles: List<RatedTitle>,
    val recentReviews: List<StatsReview>, val contributions: ContributionStats) {
    val xpop get() = XpopSummary(overview.watched, overview.ratings, overview.reviews, contributions.units)
}
data class UserStatsState(val ownerId: String? = null, val loading: Boolean = false, val data: UserStats? = null, val error: String? = null)

internal fun catalogFraction(watched: Long, total: Long): Float = if (total <= 0) 0f else (watched.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
internal fun statsDuration(seconds: Long): String {
    val minutes = seconds.coerceAtLeast(0) / 60
    return when {
        seconds <= 0 -> "0m"
        minutes == 0L -> "<1m"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
