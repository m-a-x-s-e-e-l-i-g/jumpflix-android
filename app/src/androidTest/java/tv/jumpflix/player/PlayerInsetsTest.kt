// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.player

import android.content.Intent
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class PlayerInsetsTest {
    @Test fun playerChromeRespectsCutoutAndGestureInsetsWithHiddenSystemBars() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, PlaybackActivity::class.java).putExtra("kind", "hls").putExtra("url", "https://video.example/qa.m3u8")
            .putExtra("title", "Insets QA").putExtra("key", "qa-insets")
        ActivityScenario.launch<PlaybackActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                val root = content.getChildAt(0) as ViewGroup
                val chrome = root.getChildAt(root.childCount - 1)
                val insets = WindowInsetsCompat.Builder()
                    .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout(), Insets.of(60, 80, 0, 0))
                    .setInsets(WindowInsetsCompat.Type.mandatorySystemGestures(), Insets.of(0, 0, 0, 30)).build()
                ViewCompat.dispatchApplyWindowInsets(root, insets)
                assertEquals(60, chrome.paddingLeft)
                assertEquals(80, chrome.paddingTop)
                assertEquals(30, chrome.paddingBottom)
            }
        }
    }
}
