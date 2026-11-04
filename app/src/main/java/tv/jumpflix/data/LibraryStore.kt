// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.data

import android.content.Context
import org.json.JSONObject

/** Device-local library. Account sync is deliberately not implied by this store. */
class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("jumpflix-library", Context.MODE_PRIVATE)
    fun saved(): Set<String> = prefs.getStringSet("saved", emptySet())?.toSet() ?: emptySet()
    fun toggle(id: String): Set<String> = saved().toMutableSet().also { ids ->
        if (!ids.remove(id)) ids.add(id)
        prefs.edit().putStringSet("saved", ids).apply()
    }
    fun progress(): List<Progress> = runCatching {
        val json = JSONObject(prefs.getString("progress", "{}") ?: "{}")
        json.keys().asSequence().map { key ->
            val entry = json.getJSONObject(key)
            Progress(key, entry.getString("itemId"), entry.getString("title"), entry.optLong("positionMs"), entry.optLong("durationMs"), entry.optLong("updatedAt"))
        }.sortedByDescending { it.updatedAt }.toList()
    }.getOrDefault(emptyList())

    fun record(key: String, itemId: String, title: String, positionMs: Long, durationMs: Long) {
        if (positionMs < 0 || durationMs < 0) return
        val json = runCatching { JSONObject(prefs.getString("progress", "{}") ?: "{}") }.getOrDefault(JSONObject())
        json.put(key, JSONObject().put("itemId", itemId).put("title", title)
            .put("positionMs", positionMs).put("durationMs", durationMs).put("updatedAt", System.currentTimeMillis()))
        prefs.edit().putString("progress", json.toString()).apply()
    }
}
