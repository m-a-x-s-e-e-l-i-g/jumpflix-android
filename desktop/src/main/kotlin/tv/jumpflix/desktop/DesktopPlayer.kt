// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ca.weblite.webview.WebViewDownloadEvent
import ca.weblite.webview.WebViewDownloadHandler
import ca.weblite.webview.swing.WebViewComponent
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.UUID
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay
import org.json.JSONObject
import tv.jumpflix.data.PlaybackSource

internal data class DesktopPlayback(val title: String, val source: PlaybackSource, val fallbackUrl: String)

internal val PlaybackSource.embeddedOnDesktop: Boolean
    get() = native || kind in setOf("youtube", "vimeo") && providerId != null

internal fun providerEmbed(source: PlaybackSource): String? {
    val id = source.providerId ?: return null
    return when (source.kind) {
        "youtube" -> "https://www.youtube.com/embed/$id?autoplay=1&playsinline=1&fs=0&widget_referrer=https%3A%2F%2Ftv.jumpflix.desktop"
        "vimeo" -> "https://player.vimeo.com/video/$id?autoplay=1" +
            source.vimeoPrivacyHash?.let { "&h=$it" }.orEmpty()
        else -> null
    }
}

@Composable
internal fun EmbeddedVideo(playback: DesktopPlayback) {
    var ready by remember(playback) { mutableStateOf(false) }
    var error by remember(playback) { mutableStateOf<String?>(null) }
    var page by remember(playback) { mutableStateOf<PlayerPage?>(null) }
    var view by remember(playback) { mutableStateOf<WebViewComponent?>(null) }
    DisposableEffect(playback) {
        val session = runCatching { PlayerPage(playback.source) }.getOrNull()
        page = session
        if (session == null) error = "Could not open this video."
        onDispose {
            try { view?.dispose() }
            finally { session?.close() }
        }
    }
    LaunchedEffect(page) {
        delay(15_000)
        if (!ready && error == null) error = "The video player could not start. Try opening the video in your browser."
    }
    val session = page
    val message = error
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (session != null && message == null) {
            SwingPanel(background = Color.Black, modifier = Modifier.fillMaxSize(), factory = {
                try {
                    WebViewComponent.create().apply {
                        setPopupHandler(null)
                        setDownloadHandler(object : WebViewDownloadHandler {
                            override fun downloadRequested(event: WebViewDownloadEvent) = null
                        })
                        setPermissionHandler { it.deny() }
                        setPasswordManagerEnabled(false)
                        setDefaultContextMenuEnabled(false)
                        setNavigationHandler { session.allowsNavigation(it.url()) }
                        addJavascriptCallback("jumpflixPlayerStatus") { payload ->
                            val status = runCatching { JSONObject(payload).getJSONArray("args").getString(0) }.getOrNull()
                            SwingUtilities.invokeLater {
                                when (status) {
                                    "ready" -> ready = true
                                    "error" -> error = "This stream could not be played here. Try opening it in your browser."
                                }
                            }
                        }
                        setUrl(session.url)
                    }.also { view = it }
                } catch (_: Exception) {
                    error = "Could not start the system video player."
                    JPanel()
                } catch (_: LinkageError) {
                    error = "The system web engine is unavailable. Try opening the video in your browser."
                    JPanel()
                }
            })
        } else {
            Text(message.orEmpty(), color = Color.White, modifier = Modifier.padding(24.dp))
        }
    }
}

// An HTTP page gives the provider iframe a referrer and serves the bundled HLS script.
// Each session binds only to loopback and accepts requests for its own unguessable path.
internal class PlayerPage(source: PlaybackSource) : AutoCloseable {
    private val embed = providerEmbed(source)
    private val path = "/${UUID.randomUUID()}/"
    private val html = playerHtml(source, embed).toByteArray(Charsets.UTF_8)
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    private val authority = "127.0.0.1:${server.address.port}"
    val url = "http://$authority$path"

    init {
        server.createContext(path) { exchange ->
            exchange.use {
                val valid = it.requestMethod == "GET" && it.requestHeaders.getFirst("Host") == authority
                val script = valid && source.kind == "hls" && it.requestURI.rawPath == "${path}hls.js"
                if (valid && it.requestURI.rawPath == path) {
                    it.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
                    it.responseHeaders.set("Referrer-Policy", "strict-origin-when-cross-origin")
                    it.responseHeaders.set("Cache-Control", "no-store")
                    it.sendResponseHeaders(200, html.size.toLong())
                    it.responseBody.write(html)
                } else if (script) {
                    javaClass.getResourceAsStream("/META-INF/resources/_static/hls.js/1.7.4/dist/hls.min.js")?.use { body ->
                        it.responseHeaders.set("Content-Type", "text/javascript; charset=utf-8")
                        it.sendResponseHeaders(200, 0)
                        body.copyTo(it.responseBody)
                    } ?: it.sendResponseHeaders(404, -1)
                } else {
                    it.sendResponseHeaders(404, -1)
                }
            }
        }
        server.start()
    }

    fun allowsNavigation(location: String): Boolean {
        if (location == url || location == "about:blank") return true
        val provider = embed?.let(::URI) ?: return false
        val target = runCatching { browserUri(location) }.getOrNull() ?: return false
        return target.host == provider.host && target.path == provider.path && target.port in setOf(-1, 443)
    }

    override fun close() = server.stop(0)
}

private fun playerHtml(source: PlaybackSource, embed: String?): String {
    require(source.embeddedOnDesktop)
    val content = if (embed != null) {
        """<iframe id="video" title="Video player" src="$embed" allow="autoplay; encrypted-media" referrerpolicy="strict-origin-when-cross-origin"></iframe>"""
    } else {
        val url = JSONObject.quote(browserUri(source.url).toASCIIString()).replace("<", "\\u003c")
        val hls = if (source.kind == "hls") """<script src="hls.js"></script>""" else ""
        """
        <video id="video" controls autoplay playsinline></video>
        $hls
        <script>
        const video = document.getElementById('video');
        video.addEventListener('error', () => window.jumpflixPlayerStatus?.('error'));
        if (${source.kind == "hls"} && !video.canPlayType('application/vnd.apple.mpegurl')) {
          if (window.Hls && Hls.isSupported()) {
            const player = new Hls();
            player.on(Hls.Events.ERROR, (_, info) => { if (info.fatal) window.jumpflixPlayerStatus?.('error'); });
            player.loadSource($url);
            player.attachMedia(video);
          } else {
            window.jumpflixPlayerStatus?.('error');
          }
        } else {
          video.src = $url;
        }
        </script>
        """.trimIndent()
    }
    val frames = embed?.let { "https://${URI(it).host}" } ?: "'none'"
    return """
        <!doctype html><html lang="en"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta http-equiv="Content-Security-Policy" content="default-src 'none'; frame-src $frames; style-src 'unsafe-inline'; script-src 'self' 'unsafe-inline'; worker-src blob:; media-src https: blob:; connect-src https:;">
        <style>html,body,#video{margin:0;width:100%;height:100%;border:0;background:#000;display:block}</style>
        </head><body>
        <script>window.jumpflixPlayerStatus?.('ready');</script>
        $content
        </body></html>
    """.trimIndent()
}
