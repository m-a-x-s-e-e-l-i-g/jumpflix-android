// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.player

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tv.jumpflix.data.*

class PlaybackCreditsTest {
    @get:Rule val app = createAndroidComposeRule<ComponentActivity>()
    private fun View.descendants(): List<View> = listOf(this) + if (this is ViewGroup) (0 until childCount).flatMap { getChildAt(it).descendants() } else emptyList()
    private fun clickText(label: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        instrumentation.uiAutomation.waitForIdle(100, 3000)
        var node = instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(label).first { it.text?.toString()?.equals(label, ignoreCase = true) == true }
        while (!node.isClickable) node = node.parent ?: error("No clickable parent for $label")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        instrumentation.waitForIdleSync()
    }

    @Test fun playbackLabelsFollowTheClockAndTheChapterDialogSeeks() {
        var position = -1L
        lateinit var view: PlaybackCredits
        val data = VideoCredits(spots = listOf(SpotCredit("spot_abcdefgh", "Beach walls", 10, 20, "Portugal")),
            songs = listOf(SongCredit("one", "Track", "Artist", 0, null)))
        app.runOnUiThread {
            view = PlaybackCredits(app.activity, { position = it }, {}, {})
            app.activity.setContentView(view)
            view.render(data, 12_000, true)
            val text = view.descendants().filterIsInstance<TextView>().joinToString { it.text }
            assertTrue(text.contains("Spot · Beach walls"))
            assertTrue(text.contains("Song · Artist — Track"))
            view.render(data, 20_000, true)
            assertFalse(view.descendants().filterIsInstance<TextView>().joinToString { it.text }.contains("Spot · Beach walls"))
        }
        app.runOnUiThread { view.descendants().first { it.contentDescription == "Spots and songs in this video" }.performClick() }
        clickText("00:10–00:20 · Beach walls")
        clickText("Play from 00:10")
        app.runOnUiThread { assertEquals(10L, position); assertFalse(view.dialogOpen) }
    }
}
