// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import tv.jumpflix.xpop.watchKey

data class MoviePlaybackAction(val label: String, val startSeconds: Long? = null)

fun filmWatched(itemId: String, source: PlaybackSource?, watched: Set<String>): Boolean =
    itemId in watched || watchKey(itemId, source) in watched

fun moviePlaybackAction(item: CatalogItem, progress: Progress?, watched: Set<String>): MoviePlaybackAction = when {
    item.paid -> MoviePlaybackAction("Open official provider")
    filmWatched(item.id, item.playback, watched) -> MoviePlaybackAction("Rewatch", 0)
    progress?.inProgress == true -> MoviePlaybackAction("Resume")
    else -> MoviePlaybackAction("Watch film")
}
