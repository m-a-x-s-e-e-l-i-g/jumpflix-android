// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class SongSearchLiveTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private fun SemanticsNodeInteraction.activate() {
        performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        performKeyInput { pressKey(Key.DirectionCenter) }
    }

    @Test fun creditedSongSearchFindsItsFilmThroughTheNativeSearchScreen() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("songSearchSmoke") == "true")
        val model = ViewModelProvider(app.activity)[CatalogViewModel::class.java]
        app.waitUntil(40_000) { model.state.value.music["movie:314"]?.any { it.title == "O Mar Apaga" } == true }
        app.onAllNodesWithContentDescription("Search").filter(hasClickAction()).onFirst().activate()
        app.onNode(hasSetTextAction()).performTextInput("O Mar Apaga")
        app.onNode(hasSetTextAction()).performImeAction()
        app.onNodeWithTag("film-grid").performScrollToNode(hasText("Peanutbutter"))
        app.onNodeWithText("Peanutbutter").assertIsDisplayed()
    }
}
