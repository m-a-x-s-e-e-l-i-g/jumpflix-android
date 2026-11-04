// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

data class MusicCredit(val title: String, val artist: String) {
    internal val searchText = normalizedSearch("$title $artist")
}

