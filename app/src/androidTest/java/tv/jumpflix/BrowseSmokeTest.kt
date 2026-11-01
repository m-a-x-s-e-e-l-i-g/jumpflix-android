// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test

/** Exercises the real bundled catalog, detail screen, local library, and search UI. */
@OptIn(ExperimentalTestApi::class)
class BrowseSmokeTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    private fun awaitHome() {
        app.waitUntil(30_000) { app.onAllNodesWithText("Discover film").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun SemanticsNodeInteraction.activate() {
        performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        performKeyInput { pressKey(Key.DirectionCenter) }
    }

    @Test fun saveFromDetailsAndFindInLibrary() {
        awaitHome()
        app.onAllNodesWithContentDescription("Map").assertCountEquals(0)
        app.onNodeWithText("Discover film").activate()
        lateinit var selectedTitle: String
        app.runOnIdle { selectedTitle = ViewModelProvider(app.activity)[CatalogViewModel::class.java].state.value.selected!!.title }
        val saved = app.onAllNodesWithText("Saved to my list").fetchSemanticsNodes()
        if (saved.isNotEmpty()) app.onNodeWithText("Saved to my list").activate()
        app.onNodeWithText("Save to my list").activate()
        app.onNodeWithText("Saved to my list").assertExists()
        app.onAllNodesWithContentDescription("My list").filter(hasClickAction()).onFirst().activate()
        app.onNodeWithText("Your next movie night").assertExists()
        app.onNodeWithText(selectedTitle).assertExists()
    }

    @Test fun searchFindsAccentedTitlesFromPlainKeyboardInput() {
        awaitHome()
        app.onAllNodesWithContentDescription("Search").filter(hasClickAction()).onFirst().activate()
        app.onNode(hasSetTextAction()).performTextInput("kevin franzen")
        app.onNode(hasSetTextAction()).performImeAction()
        app.onNodeWithTag("film-grid").performScrollToNode(hasText("KEVIN FRANZÉN"))
        app.onNodeWithText("KEVIN FRANZÉN").assertExists()
        app.onNodeWithText("No films found").assertDoesNotExist()
    }

    @Test fun detailsShortcutReachesCreditSelectorsWithTheRemote() {
        awaitHome()
        app.onNodeWithText("Discover film").activate()
        app.onNode(hasText("Spots & songs") and hasClickAction()).activate()
        app.waitUntil(10_000) { app.onAllNodes(hasText("Songs", substring = true) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
        app.onNode(hasText("Songs", substring = true) and hasClickAction()).assertIsDisplayed()
        app.onNode(hasText("Songs", substring = true) and hasClickAction()).activate()
    }

    @Test fun peopleSearchOpensAthleteFilmographyAndBackReturnsThroughProfile() {
        awaitHome()
        app.onAllNodesWithContentDescription("Search").filter(hasClickAction()).onFirst().activate()
        app.onNode(hasSetTextAction()).performTextInput("kevin franzen")
        app.onNode(hasSetTextAction()).performImeAction()
        app.onNode(hasText("People") and hasClickAction()).activate()
        app.onNodeWithTag("people-results").performScrollToNode(hasContentDescription("Open profile: Kevin Franzén"))
        app.onNodeWithContentDescription("Open profile: Kevin Franzén").activate()
        app.onNodeWithText("Filmography").assertExists()
        app.onNodeWithTag("person-filmography").performScrollToNode(hasContentDescription("KEVIN FRANZÉN"))
        app.onNode(hasContentDescription("KEVIN FRANZÉN") and hasClickAction()).activate()
        app.onNodeWithText("Watch film").assertExists()
        app.onNode(hasText("Back") and hasClickAction()).activate()
        app.onNodeWithTag("person-filmography").assertExists()
        app.onNode(hasText("Back") and hasClickAction()).activate()
        app.onNodeWithTag("people-results").assertExists()
        app.onNode(hasSetTextAction()).assertTextContains("kevin franzen")
    }

    @Test fun filmCreditNamesOpenCreatorProfilesAndReturnToTheOriginalFilm() {
        awaitHome()
        app.onNodeWithText("Discover film").activate()
        lateinit var creator: String
        lateinit var original: String
        app.runOnIdle {
            val film = ViewModelProvider(app.activity)[CatalogViewModel::class.java].state.value.selected!!
            creator = film.creators.first()
            original = film.title
        }
        app.onNodeWithContentDescription("Open profile: $creator").activate()
        app.onNodeWithText("Filmography").assertExists()
        app.onNode(hasText("Back") and hasClickAction()).activate()
        app.onNodeWithText(original).assertExists()
        app.onNodeWithText("Discover film").assertDoesNotExist()
    }
}
