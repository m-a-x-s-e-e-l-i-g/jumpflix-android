// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.player

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale
import tv.jumpflix.R

internal data class PlaybackSnapshot(
    val position: Long = 0, val duration: Long = 0, val paused: Boolean = true,
    val rate: Float = 1f, val muted: Boolean = false, val ready: Boolean = false,
    val rates: List<Float> = listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f)
)

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun playerControlBackground(context: Context) = StateListDrawable().apply {
    fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = context.dp(10).toFloat() }
    addState(intArrayOf(android.R.attr.state_focused), shape(Color.WHITE))
    addState(intArrayOf(android.R.attr.state_pressed), shape(Color.rgb(230, 59, 40)))
    addState(intArrayOf(), shape(Color.rgb(35, 35, 38)))
}

internal fun playerIconButton(context: Context, icon: Int, description: String, action: () -> Unit): ImageButton = ImageButton(context).apply {
    setImageResource(icon); contentDescription = description
    scaleType = ImageView.ScaleType.CENTER_INSIDE
    imageTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(Color.BLACK, Color.WHITE))
    background = playerControlBackground(context)
    setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
    setOnClickListener { action() }
}

internal fun playerButton(context: Context, label: String, description: String, action: () -> Unit): Button =
    Button(context).apply {
        text = label; contentDescription = description; isAllCaps = false; textSize = 13f
        typeface = resources.getFont(R.font.noto_sans)
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(context.dp(4), 0, context.dp(4), 0)
        setTextColor(ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(Color.BLACK, Color.WHITE)))
        background = playerControlBackground(context)
        setOnClickListener { action() }
    }

/** Shared touch/D-pad controls, outside the official provider viewport. */
internal class PlaybackControls(context: Context, private val television: Boolean, private val command: (String, Double?) -> Unit) : LinearLayout(context) {
    private var snapshot = PlaybackSnapshot()
    private var dragging = false
    private val time = TextView(context).apply { setTextColor(Color.LTGRAY); textSize = 12f }
    private val seek = SeekBar(context).apply {
        max = 10000; contentDescription = "Playback position"
        progressTintList = ColorStateList.valueOf(Color.rgb(230, 59, 40))
        thumbTintList = ColorStateList.valueOf(Color.WHITE)
    }
    private val play = playerButton(context, "▶", "Play") { command("toggle", null) }
    private val speed = playerButton(context, "1×", "Playback speed") { showRates() }
    private val mute = playerIconButton(context, R.drawable.ic_volume_up, "Mute audio") { command("mute", null) }
    private val fullscreen = playerIconButton(context, R.drawable.ic_fullscreen, "Enter fullscreen") { command("fullscreen", null) }
    private val back = playerIconButton(context, R.drawable.ic_arrow_back, "Back to film") { command("back", null) }.apply { visibility = View.GONE }

    init {
        orientation = VERTICAL; setBackgroundColor(Color.rgb(18, 18, 20))
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(12))
        addView(time)
        addView(seek, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(40)))
        val actions = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        fun button(view: View) { actions.addView(view, LayoutParams(context.dp(48), context.dp(48)).apply { marginEnd = context.dp(4) }) }
        button(back); button(play)
        button(playerButton(context, "−10", "Rewind 10 seconds") { command("relative", -10.0) })
        button(playerButton(context, "+10", "Forward 10 seconds") { command("relative", 10.0) })
        actions.addView(View(context), LayoutParams(0, 1, 1f))
        button(speed); button(mute)
        if (!television) button(fullscreen)
        addView(actions)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) { dragging = true }
            override fun onStopTrackingTouch(bar: SeekBar) { dragging = false; seekTo(bar.progress) }
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    time.text = "${clock(snapshot.duration * progress / 10000)} / ${clock(snapshot.duration)}"
                    if (!dragging) seekTo(progress)
                }
            }
        })
        render(snapshot)
    }

    private fun seekTo(progress: Int) { if (snapshot.duration > 0) command("seek", snapshot.duration * progress / 10000.0 / 1000) }
    fun focusPlayback() { play.requestFocus() }
    fun setFullscreen(value: Boolean) {
        fullscreen.setImageResource(if (value) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen)
        fullscreen.contentDescription = if (value) "Exit fullscreen" else "Enter fullscreen"
        back.visibility = if (!television && value) View.VISIBLE else View.GONE
    }
    private fun showRates() {
        PopupMenu(context, speed).apply {
            snapshot.rates.forEachIndexed { index, rate -> menu.add(0, index, index, "${rate}×") }
            setOnMenuItemClickListener { command("rate", snapshot.rates[it.itemId].toDouble()); true }
            show()
        }
    }

    fun render(value: PlaybackSnapshot) {
        snapshot = value
        play.text = if (value.paused) "▶" else "Ⅱ"
        play.contentDescription = if (value.paused) "Play" else "Pause"
        speed.text = "${value.rate.toString().removeSuffix(".0")}×"
        mute.setImageResource(if (value.muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up)
        mute.contentDescription = if (value.muted) "Unmute audio" else "Mute audio"
        listOf(play, speed, mute).forEach { it.isEnabled = value.ready; it.alpha = if (value.ready) 1f else .5f }
        speed.isEnabled = value.ready && value.rates.size > 1
        seek.isEnabled = value.ready && value.duration > 0
        seek.keyProgressIncrement = if (value.duration > 0) (100_000_000L / value.duration).toInt().coerceIn(1, 10000) else 500
        if (!dragging) {
            seek.progress = if (value.duration > 0) (value.position * 10000 / value.duration).toInt().coerceIn(0, 10000) else 0
            time.text = "${clock(value.position)} / ${clock(value.duration)}"
        }
    }

    private fun clock(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return if (seconds >= 3600) String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
    }
}
