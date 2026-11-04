// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
package tv.jumpflix.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*
import tv.jumpflix.data.*

class DesktopBrowseTest {
    @get:Rule val ui = createComposeRule()

    @Test fun `TV keyboard navigation searches opens a title and goes back`() {
        val catalog = fixture()
        val film = catalog.visibleItems.first { it.title == "KEVIN FRANZÉN" }
        var fullscreenChanges = 0
        ui.setContent { DesktopBrowse(catalog, false, null, {}, { error("Not a series") }, false, { fullscreenChanges++ }, {}) }
        ui.onNodeWithText("Home").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        ui.onNodeWithText("Search").assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        ui.onNodeWithTag("desktop-search").performTextInput("kevin franzen")
        ui.onNodeWithTag("poster-${film.id}").assertExists().performClick()
        ui.onNodeWithText("Watch in browser").assertExists()
        screenshot("desktop-detail")
        ui.onNodeWithText("Back").assertIsFocused().performKeyInput { pressKey(Key.Escape) }
        ui.onNodeWithTag("desktop-search").assertTextContains("kevin franzen")
        ui.onNode(hasText("Search") and hasClickAction()).assertIsFocused()
        ui.onRoot().performKeyInput { pressKey(Key.F11) }
        assertEquals(1, fullscreenChanges)
    }

    @Test fun `series retry fetches again and only playable episodes are shown`() {
        val original = fixture().visibleItems.first { it.type == "series" && !it.paid }
        val episode = Episode("available", "Playable episode", 7, PlaybackSource("vimeo", "https://vimeo.com/123456"))
        val series = original.copy(title = "Unique series fixture", seasons = listOf(Season(1, "Season 1", listOf(episode, Episode("missing", "Hidden episode", 9, null)))))
        var attempts = 0
        var opened: String? = null
        ui.setContent { DesktopBrowse(Catalog(listOf(series), "fixture"), false, null, {}, {
            attempts++
            if (attempts == 1) error("offline") else series
        }, false, {}, { opened = it }) }
        ui.onNodeWithText("Search").performClick()
        ui.onNodeWithTag("poster-${series.id}").performClick()
        ui.onNodeWithText("Retry episodes").performClick()
        ui.onNodeWithText("7. Playable episode").assertExists().performClick()
        ui.onNodeWithText("9. Hidden episode").assertDoesNotExist()
        assertEquals(2, attempts)
        assertEquals("https://vimeo.com/123456", opened)
    }

    @Test fun `home keeps New Explore and mascot in the desktop TV layout`() {
        ui.setContent { DesktopBrowse(fixture(), false, null, {}, { error("Not a series") }, false, {}, {}) }
        ui.onNodeWithContentDescription("JUMPFLIX popcorn character").assertExists()
        ui.onNodeWithText("New").assertExists()
        screenshot("desktop-home")
        ui.onNodeWithText("Explore").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Randomize").performClick()
        ui.onNodeWithText("Explore").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val image = ui.onRoot().captureToImage()
        val pixels = image.toPixelMap()
        val bitmap = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) for (x in 0 until image.width) bitmap.setRGB(x, y, pixels[x, y].toArgb())
        val file = File("build/screenshots/$name.png")
        file.parentFile.mkdirs()
        check(ImageIO.write(bitmap, "png", file))
    }
}
