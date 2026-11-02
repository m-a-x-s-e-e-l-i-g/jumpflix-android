// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.player

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import org.json.JSONTokener
import tv.jumpflix.R
import tv.jumpflix.BuildConfig
import tv.jumpflix.data.LibraryStore
import tv.jumpflix.data.PlaybackSource
import tv.jumpflix.data.CreditReference
import tv.jumpflix.data.VideoCredits
import tv.jumpflix.data.VideoCreditsRepository
import tv.jumpflix.data.urlAt
import tv.jumpflix.ui.openExternal
import tv.jumpflix.xpop.XpopRepository
import tv.jumpflix.xpop.WatchedItem
import tv.jumpflix.xpop.watchKey

@OptIn(UnstableApi::class)
class PlaybackActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var web: WebView? = null
    private lateinit var root: FrameLayout
    private lateinit var status: TextView
    private lateinit var controls: PlaybackControls
    private lateinit var creditsView: PlaybackCredits
    private var credits = VideoCredits()
    private var creditsLoading = true
    private var creditsReference: CreditReference? = null
    private lateinit var toolbar: LinearLayout
    private lateinit var chrome: LinearLayout
    private lateinit var source: PlaybackSource
    private lateinit var library: LibraryStore
    private var fullscreen: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var itemId = ""
    private var playbackKey = ""
    private var filmTitle = ""
    private var television = false
    private var lastSave = 0L
    private var snapshot = PlaybackSnapshot()
    private var xpopOwner: String? = null
    private var completionQueued = false
    private var accountWatchKey = ""
    private var revealingTouch = false
    private var revealingKey = KeyEvent.KEYCODE_UNKNOWN
    private val handler = Handler(Looper.getMainLooper())
    private val hideChrome = object : Runnable {
        override fun run() {
            if (creditsView.dialogOpen || controls.interacting) handler.postDelayed(this, 4000)
            else if (snapshot.ready && !snapshot.paused && !status.isShown &&
                !(getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager).isTouchExplorationEnabled) setControlsVisible(false)
        }
    }
    private val poll = object : Runnable {
        override fun run() { updatePlayback(); handler.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = PlaybackSource.fromJson(JSONObject().put("kind", intent.getStringExtra("kind")).put("url", intent.getStringExtra("url"))) ?: run { finish(); return }
        itemId = intent.getStringExtra("itemId").orEmpty()
        playbackKey = intent.getStringExtra("key").orEmpty()
        filmTitle = intent.getStringExtra("title").orEmpty()
        library = LibraryStore(this)
        accountWatchKey = intent.getStringExtra("watchKey").orEmpty()
        lifecycleScope.launch { xpopOwner = XpopRepository.get(this@PlaybackActivity).playbackOwner() }
        val deviceIsTv = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        television = if (BuildConfig.DEBUG) when (intent.getStringExtra("layout")) {
            "phone" -> false
            "tv" -> true
            else -> deviceIsTv
        } else deviceIsTv
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chrome = layout
        toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(6), dp(12), dp(6)); setBackgroundColor(0xD0101012.toInt()) }
        toolbar.addView(playerIconButton(this, R.drawable.ic_arrow_back, "Back to film") { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(ImageView(this).apply { setImageResource(R.drawable.mascot); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(32), dp(36)).apply { marginStart = dp(8); marginEnd = dp(8) })
        toolbar.addView(TextView(this).apply { text = filmTitle; setTextColor(Color.WHITE); textSize = 16f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolbar.addView(playerButton(this, "Provider", "Open official provider") { player?.pause(); sendWebCommand("pause"); openExternal(this, source.url) }, LinearLayout.LayoutParams(dp(72), dp(48)).apply { marginStart = dp(8) })
        layout.addView(toolbar)
        val surface = FrameLayout(this)
        if (source.native) {
            root.addView(surface, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            layout.addView(View(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            root.setOnClickListener { revealControls() }
        } else layout.addView(surface, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; setPadding(dp(16), dp(6), dp(16), dp(6)); visibility = View.GONE }
        layout.addView(status)
        creditsView = PlaybackCredits(this, { command("seek", it.toDouble()); revealControls() },
            { player?.pause(); sendWebCommand("pause"); openExternal(this, it) }, { loadCredits(true) })
        layout.addView(creditsView)
        controls = PlaybackControls(this, television, ::command)
        layout.addView(controls)
        root.addView(layout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val cutout = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout())
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val gestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
            chrome.setPadding(maxOf(cutout.left, bars.left), maxOf(cutout.top, bars.top), maxOf(cutout.right, bars.right), maxOf(cutout.bottom, bars.bottom, gestures.bottom))
            insets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        adaptPlayerLayout()
        val requestedStart = intent.getLongExtra("startSeconds", -1).takeIf { it in 0..Int.MAX_VALUE.toLong() }?.times(1000)
        val previous = requestedStart ?: library.progress().firstOrNull { it.key == playbackKey }?.takeIf { it.inProgress }?.positionMs ?: 0L
        creditsReference = CreditReference.from(itemId, if (playbackKey.contains(":ep:")) playbackKey.substringAfterLast(":ep:") else null)
        loadCredits()
        if (source.native) nativePlayer(surface, previous) else embeddedPlayer(surface, previous)
        revealControls()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (fullscreen != null) closeFullscreen() else finish() }
        })
    }

    private fun loadCredits(force: Boolean = false) {
        val reference = creditsReference ?: run { creditsLoading = false; return }
        creditsLoading = true
        creditsView.render(credits, snapshot.position, snapshot.ready && snapshot.duration > 0, true)
        lifecycleScope.launch {
            credits = VideoCreditsRepository(this@PlaybackActivity).load(reference, force)
            creditsLoading = false
            creditsView.render(credits, snapshot.position, snapshot.ready && snapshot.duration > 0)
        }
    }

    private fun command(name: String, value: Double?) {
        revealControls()
        if (name == "back") { finish(); return }
        if (name == "fullscreen") {
            val entering = resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            requestedOrientation = if (entering) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (entering && snapshot.ready && !snapshot.paused) {
                handler.removeCallbacks(hideChrome)
                hideChrome.run()
            }
            return
        }
        player?.let { video ->
            when (name) {
                "toggle" -> if (video.playWhenReady && video.playbackState != Player.STATE_ENDED) video.pause() else {
                    if (video.playbackState == Player.STATE_ENDED) video.seekTo(0)
                    video.play()
                }
                "relative", "seek" -> {
                    val target = if (name == "relative") video.currentPosition + ((value ?: 0.0) * 1000).toLong() else ((value ?: 0.0) * 1000).toLong()
                    video.seekTo(target.coerceIn(0, video.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
                }
                "rate" -> video.setPlaybackSpeed((value ?: 1.0).toFloat())
                "mute" -> video.volume = if (video.volume == 0f) 1f else 0f
            }
            updatePlayback()
        } ?: sendWebCommand(name, value)
    }

    private fun sendWebCommand(name: String, value: Double? = null) {
        val json = JSONObject().put("name", name)
        if (value != null && value.isFinite()) json.put("value", value)
        web?.evaluateJavascript("window.jumpflixCommand && window.jumpflixCommand($json)", null)
    }

    private fun nativePlayer(surface: FrameLayout, position: Long) {
        val dataSource = ResolvingDataSource.Factory(DefaultHttpDataSource.Factory()) { request ->
            // Our catalog CDN expects the same public site referrer as the website player.
            if (request.uri.host == "vz-fe926028-ae3.b-cdn.net") request.withRequestHeaders(request.httpRequestHeaders + ("Referer" to "https://www.jumpflix.tv/")) else request
        }
        player = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(dataSource)).build().also { video ->
            video.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            video.setHandleAudioBecomingNoisy(true)
            video.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { revealControls() }
                override fun onPlayerError(error: PlaybackException) { showStatus("This stream could not be played here. Try Provider.") }
            })
            surface.addView(PlayerView(this).apply { player = video; useController = false; keepScreenOn = true }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            video.setMediaItem(MediaItem.fromUri(source.url)); video.seekTo(position); video.prepare(); video.playWhenReady = true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun embeddedPlayer(surface: FrameLayout, position: Long) {
        if (source.providerId == null) { showStatus("Play this release with its official provider."); return }
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false; settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (request?.isForMainFrame == true && request.url.scheme == "https") { openExternal(this@PlaybackActivity, request.url.toString()); return true }
                    return false
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null || fullscreen != null) { callback?.onCustomViewHidden(); return }
                    fullscreen = view; fullscreenCallback = callback
                    root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
                override fun onHideCustomView() { closeFullscreen() }
            }
            // Provider documents receive no native JavaScript bridge.
            loadDataWithBaseURL("https://www.jumpflix.tv/", embedDocument(source, position / 1000), "text/html", "UTF-8", null)
        }
        surface.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun closeFullscreen() {
        fullscreen?.let { root.removeView(it) }; fullscreen = null
        val callback = fullscreenCallback; fullscreenCallback = null; callback?.onCustomViewHidden()
    }

    private fun showStatus(message: String) { status.text = message; revealControls() }

    private fun setControlsVisible(visible: Boolean) {
        // Collapse the chrome without hiding the provider viewport inside its layout.
        val visibility = if (visible) View.VISIBLE else View.GONE
        controls.visibility = visibility
        creditsView.visibility = visibility
        status.visibility = if (visible && status.text.isNotBlank()) View.VISIBLE else View.GONE
        toolbar.visibility = if (visible && (television || resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE)) View.VISIBLE else View.GONE
    }

    private fun revealControls() {
        if (!::controls.isInitialized || fullscreen != null) return
        val hidden = controls.visibility != View.VISIBLE
        setControlsVisible(true)
        if (hidden && television) controls.focusPlayback()
        handler.removeCallbacks(hideChrome)
        handler.postDelayed(hideChrome, 4000)
    }

    override fun onUserInteraction() { super.onUserInteraction(); revealControls() }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (fullscreen != null) return super.dispatchTouchEvent(event)
        if (::controls.isInitialized && event.actionMasked == MotionEvent.ACTION_DOWN) {
            revealingTouch = controls.visibility != View.VISIBLE
            if (revealingTouch) revealControls()
        }
        if (revealingTouch) {
            if (event.actionMasked in setOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) revealingTouch = false
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    @SuppressLint("RestrictedApi") // Public Window.Callback override; intercept before a focused child activates.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (fullscreen != null) return super.dispatchKeyEvent(event)
        if (event.keyCode == revealingKey) {
            if (event.action == KeyEvent.ACTION_UP) revealingKey = KeyEvent.KEYCODE_UNKNOWN
            return true
        }
        if (::controls.isInitialized && controls.visibility != View.VISIBLE && event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode in setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)) {
            revealingKey = event.keyCode
            revealControls()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (::source.isInitialized && source.native) when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { command("toggle", null); revealControls(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (snapshot.paused) command("toggle", null); revealControls(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); revealControls(); return true }
            KeyEvent.KEYCODE_MEDIA_REWIND -> { command("relative", -10.0); revealControls(); return true }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> { command("relative", 10.0); revealControls(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun updatePlayback(forceSave: Boolean = false) {
        val save = forceSave || SystemClock.elapsedRealtime() - lastSave >= 5000
        if (save) lastSave = SystemClock.elapsedRealtime()
        player?.let { video ->
            renderPlayback(PlaybackSnapshot(video.currentPosition.coerceAtLeast(0), video.duration.coerceAtLeast(0), !video.playWhenReady || video.playbackState == Player.STATE_ENDED,
                video.playbackParameters.speed, video.volume == 0f, video.playbackState != Player.STATE_IDLE))
            if (save) recordProgress()
        }
        web?.evaluateJavascript("JSON.stringify(window.jumpflixState || {})") { encoded ->
            if (isDestroyed) return@evaluateJavascript
            runCatching {
                val state = JSONObject(JSONTokener(encoded).nextValue() as? String ?: return@runCatching)
                fun milliseconds(key: String): Long = state.optDouble(key, 0.0).takeIf { it.isFinite() && it >= 0 }?.let { (it * 1000).toLong() } ?: 0L
                val rates = state.optJSONArray("rates")?.let { array -> (0 until array.length()).map { array.optDouble(it).toFloat() }.filter { it.isFinite() && it in .25f..4f } }.orEmpty()
                renderPlayback(PlaybackSnapshot(milliseconds("position"), milliseconds("duration"), state.optBoolean("paused", true),
                    state.optDouble("rate", 1.0).toFloat(), state.optBoolean("muted"), state.optBoolean("ready"), rates.ifEmpty { listOf(1f) }))
                if (save) recordProgress()
                if (state.optBoolean("error")) showStatus(if (source.kind == "vimeo" && state.optString("errorName") in setOf("PrivacyError", "PasswordError"))
                    "Vimeo is not allowing this film to play here. Its owner may have restricted access. Choose Provider to check availability."
                    else "The provider cannot play this video here. Choose Provider to continue.")
                else if (state.optBoolean("controlError")) showStatus("That control is unavailable for this video. You can also use the provider controls.")
                else { status.text = ""; status.visibility = View.GONE }
            }
        }
    }

    private fun renderPlayback(value: PlaybackSnapshot) {
        val changed = value.paused != snapshot.paused || value.ready != snapshot.ready
        snapshot = value
        controls.render(snapshot)
        creditsView.render(credits, snapshot.position, snapshot.ready && snapshot.duration > 0, creditsLoading)
        if (changed) revealControls()
    }

    private fun recordProgress() {
        if (snapshot.position > 0 && snapshot.duration > 0) library.record(playbackKey, itemId, filmTitle, snapshot.position, snapshot.duration)
        val owner = xpopOwner ?: return
        if (completionQueued || accountWatchKey.isBlank()) return
        if (!accountWatchKey.startsWith("movie:") && !accountWatchKey.contains(":ep:")) return
        val completed = WatchedItem.completed(accountWatchKey, if (accountWatchKey.contains(":ep:")) "episode" else "movie", snapshot.position, snapshot.duration) ?: return
        completionQueued = true
        XpopRepository.get(this).completed(owner, completed)
    }

    private fun adaptPlayerLayout() {
        // Leave room for the complete provider viewport on short landscape phones.
        setControlsVisible(controls.visibility == View.VISIBLE)
        controls.setFullscreen(!television && resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        ViewCompat.requestApplyInsets(root)
    }

    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); adaptPlayerLayout() }

    override fun onStart() { super.onStart(); web?.onResume(); revealControls(); handler.post(poll) }
    override fun onStop() { updatePlayback(true); player?.pause(); sendWebCommand("pause"); web?.onPause(); handler.removeCallbacks(poll); handler.removeCallbacks(hideChrome); super.onStop() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); player?.release(); player = null
        web?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }; web = null
        super.onDestroy()
    }

    companion object {
        fun open(context: Context, itemId: String, key: String, title: String, source: PlaybackSource, startSeconds: Long? = null) {
            val tv = (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            // Some TVs have no WebView; their provider apps remain the supported route.
            if (source.kind == "external" || (tv && source.kind in setOf("youtube", "vimeo"))) { openExternal(context, source.urlAt(startSeconds)); return }
            context.startActivity(Intent(context, PlaybackActivity::class.java).putExtra("itemId", itemId).putExtra("key", key)
                .putExtra("title", title).putExtra("kind", source.kind).putExtra("url", source.url)
                .putExtra("startSeconds", startSeconds ?: -1L)
                .putExtra("watchKey", watchKey(itemId, source, if (key.contains(":ep:")) key.substringAfterLast(":ep:") else null)))
        }
    }
}
