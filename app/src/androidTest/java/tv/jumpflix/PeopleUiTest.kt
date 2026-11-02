// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tv.jumpflix.data.*
import tv.jumpflix.ui.*

@OptIn(ExperimentalTestApi::class)
class PeopleUiTest {
    @get:Rule val app = createComposeRule()
    private fun films(): List<CatalogItem> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sample = Catalog.parse(context.assets.open("catalog-seed.json").bufferedReader().use { it.readText() }).items.first()
        return listOf(sample.copy(id = "movie:1", title = "Created film", creators = listOf("Alex Example"), starring = emptyList()),
            sample.copy(id = "movie:2", title = "Featured film", creators = emptyList(), starring = listOf("Alex Example")))
    }
    private fun SemanticsNodeInteraction.activate() {
        performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        performKeyInput { pressKey(Key.DirectionCenter) }
    }

    @Test fun phoneProfileFiltersCreditsAndOpensPublishedSocialLinks() {
        val films = films()
        val person = buildPeopleIndex(films).single()
        var selected = ""
        var url = ""
        app.setContent { JumpflixTheme {
            PersonProfileContent(person, films, false, true, PersonLinks(listOf("alex.example")), false, {}, { selected = it.id }, { url = it }, setOf("movie:1"))
        } }
        app.onNodeWithText("Creator & athlete").assertExists()
        app.onNodeWithText("Credits from the saved catalog").assertExists()
        app.onNodeWithText("Instagram · @alex.example").performScrollTo().performClick()
        app.runOnIdle { assertEquals("https://www.instagram.com/alex.example/", url) }
        app.onNodeWithText("Created by (1)").performScrollTo().performClick()
        app.onNodeWithTag("person-filmography").performScrollToNode(hasContentDescription("Created film"))
        app.onNodeWithText("Watched", useUnmergedTree = true).assertIsDisplayed()
        app.onNode(hasContentDescription("Created film") and hasClickAction()).performClick()
        app.runOnIdle { assertEquals("movie:1", selected) }
        app.onNodeWithText("Featured film").assertDoesNotExist()
        app.onNodeWithText("Featuring (1)").performScrollTo().performClick()
        app.onNodeWithTag("person-filmography").performScrollToNode(hasContentDescription("Featured film"))
        app.onNodeWithText("Featured film").assertExists()
        app.onNodeWithText("Watched", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun tvProfileKeepsFilmographyAvailableWhenSocialLinksFailAndCanRetry() {
        val films = films()
        var retries = 0
        app.setContent { JumpflixTheme {
            PersonProfileContent(buildPeopleIndex(films).single(), films, true, false, PersonLinks(error = "Social links unavailable."), false, { retries++ }, {}, {})
        } }
        app.onNodeWithText("Retry profile links").activate()
        app.runOnIdle { assertEquals(1, retries) }
        app.onNodeWithTag("person-filmography").performScrollToNode(hasContentDescription("Created film"))
        app.onNodeWithText("Created film").assertExists()
    }
}
