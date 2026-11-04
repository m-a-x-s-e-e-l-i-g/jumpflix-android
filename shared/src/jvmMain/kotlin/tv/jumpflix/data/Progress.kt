// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

data class Progress(val key: String, val itemId: String, val title: String, val positionMs: Long, val durationMs: Long, val updatedAt: Long) {
    val inProgress: Boolean get() = positionMs >= 10_000 && (durationMs <= 0 || positionMs < durationMs * 0.95)
}

