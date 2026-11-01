// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.player

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import tv.jumpflix.R
import tv.jumpflix.data.*

/** Uses the same clock as our playback controls for both Media3 and provider SDKs. */
@SuppressLint("ViewConstructor") // Constructed programmatically with player callbacks, never inflated.
internal class PlaybackCredits(context: Context, private val seek: (Long) -> Unit,
    private val open: (String) -> Unit, private val retry: () -> Unit) : LinearLayout(context) {
    private val current = TextView(context).apply {
        setTextColor(Color.rgb(245, 242, 238)); textSize = 14f; maxLines = 2
        typeface = resources.getFont(R.font.noto_sans)
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private var credits = VideoCredits()
    private var loading = true
    private var ready = false
    private var dialogs = 0
    val dialogOpen: Boolean get() = dialogs > 0

    init {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(16), context.dp(4), context.dp(12), context.dp(4))
        setBackgroundColor(0xDD100F0F.toInt())
        addView(current, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(playerButton(context, "Spots & songs", "Spots and songs in this video") { showList() }, LayoutParams(context.dp(112), context.dp(48)))
    }

    fun render(data: VideoCredits, position: Long, canSeek: Boolean, isLoading: Boolean = false) {
        credits = data; loading = isLoading; ready = canSeek
        val spot = data.spotAt(position / 1000)
        val song = data.songAt(position / 1000)
        val label = listOfNotNull(spot?.let { "Spot · ${it.name}" }, song?.let { "Song · ${it.artist} — ${it.title}" }).joinToString("\n")
            .ifBlank { if (loading) "Loading credits…" else if (data.spotsError != null || data.songsError != null) "Credits unavailable · retry in Spots & songs"
                else if (data.spots.isEmpty() && data.songs.isEmpty()) "No spots or songs linked yet"
                else "${data.spots.size} spot chapters · ${data.songs.size} songs" }
        if (current.text.toString() != label) current.text = label
    }

    private fun showList() {
        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        credits.spots.forEach { spot ->
            labels += "${creditTime(spot.start)}–${creditTime(spot.end)} · ${spot.name}"
            actions += { showEntry(spot.name, spot.country.orEmpty(), spot.start, spot.url, "Open on parkour.spot") }
        }
        credits.songs.forEach { song ->
            val timed = song.start?.takeIf { start -> credits.songs.count { it.start == start } == 1 }
            labels += "${timed?.let(::creditTime) ?: "Untimed"} · ${song.artist} — ${song.title}"
            actions += { showEntry(song.title, song.artist + if (song.explicit) "\nExplicit lyrics" else "", timed, song.spotifyUrl, "Open in Spotify") }
        }
        if (credits.spotsError != null || credits.songsError != null) {
            labels += "Retry loading credits"
            actions += retry
        }
        val builder = AlertDialog.Builder(context).setTitle("Spots & songs").setNegativeButton("Close", null)
        if (labels.isEmpty()) builder.setMessage(if (loading) "Credits are loading. Open this list again in a moment." else "No spots or songs linked to this video yet.")
        else builder.setItems(labels.toTypedArray()) { _, which -> actions[which]() }
        show(builder)
    }

    private fun showEntry(title: String, detail: String, start: Long?, url: String?, linkLabel: String) {
        val builder = AlertDialog.Builder(context).setTitle(title).setMessage(detail).setNegativeButton("Back") { _, _ -> showList() }
        if (start != null && ready) builder.setPositiveButton("Play from ${creditTime(start)}") { _, _ -> seek(start) }
        if (url != null) builder.setNeutralButton(linkLabel) { _, _ -> open(url) }
        show(builder)
    }

    private fun show(builder: AlertDialog.Builder) {
        val dialog = builder.create()
        dialogs++
        dialog.setOnDismissListener { dialogs-- }
        dialog.show()
        listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_NEGATIVE).forEach { which ->
            dialog.getButton(which)?.apply { isAllCaps = false; typeface = resources.getFont(R.font.noto_sans); minHeight = context.dp(48) }
        }
    }
}
