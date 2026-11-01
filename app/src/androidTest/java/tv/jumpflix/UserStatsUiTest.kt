// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tv.jumpflix.auth.AccountProfile
import tv.jumpflix.stats.*
import tv.jumpflix.ui.*
import java.io.File

@OptIn(ExperimentalTestApi::class)
class UserStatsUiTest {
    @get:Rule val app = createComposeRule()
    private fun fixture() = UserStats(
        StatsOverview(12, 8, 1, 3, 7200, 9000, 100, 50, 12, 2, 8.5),
        List(10) { if (it == 7) 12L else 0L },
        (1L..12L).map { RatedTitle(StatsTitle(it, "Film $it", "movie", "film-$it"), 8) },
        listOf(StatsReview(1, StatsTitle(1, "Film 1", "movie", "film-1"), "Fixture review body", "2026-10-30T10:00:00Z")),
        ContributionStats(2, 3, 1, 4)
    )
    private fun scrollTo(text: String) { app.onNode(hasScrollAction()).performScrollToNode(hasText(text)) }
    private fun SemanticsNodeInteraction.activate() {
        performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        performKeyInput { pressKey(Key.DirectionCenter) }
    }
    private fun capture(tag: String, file: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        app.onNodeWithTag(tag).captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.cacheDir, file).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun tvStatsShowBreakdownDistributionAndPaginatedRatings() {
        app.setContent { JumpflixTheme { Box(Modifier.fillMaxSize().background(Background).testTag("stats-tv")) {
            UserStatsContent(UserStatsState("qa", data = fixture()), true, {}, {})
        } } }
        app.onNodeWithText("330 XPOP").assertExists()
        scrollTo("Saved playback time")
        app.onNodeWithText("2h 0m").assertExists()
        app.onNodeWithText("8.5 / 10").assertExists()
        capture("stats-tv", "stats-tv-qa.png")
        scrollTo("Rating distribution")
        app.onNodeWithContentDescription("8 out of 10: 12 ratings").assertExists()
        scrollTo("Next ratings")
        app.onNodeWithText("Next ratings").activate()
        scrollTo("Film 11 · 8/10")
        app.onNodeWithText("Film 11 · 8/10").assertExists()
        scrollTo("Fixture review body")
        app.onNodeWithText("Fixture review body").assertExists()
    }
    @Test fun phoneStatsShowEmptyAccountAndTouchRefreshWithoutInventingRatings() {
        var refreshes = 0
        val empty = UserStats(StatsOverview(), List(10) { 0L }, emptyList(), emptyList(), ContributionStats())
        app.setContent { JumpflixTheme { Box(Modifier.width(360.dp).fillMaxSize().background(Background).testTag("stats-phone")) {
            UserStatsContent(UserStatsState("qa", data = empty), false, { refreshes++ }, {})
        } } }
        app.onNodeWithText("Refresh stats").performClick()
        app.runOnIdle { assertEquals(1, refreshes) }
        scrollTo("Average rating")
        app.onNodeWithText("—").assertExists()
        capture("stats-phone", "stats-phone-qa.png")
        scrollTo("No ratings yet. Rate a title to start your chart.")
        app.onNodeWithText("No ratings yet. Rate a title to start your chart.").assertExists()
        scrollTo("No reviews yet.")
        app.onNodeWithText("No reviews yet.").assertExists()
    }
    @Test fun failureOffersRetryWithoutPresentingAZeroAccount() {
        app.setContent { JumpflixTheme { UserStatsContent(UserStatsState("qa", error = "Could not load stats."), false, {}, {}) } }
        app.onNodeWithText("Could not load stats.").assertExists()
        app.onNodeWithText("Refresh stats").assertIsEnabled()
        app.onNodeWithText("0 XPOP").assertDoesNotExist()
    }
    @Test fun accountEntryReturnsOnBackAndResetsWhenAccountChanges() {
        val account = mutableStateOf(AccountState(profile = AccountProfile("fixture-a", "viewer@example.test", "Viewer", setOf("google"))))
        app.setContent { JumpflixTheme { AccountScreen(account.value, false, { _, _ -> }, {}, {}, {}, {}) } }
        app.onNodeWithText("Your stats").performScrollTo().performClick()
        app.onNodeWithText("Back to account").performClick()
        app.onNodeWithText("Google connected").assertExists()
        app.onNodeWithText("Your stats").performScrollTo().performClick()
        app.runOnIdle { account.value = AccountState(profile = AccountProfile("fixture-b", "second@example.test", "Second", setOf("google"))) }
        app.onNodeWithText("Welcome back, Second").assertExists()
        app.onNodeWithText("Back to account").assertDoesNotExist()
        app.runOnIdle { account.value = AccountState() }
        app.onNodeWithText("Your stats").assertDoesNotExist()
        app.onNodeWithText("Continue with Google").assertExists()
    }
}
