// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.player

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/** Opt-in network check against a real authorized catalog stream. */
@androidx.annotation.OptIn(UnstableApi::class)
class NativePlaybackSmokeTest {
    private fun View.descendants(): List<View> = listOf(this) + if (this is ViewGroup) (0 until childCount).flatMap { getChildAt(it).descendants() } else emptyList()

    @Test fun directStreamPlaysWithJumpflixControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("nativePlaybackSmoke") == "true")
        val context = instrumentation.targetContext
        val catalog = JSONObject(context.assets.open("catalog-seed.json").bufferedReader().use { it.readText() }).getJSONArray("items")
        val film = (0 until catalog.length()).map { catalog.getJSONObject(it) }.first { it.optString("slug") == "enter-the-breach-2021" }
        val source = film.getJSONObject("playback")
        val intent = Intent(context, PlaybackActivity::class.java)
            .putExtra("itemId", film.getString("id")).putExtra("key", "qa-native-playback")
            .putExtra("title", film.getString("title")).putExtra("kind", source.getString("kind")).putExtra("url", source.getString("url"))
        ActivityScenario.launch<PlaybackActivity>(intent).use { scenario ->
            val deadline = SystemClock.elapsedRealtime() + 45_000
            var ready = false
            while (!ready && SystemClock.elapsedRealtime() < deadline) {
                scenario.onActivity { activity ->
                    val player = activity.window.decorView.descendants().filterIsInstance<PlayerView>().single().player!!
                    ready = player.playbackState == Player.STATE_READY && player.currentPosition >= 2000 && player.duration > 0 &&
                        ((player as ExoPlayer).videoDecoderCounters?.renderedOutputBufferCount ?: 0) > 0 &&
                        activity.window.decorView.descendants().any { it.contentDescription == "Pause" && it.isEnabled }
                }
                if (!ready) SystemClock.sleep(250)
            }
            assertTrue("The real catalog HLS stream must start within 45 seconds", ready)
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.cacheDir, "native-player-smoke.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            SystemClock.sleep(4500)
            scenario.onActivity { activity ->
                assertFalse(activity.window.decorView.descendants().filterIsInstance<PlaybackControls>().single().isShown)
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            scenario.onActivity { activity ->
                assertTrue(activity.window.decorView.descendants().filterIsInstance<PlaybackControls>().single().isShown)
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            scenario.onActivity { activity ->
                val views = activity.window.decorView.descendants()
                val surface = views.filterIsInstance<PlayerView>().single()
                assertFalse(surface.useController)
                val player = surface.player!!
                assertFalse(player.playWhenReady)
                val before = player.currentPosition
                views.first { it.contentDescription == "Forward 10 seconds" }.performClick()
                assertTrue(abs(before + 10_000 - player.currentPosition) <= 150)
                views.first { it.contentDescription == "Mute audio" }.performClick()
                assertEquals(0f, player.volume)
            }
        }
        // Keep this QA watch out of the device-local user library.
        val prefs = context.getSharedPreferences("jumpflix-library", 0)
        val progress = JSONObject(prefs.getString("progress", "{}") ?: "{}")
        progress.remove("qa-native-playback")
        prefs.edit().putString("progress", progress.toString()).commit()
    }
}
