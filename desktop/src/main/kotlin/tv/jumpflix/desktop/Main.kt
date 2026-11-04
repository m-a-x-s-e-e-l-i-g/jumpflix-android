// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import java.awt.Desktop
import java.io.File
import java.net.URI
import tv.jumpflix.data.CatalogClient
import org.jetbrains.compose.resources.painterResource
import tv.jumpflix.desktop.resources.Res
import tv.jumpflix.desktop.resources.mascot

internal fun cacheDirectory(os: String = System.getProperty("os.name"), home: String = System.getProperty("user.home"),
    environment: Map<String, String> = System.getenv()): File {
    val base = when {
        os.startsWith("Windows") -> environment["LOCALAPPDATA"]?.takeIf { File(it).isAbsolute }?.let(::File) ?: File(home, "AppData/Local")
        os.startsWith("Mac") -> File(home, "Library/Caches")
        else -> environment["XDG_CACHE_HOME"]?.takeIf { File(it).isAbsolute }?.let(::File) ?: File(home, ".cache")
    }
    return File(base, "Jumpflix")
}

internal fun browserUri(url: String): URI {
    val uri = URI(url)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "This video link cannot be opened safely." }
    return uri
}

fun main() = application {
    val state = rememberWindowState(width = 1280.dp, height = 800.dp)
    Window(onCloseRequest = ::exitApplication, title = "JUMPFLIX", state = state,
        icon = painterResource(Res.drawable.mascot)) {
        SideEffect { window.minimumSize = java.awt.Dimension(960, 640) }
        val repository = remember {
            CatalogClient(File(cacheDirectory(), "catalog-v1.json"),
                { requireNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("catalog-seed.json")).bufferedReader().use { it.readText() } },
                "https://www.jumpflix.tv", "Jumpflix-Desktop/0.1.0-preview")
        }
        DesktopApp(repository, fullscreen = state.placement == WindowPlacement.Fullscreen,
            toggleFullscreen = {
                state.placement = if (state.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
            }, openUrl = { url ->
                val uri = browserUri(url)
                check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) { "No browser is available on this computer." }
                Desktop.getDesktop().browse(uri)
            })
    }
}

