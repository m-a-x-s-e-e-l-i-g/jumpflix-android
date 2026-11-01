// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import android.app.UiModeManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import tv.jumpflix.ui.JumpflixApp

class MainActivity : ComponentActivity() {
    private val model: CatalogViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceIsTv = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        // Debug-only layout override lets emulator QA render both input layouts.
        val television = if (BuildConfig.DEBUG) when (intent.getStringExtra("layout")) {
            "phone" -> false
            "tv" -> true
            else -> deviceIsTv
        } else deviceIsTv
        if (television) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        setContent { JumpflixApp(model, television, intent?.data?.lastPathSegment) }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) { model.reloadLibrary() } }
    }
}
