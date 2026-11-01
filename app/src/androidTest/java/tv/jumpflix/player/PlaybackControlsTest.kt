// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.player

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlaybackControlsTest {
    @get:Rule val app = createAndroidComposeRule<ComponentActivity>()

    private fun View.descendants(): List<View> = listOf(this) + if (this is ViewGroup) (0 until childCount).flatMap { getChildAt(it).descendants() } else emptyList()

    @Test fun controlsReflectPlaybackAndSeekWithTheRemote() {
        app.runOnUiThread {
            val commands = mutableListOf<Pair<String, Double?>>()
            val controls = PlaybackControls(app.activity, true) { name, value -> commands.add(name to value) }
            app.activity.setContentView(controls)
            val play = controls.descendants().first { it.contentDescription == "Play" }
            assertFalse(play.isEnabled)
            controls.render(PlaybackSnapshot(30_000, 120_000, false, 1.5f, true, true))
            assertEquals("Pause", play.contentDescription)
            play.performClick()
            controls.descendants().first { it.contentDescription == "Unmute audio" }.performClick()
            val seek = controls.descendants().filterIsInstance<SeekBar>().single()
            seek.requestFocus()
            seek.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            seek.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
            assertEquals("toggle", commands[0].first)
            assertEquals("mute", commands[1].first)
            assertEquals("seek", commands[2].first)
            assertEquals(40.0, commands[2].second!!, .1)
            assertFalse(controls.descendants().any { it.contentDescription == "Toggle fullscreen" })
        }
    }

    @Test fun phoneControlsExposeFullscreenAndDisableUnknownDurationSeeking() {
        app.runOnUiThread {
            var lastCommand = ""
            val controls = PlaybackControls(app.activity, false) { name, _ -> lastCommand = name }
            app.activity.setContentView(controls)
            controls.render(PlaybackSnapshot(ready = true))
            assertFalse(controls.descendants().filterIsInstance<SeekBar>().single().isEnabled)
            controls.descendants().first { it.contentDescription == "Toggle fullscreen" }.performClick()
            assertEquals("fullscreen", lastCommand)
        }
    }
}
