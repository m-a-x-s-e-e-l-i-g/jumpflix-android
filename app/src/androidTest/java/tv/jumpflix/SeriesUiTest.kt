// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tv.jumpflix.data.*
import tv.jumpflix.ui.*

@OptIn(ExperimentalTestApi::class)
class SeriesUiTest {
    @get:Rule val app = createComposeRule()
    private val episode = Episode("video", "A long episode title that should wrap without pushing its actions off screen", 1,
        PlaybackSource("youtube", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))

    @Test fun phoneEpisodeKeepsSecondaryActionsCollapsedAndRewatchStartsAtZero() {
        var start: Long? = -1
        app.setContent { JumpflixTheme {
            var expanded by remember { mutableStateOf(false) }
            EpisodeRow(episode, Progress("key", "series:75", "Episode", 50_000, 100_000, 0), true, true, false,
                expanded, { expanded = !expanded }, { start = it }) { androidx.compose.material3.Text("Extra episode actions") }
        } }
        app.onNodeWithText("Watched · Rewatch").assertExists()
        app.onNodeWithText("Extra episode actions").assertDoesNotExist()
        app.onNodeWithContentDescription("Rewatch episode 1: ${episode.title}").performClick()
        app.runOnIdle { assertEquals(0L, start) }
        app.onNodeWithContentDescription("Episode 1 details").performClick()
        app.onNodeWithText("Extra episode actions").assertIsDisplayed()
        app.onNodeWithContentDescription("Hide episode 1 details").performClick()
        app.onNodeWithText("Extra episode actions").assertDoesNotExist()
    }

    @Test fun remoteSelectsSeasonsResumesEpisodesAndDoesNotPlayUnavailableSources() {
        var start: Long? = -1
        var plays = 0
        app.setContent { JumpflixTheme {
            var season by remember { mutableIntStateOf(1) }
            var expanded by remember { mutableStateOf(false) }
            val seasons = listOf(Season(1, "Season 1", listOf(episode)), Season(2, "Extras", listOf(episode)))
            Column {
                SeasonSelector(seasons, seasons.first { it.number == season }, true) { season = it }
                EpisodeRow(episode, Progress("key", "series:75", "Episode", 50_000, 100_000, 0), false, season == 1, true,
                    expanded, { expanded = !expanded }, { start = it; plays++ }) { androidx.compose.material3.Text("Extra episode actions") }
            }
        } }
        fun SemanticsNodeInteraction.activate() {
            performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            performKeyInput { pressKey(Key.DirectionCenter) }
        }
        app.onNodeWithContentDescription("Resume episode 1: ${episode.title}").activate()
        app.runOnIdle { assertNull(start); assertEquals(1, plays) }
        app.onNodeWithTag("season-2").activate()
        app.onNodeWithTag("season-2").assertIsSelected()
        app.onNodeWithContentDescription("Unavailable episode 1: ${episode.title}").activate()
        app.onNodeWithText("Extra episode actions").assertIsDisplayed()
        app.runOnIdle { assertEquals(1, plays) }
    }
}
