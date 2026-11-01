// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.xpop

import android.content.Context
import org.json.JSONObject

/** Only authenticated completions enter this queue. No tokens or anonymous history. */
internal class XpopWatchQueue(context: Context, namespace: String = "jumpflix-xpop-pending") {
    private val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private fun read() = runCatching { JSONObject(prefs.getString("watches", "{}") ?: "{}") }.getOrDefault(JSONObject())
    @Synchronized fun add(owner: String, item: WatchedItem) {
        val json = read()
        json.put("$owner|${item.mediaId}", item.json().put("owner", owner))
        check(prefs.edit().putString("watches", json.toString()).commit()) { "Could not queue your completed watch." }
    }
    @Synchronized fun forOwner(owner: String): List<WatchedItem> {
        val json = read()
        return json.keys().asSequence().mapNotNull { key ->
            runCatching { json.getJSONObject(key).takeIf { it.optString("owner") == owner }?.let(WatchedItem::parse) }.getOrNull()
        }.toList()
    }
    @Synchronized fun remove(owner: String, mediaId: String) {
        val json = read(); json.remove("$owner|$mediaId")
        check(prefs.edit().putString("watches", json.toString()).commit()) { "Could not update pending watches." }
    }
}
