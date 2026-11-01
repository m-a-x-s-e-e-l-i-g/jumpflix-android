// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.player

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import tv.jumpflix.data.LibraryStore
import tv.jumpflix.data.PlaybackSource
import tv.jumpflix.ui.openExternal

@OptIn(UnstableApi::class)
class PlaybackActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private var web: WebView? = null
    private lateinit var root: FrameLayout
    private lateinit var status: TextView
    private lateinit var source: PlaybackSource
    private lateinit var library: LibraryStore
    private var fullscreen: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var itemId = ""
    private var playbackKey = ""
    private var filmTitle = ""
    private var television = false
    private val handler = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() { saveProgress(); handler.postDelayed(this, 5000) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = PlaybackSource.fromJson(JSONObject().put("kind", intent.getStringExtra("kind")).put("url", intent.getStringExtra("url"))) ?: run { finish(); return }
        itemId = intent.getStringExtra("itemId").orEmpty()
        playbackKey = intent.getStringExtra("key").orEmpty()
        filmTitle = intent.getStringExtra("title").orEmpty()
        library = LibraryStore(this)
        television = (getSystemService(UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(12, 8, 12, 8) }
        toolbar.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        toolbar.addView(TextView(this).apply { text = filmTitle; setTextColor(Color.WHITE); textSize = 16f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = android.view.Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        toolbar.addView(Button(this).apply { text = "Open provider"; setOnClickListener { player?.pause(); pauseEmbed(); openExternal(this@PlaybackActivity, source.url) } })
        layout.addView(toolbar)
        val surface = FrameLayout(this)
        layout.addView(surface, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; setPadding(24, 12, 24, 20); visibility = View.GONE }
        layout.addView(status)
        root.addView(layout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(root)
        val previous = library.progress().firstOrNull { it.key == playbackKey }?.takeIf { it.inProgress }?.positionMs ?: 0L
        if (source.native) nativePlayer(surface, previous) else embeddedPlayer(surface, previous)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (fullscreen != null) closeFullscreen() else finish() }
        })
    }

    private fun nativePlayer(surface: FrameLayout, position: Long) {
        player = ExoPlayer.Builder(this).build().also { video ->
            video.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            video.setHandleAudioBecomingNoisy(true)
            video.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    status.text = "This stream could not be played here. Try Open provider."
                    status.visibility = View.VISIBLE
                }
            })
            video.setMediaItem(MediaItem.fromUri(source.url))
            video.seekTo(position)
            video.prepare()
            video.playWhenReady = true
            surface.addView(PlayerView(this).apply { player = video; keepScreenOn = true; requestFocus() }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun embeddedPlayer(surface: FrameLayout, position: Long) {
        if (source.providerId == null) {
            status.text = "Play this release with its official provider."
            status.visibility = View.VISIBLE
            return
        }
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    if (request?.isForMainFrame == true && request.url.scheme == "https") {
                        openExternal(this@PlaybackActivity, request.url.toString())
                        return true
                    }
                    return false
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    if (view == null || fullscreen != null) { callback?.onCustomViewHidden(); return }
                    fullscreen = view
                    fullscreenCallback = callback
                    root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
                override fun onHideCustomView() { closeFullscreen() }
            }
            // No native JavaScript bridge is exposed to provider documents.
            loadDataWithBaseURL("https://www.jumpflix.tv/", embedDocument(source, position / 1000), "text/html", "UTF-8", null)
        }
        surface.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (television) web?.requestFocus()
    }

    private fun closeFullscreen() {
        fullscreen?.let { root.removeView(it) }
        fullscreen = null
        val callback = fullscreenCallback
        fullscreenCallback = null
        callback?.onCustomViewHidden()
    }

    private fun saveProgress() {
        player?.let { video ->
            val duration = video.duration.takeIf { it > 0 } ?: 0
            if (video.currentPosition > 0) library.record(playbackKey, itemId, filmTitle, video.currentPosition, duration)
        }
        web?.evaluateJavascript("JSON.stringify(window.jumpflixState || {})") { encoded ->
            runCatching {
                val jsonText = org.json.JSONTokener(encoded).nextValue() as? String ?: return@runCatching
                val state = JSONObject(jsonText)
                val position = state.optDouble("position", 0.0)
                val duration = state.optDouble("duration", 0.0)
                if (position.isFinite() && duration.isFinite() && position > 0 && duration > 0) {
                    library.record(playbackKey, itemId, filmTitle, (position * 1000).toLong(), (duration * 1000).toLong())
                }
                if (state.optBoolean("error")) {
                    status.text = if (source.kind == "vimeo" && state.optString("errorName") in setOf("PrivacyError", "PasswordError"))
                        "Vimeo is not allowing this film to play here. Its owner may have restricted access. Choose Open provider to check availability."
                    else "The provider cannot play this video here. Choose Open provider to continue."
                    status.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun pauseEmbed() {
        web?.evaluateJavascript("window.jumpflixControl && window.jumpflixControl('pause')", null)
        web?.onPause()
    }

    override fun onStart() { super.onStart(); web?.onResume(); handler.post(poll) }
    override fun onStop() { saveProgress(); handler.removeCallbacks(poll); player?.pause(); pauseEmbed(); super.onStop() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        web?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
        web = null
        super.onDestroy()
    }

    companion object {
        fun open(context: Context, itemId: String, key: String, title: String, source: PlaybackSource) {
            val tv = (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
            // TV devices may have no WebView implementation. Official provider apps
            // are the reliable initial route; native HLS/direct playback stays here.
            if (source.kind == "external" || (tv && source.kind in setOf("youtube", "vimeo"))) {
                openExternal(context, source.url)
                return
            }
            context.startActivity(Intent(context, PlaybackActivity::class.java).putExtra("itemId", itemId)
                .putExtra("key", key).putExtra("title", title).putExtra("kind", source.kind).putExtra("url", source.url))
        }
    }
}

/** Official provider embeds; no extraction, rehosting, or hidden provider controls. */
internal fun embedDocument(source: PlaybackSource, startSeconds: Long): String {
    val id = JSONObject.quote(source.providerId ?: "")
    val start = startSeconds.coerceAtLeast(0)
    val vimeoHash = source.vimeoPrivacyHash?.let { "&h=$it" }.orEmpty()
    val script = if (source.kind == "youtube") """
        <div id="player"></div><script src="https://www.youtube.com/iframe_api"></script>
        <script>
        var player; window.jumpflixState = {};
        function onYouTubeIframeAPIReady() {
          player = new YT.Player('player', {width:'100%', height:'100%', videoId:$id,
            playerVars:{autoplay:1, playsinline:1, start:$start, origin:'https://www.jumpflix.tv'},
            events:{onError:function(){window.jumpflixState.error=true;}}
          });
        }
        setInterval(function(){ if(player && player.getCurrentTime) {
          window.jumpflixState.position=player.getCurrentTime(); window.jumpflixState.duration=player.getDuration();
        }},1000);
        window.jumpflixControl=function(command){if(!player || !player.getPlayerState)return;
          if(command==='pause')player.pauseVideo();
          else if(command==='toggle'){player.getPlayerState()===1 ? player.pauseVideo() : player.playVideo();}
          else player.seekTo(Math.max(0,player.getCurrentTime()+(command==='back'?-10:10)),true);
        };
        </script>
    """.trimIndent() else """
        <script>
        window.jumpflixState={};
        function jumpflixVimeoError(error){window.jumpflixState.error=true;window.jumpflixState.errorName=(error&&error.name)||'Error';}
        window.addEventListener('message',function(event){
          var frame=document.getElementById('player');
          if(event.origin!=='https://player.vimeo.com'||!frame||event.source!==frame.contentWindow)return;
          var data=event.data;try{if(typeof data==='string')data=JSON.parse(data);}catch(e){return;}
          if(data&&data.event==='error')jumpflixVimeoError(data.data);
        });
        </script>
        <iframe id="player" title="Vimeo video player" src="https://player.vimeo.com/video/${source.providerId}?autoplay=1${vimeoHash}#t=${start}s" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen referrerpolicy="strict-origin-when-cross-origin"></iframe>
        <script src="https://player.vimeo.com/api/player.js"></script><script>
        var player=new Vimeo.Player('player');
        player.on('timeupdate',function(data){window.jumpflixState.position=data.seconds;window.jumpflixState.duration=data.duration;});
        player.on('error',jumpflixVimeoError);
        player.ready().catch(jumpflixVimeoError);
        window.jumpflixControl=function(command){
          if(command==='pause')player.pause().catch(function(){});
          else if(command==='toggle')player.getPaused().then(function(p){return p?player.play():player.pause();}).catch(jumpflixVimeoError);
          else player.getCurrentTime().then(function(t){return player.setCurrentTime(Math.max(0,t+(command==='back'?-10:10)));}).catch(jumpflixVimeoError);
        };
        </script>
    """.trimIndent()
    return """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="referrer" content="strict-origin-when-cross-origin"><style>html,body,#player{margin:0;width:100%;height:100%;background:#000;border:0;overflow:hidden}</style></head><body>$script</body></html>"""
}
