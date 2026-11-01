// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tv.jumpflix.data.*
import tv.jumpflix.ui.*

@OptIn(ExperimentalTestApi::class)
class VideoCreditsUiTest {
    @get:Rule val app = createComposeRule()
    private val fixture = VideoCredits(
        spots = listOf(SpotCredit("mWpxjbkc7LRxd1AQFAu6", "Carcavelos Beach", 208, 219, "Portugal")),
        songs = listOf(SongCredit("1", "A credited song", "The artist", 65, "https://open.spotify.com/track/1234567890123456789012")))

    @Test fun phoneCreditsSeekToCueAndOpenCreditedLinks() {
        var position = -1L
        var link = ""
        app.setContent { JumpflixTheme {
            Column(Modifier.width(360.dp).fillMaxSize().background(Background).verticalScroll(rememberScrollState())) {
                VideoCreditsContent(fixture, false, false, true, {}, { position = it }, { link = it })
            }
        } }
        app.onNodeWithText("Carcavelos Beach").assertExists()
        app.onNodeWithText("Play from 03:28").performScrollTo().performClick()
        app.runOnIdle { assertEquals(208, position) }
        app.onNodeWithText("Open on parkour.spot").performScrollTo().performClick()
        app.runOnIdle { assertEquals(fixture.spots.single().url, link) }
        app.onNodeWithText("Songs (1)").performScrollTo().performClick()
        app.onNodeWithText("Play from 01:05").performScrollTo().performClick()
        app.runOnIdle { assertEquals(65, position) }
        app.onNodeWithText("Open in Spotify").performScrollTo().performClick()
        app.runOnIdle { assertEquals(fixture.songs.single().spotifyUrl, link) }
    }

    @Test fun tvFocusReachesCreditsAndFailureCanRetryWithoutInventingEmptyResults() {
        var retries = 0
        app.setContent { JumpflixTheme {
            Column(Modifier.fillMaxSize().background(Background).verticalScroll(rememberScrollState())) {
                VideoCreditsContent(VideoCredits(spotsError = "Spots could not be refreshed.", songsError = "Songs could not be refreshed."),
                    false, true, true, { retries++ }, {}, {})
            }
        } }
        app.onNodeWithText("No spots linked yet.").assertDoesNotExist()
        app.onNodeWithText("No songs credited yet.").assertDoesNotExist()
        app.onNodeWithText("Retry credits").performScrollTo().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        app.onNodeWithText("Retry credits").performKeyInput { pressKey(Key.DirectionCenter) }
        app.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun unavailableVideosKeepCreditsWithoutOfferingPlaybackAndUntimedSongsHaveNoCue() {
        app.setContent { JumpflixTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                VideoCreditsContent(fixture.copy(songs = listOf(fixture.songs.single().copy(start = null))), false, false, false, {}, {}, {})
            }
        } }
        app.onAllNodes(hasText("Play from", substring = true)).assertCountEquals(0)
        app.onNodeWithText("Songs (1)").performScrollTo().performClick()
        app.onNodeWithText("Timing unavailable").assertExists()
        app.onNodeWithText("Open in Spotify").assertExists()
    }
}
