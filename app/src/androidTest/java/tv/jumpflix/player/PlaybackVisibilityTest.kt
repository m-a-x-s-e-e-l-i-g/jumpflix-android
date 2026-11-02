// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.player

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

/** A local provider-clock fixture exercises the real activity without a live video/account. */
class PlaybackVisibilityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun View.descendants(): List<View> = listOf(this) + if (this is ViewGroup) (0 until childCount).flatMap { getChildAt(it).descendants() } else emptyList()
    private fun PlaybackActivity.controls() = window.decorView.descendants().filterIsInstance<PlaybackControls>().single()
    private fun PlaybackActivity.web() = window.decorView.descendants().filterIsInstance<WebView>().single()

    private fun launch(kind: String, layout: String): ActivityScenario<PlaybackActivity> {
        val url = if (kind == "youtube") "https://www.youtube.com/watch?v=abcdefghijk" else "https://vimeo.com/123456"
        val intent = Intent(instrumentation.targetContext, PlaybackActivity::class.java).putExtra("kind", kind).putExtra("url", url)
            .putExtra("title", "Visibility QA").putExtra("key", "qa-visibility").putExtra("layout", layout)
        return ActivityScenario.launch<PlaybackActivity>(intent).also { scenario ->
            if (layout == "phone") {
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                await(scenario) { it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            }
            val fixtureLoaded = AtomicBoolean(false)
            scenario.onActivity { activity ->
                activity.web().apply {
                    stopLoading()
                    settings.blockNetworkLoads = true
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            view.evaluateJavascript("window.jumpflixFixture===true") { fixtureLoaded.set(it == "true") }
                        }
                    }
                    loadDataWithBaseURL("https://www.jumpflix.tv/", """
                        <html><body style="margin:0;height:100vh;background:black" onclick="window.jumpflixState.paused=!window.jumpflixState.paused"><script>
                        window.jumpflixFixture=true;
                        window.jumpflixState={ready:true,paused:true,position:0,duration:120,rate:1,rates:[1,1.5]};
                        window.jumpflixCommand=function(c){if(c.name==='toggle')window.jumpflixState.paused=!window.jumpflixState.paused;};
                        </script></body></html>
                    """.trimIndent(), "text/html", "UTF-8", null)
                }
            }
            await(scenario) { fixtureLoaded.get() && it.controls().descendants().any { view -> view.contentDescription == "Play" && view.isEnabled } }
        }
    }

    private fun await(scenario: ActivityScenario<PlaybackActivity>, condition: (PlaybackActivity) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var matches = false
        while (!matches && SystemClock.elapsedRealtime() < deadline) {
            scenario.onActivity { matches = condition(it) }
            if (!matches) SystemClock.sleep(100)
        }
        assertTrue("Player state did not settle", matches)
    }

    private fun play(scenario: ActivityScenario<PlaybackActivity>) {
        scenario.onActivity { it.controls().descendants().first { view -> view.contentDescription == "Play" }.performClick() }
        await(scenario) { it.controls().descendants().any { view -> view.contentDescription == "Pause" } }
    }

    @Test fun phoneEmbedHidesWhilePlayingAndFirstTouchOnlyRevealsControls() {
        launch("youtube", "phone").use { scenario ->
            play(scenario)
            await(scenario) { !it.controls().isShown }
            var hiddenHeight = 0
            scenario.onActivity { activity ->
                hiddenHeight = activity.web().height
                val location = IntArray(2).also { activity.web().getLocationOnScreen(it) }
                val x = location[0] + activity.web().width / 2f
                val y = location[1] + hiddenHeight / 2f
                val now = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    MotionEvent.obtain(now, now, action, x, y, 0).also { activity.dispatchTouchEvent(it); it.recycle() }
                }
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(750)
            scenario.onActivity { activity ->
                assertTrue(activity.controls().isShown)
                assertTrue(activity.web().height < hiddenHeight)
                assertTrue(activity.controls().descendants().any { it.contentDescription == "Pause" })
            }
            await(scenario) { !it.controls().isShown }
            var providerTaps = 0
            var providerClosed = false
            scenario.onActivity { activity ->
                val providerFullscreen = View(activity).apply { setOnClickListener { providerTaps++ } }
                activity.web().webChromeClient!!.onShowCustomView(providerFullscreen, WebChromeClient.CustomViewCallback { providerClosed = true })
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val now = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    MotionEvent.obtain(now, now, action, 100f, 100f, 0).also { activity.dispatchTouchEvent(it); it.recycle() }
                }
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(1, providerTaps)
                assertFalse(activity.controls().isShown)
                activity.web().webChromeClient!!.onHideCustomView()
                assertTrue(providerClosed)
            }
        }
    }

    @Test fun remoteRevealDoesNotPauseAndPausedControlsStayVisible() {
        launch("vimeo", "tv").use { scenario ->
            play(scenario)
            await(scenario) { !it.controls().isShown }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            scenario.onActivity { activity ->
                assertTrue(activity.controls().isShown)
                assertTrue(activity.controls().descendants().any { it.contentDescription == "Pause" })
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            await(scenario) { it.controls().descendants().any { view -> view.contentDescription == "Play" } }
            SystemClock.sleep(4500)
            scenario.onActivity { assertTrue(it.controls().isShown) }
        }
    }

    @Test fun enteringFullscreenHidesPlayingControlsAndRotationKeepsThemHidden() {
        launch("youtube", "phone").use { scenario ->
            play(scenario)
            lateinit var original: PlaybackActivity
            scenario.onActivity { activity ->
                original = activity
                activity.controls().descendants().first { it.contentDescription == "Enter fullscreen" }.performClick()
                assertFalse(activity.controls().isShown)
            }
            await(scenario) { it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            scenario.onActivity {
                assertSame("Fullscreen rotation must retain the playing activity", original, it)
                assertFalse(it.controls().isShown)
            }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            scenario.onActivity { activity ->
                assertTrue(activity.controls().isShown)
                assertTrue(activity.controls().descendants().any { it.contentDescription == "Exit fullscreen" })
            }
        }
    }
}
