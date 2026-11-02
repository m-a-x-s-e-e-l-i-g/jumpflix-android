// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.*
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Rule
import org.junit.Test
import tv.jumpflix.data.*
import tv.jumpflix.ui.*

@OptIn(ExperimentalTestApi::class)
class HomeUiTest {
    @get:Rule val app = createComposeRule()

    private fun checkHome(tv: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = Catalog.parse(context.assets.open("catalog-seed.json").bufferedReader().use { it.readText() })
        val films = catalog.items.take(36)
        val continuing = films.first()
        val state = CatalogState(catalog = catalog.copy(items = films), loading = false, cached = true, error = "Offline",
            progress = listOf(Progress(continuing.id, continuing.id, continuing.title, 50_000, 100_000, 0)))
        val saved = StateRestorationTester(app)
        saved.setContent { JumpflixTheme { Home(state, tv, {}, {}) } }
        app.onNodeWithText("Browsing saved catalog").assertDoesNotExist()
        app.onNodeWithText("Retry").assertDoesNotExist()
        app.onNodeWithTag("home-feed").performScrollToIndex(1)
        app.onNodeWithText("Continue watching").assertIsDisplayed()
        app.onNodeWithTag("home-feed").performScrollToIndex(2)
        app.onNodeWithText("New to the archive").assertIsDisplayed()
        app.onNodeWithTag("home-feed").performScrollToIndex(3)
        app.onNodeWithText("Explore the archive").assertIsDisplayed()
        val rowTag = "film-row-Explore the archive"
        app.onNodeWithTag(rowTag).performScrollToIndex(10)
        val button = app.onNodeWithContentDescription("Randomize archive")
        if (tv) {
            button.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            button.performKeyInput { pressKey(Key.DirectionCenter) }
        } else button.performClick()
        val first = films.shuffled(Random(LocalDate.now().toEpochDay().toInt() + 1)).first()
        val firstCard = hasContentDescription(first.title) and hasAnyAncestor(hasTestTag(rowTag))
        app.onNode(firstCard).assertIsDisplayed()
        saved.emulateSavedInstanceStateRestore()
        app.onNode(firstCard).assertIsDisplayed()
    }

    @Test fun phoneHomeOrdersRowsAndRandomizesTheArchiveWithoutTheCacheBanner() = checkHome(false)
    @Test fun remoteHomeCanRandomizeAndRestoreTheArchiveRow() = checkHome(true)

    @Test fun unknownHistoryWithholdsFeaturesAndWatchedUpdatesRemoveExistingPicks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = Catalog.parse(context.assets.open("catalog-seed.json").bufferedReader().use { it.readText() })
        val state = CatalogState(catalog = catalog, loading = false)
        var watched by mutableStateOf<Set<String>?>(null)
        var pending by mutableStateOf(true)
        app.setContent { JumpflixTheme { Home(state, false, {}, {}, watched, pending) } }
        app.onNodeWithText("Discover film").assertDoesNotExist()
        app.onNodeWithText("New to the archive").assertIsDisplayed()
        app.runOnIdle { watched = emptySet() }
        val first = dailyFeatured(catalog.items, LocalDate.now()).first()
        val featuredTitle = hasText(first.title) and hasAnyAncestor(hasTestTag("featured-picks"))
        app.onNode(featuredTitle).assertIsDisplayed()
        app.runOnIdle { watched = setOf("${first.id}:yt:old-provider") }
        app.onNode(featuredTitle).assertDoesNotExist()
        app.onNodeWithText("Discover film").assertExists()
        app.runOnIdle { watched = catalog.items.map { it.id }.toSet() }
        app.onNodeWithText("Discover film").assertDoesNotExist()
        app.onNodeWithText("New to the archive").assertIsDisplayed()
        app.runOnIdle { watched = null; pending = false }
        app.onNodeWithTag("featured-picks").assertDoesNotExist()
        app.onNodeWithText("New to the archive").assertIsDisplayed()
    }
}
