// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import tv.jumpflix.data.*
import tv.jumpflix.ui.*

@OptIn(ExperimentalTestApi::class)
class WatchedCardTest {
    @get:Rule val app = createComposeRule()

    private fun checkCard(tv: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val movie = Catalog.parse(context.assets.open("catalog-seed.json").bufferedReader().use { it.readText() })
            .items.first { it.type == "movie" && it.paid && it.available && it.thumbnail != null }
        val progress = Progress(movie.id, movie.id, movie.title, 50_000, 100_000, 0)
        var watched by mutableStateOf(true)
        var opened = false
        app.setContent { JumpflixTheme {
            Column(Modifier.fillMaxSize().background(Background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Your archive", color = Ink, style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    FilmCard(movie, tv, Modifier.width(160.dp), progress, watched) { opened = true }
                    FilmCard(movie.copy(id = "movie:999", title = "In progress", paid = false), tv, Modifier.width(160.dp), progress) {}
                }
            }
        } }
        val card = app.onNodeWithContentDescription(movie.title)
        card.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Watched"))
        app.onNodeWithText("Watched", useUnmergedTree = true).assertIsDisplayed()
        app.onNodeWithText("PAID", useUnmergedTree = true).assertIsDisplayed()
        val progressBar = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        app.onAllNodes(progressBar).assertCountEquals(1)
        if (tv) {
            card.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            card.performKeyInput { pressKey(Key.DirectionCenter) }
        } else card.performClick()
        app.runOnIdle { assertTrue(opened) }
        File(context.getExternalFilesDir(null), "watched-cards-${if (tv) "tv" else "phone"}.png").outputStream().use {
            app.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        app.runOnIdle { watched = false }
        app.onNodeWithText("Watched", useUnmergedTree = true).assertDoesNotExist()
        card.assert(SemanticsMatcher("No watched state") { !it.config.contains(SemanticsProperties.StateDescription) })
        app.onAllNodes(progressBar).assertCountEquals(2)
    }

    @Test fun watchedPosterKeepsItsTouchActionAndUpdatesItsStatus() = checkCard(false)
    @Test fun watchedPosterKeepsItsRemoteFocusAndAction() = checkCard(true)
}
