// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import tv.jumpflix.data.*
import tv.jumpflix.ui.*

@OptIn(ExperimentalTestApi::class)
class ReviewsUiTest {
    @get:Rule val app = createComposeRule()
    private val time = Instant.parse("2026-10-01T10:00:00Z")
    private fun review(body: String = "A visible public review.") = MediaReview(1, "Archive viewer", body, time, time)

    @Test fun guestCanReadAndExpandReviewsWithoutOpeningTheComposer() {
        var more = 0
        val text = "A long public review with readable detail. ".repeat(45)
        app.setContent { JumpflixTheme {
            Column(Modifier.width(360.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
                ReviewsContent(ReviewFeed(listOf(review(text)), ReviewCursor(time, 1), loaded = true), false, remember { FocusRequester() }, {}, { more++ })
            }
        } }
        app.onNodeWithText("Archive viewer").assertExists()
        app.onNodeWithText("Read full review").performScrollTo().performClick()
        app.onNodeWithText("Show less").performScrollTo().performClick()
        app.onNodeWithText("More reviews").performScrollTo().performClick()
        app.runOnIdle { assertEquals(1, more) }
        app.onNodeWithText("Sign in").assertDoesNotExist()
        app.onNodeWithText("Rate & review").assertDoesNotExist()
    }

    @Test fun failedLoadKeepsReviewsVisibleAndTvCanRetryWithoutShowingAnEmptyState() {
        var retries = 0
        app.setContent { JumpflixTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ReviewsContent(ReviewFeed(listOf(review()), loaded = true, error = "Could not load more reviews."), true, remember { FocusRequester() }, { retries++ }, {})
            }
        } }
        app.onNodeWithText("A visible public review.").assertExists()
        app.onNodeWithText("No reviews yet. Be the first to share one.").assertDoesNotExist()
        app.onNodeWithText("Retry reviews").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        app.onNodeWithText("Retry reviews").performKeyInput { pressKey(Key.DirectionCenter) }
        app.runOnIdle { assertEquals(1, retries) }
    }
}
