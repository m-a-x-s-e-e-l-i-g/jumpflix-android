// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import tv.jumpflix.data.Catalog
import tv.jumpflix.ui.*
import tv.jumpflix.xpop.*
import java.io.File

@OptIn(ExperimentalTestApi::class)
class XpopUiTest {
    @get:Rule val app = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun film() = Catalog.parse(context.assets.open("catalog-seed.json").bufferedReader().use { it.readText() }).items.first { it.type == "movie" }
    private fun SemanticsNodeInteraction.activate() {
        performScrollTo()
        performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        performKeyInput { pressKey(Key.DirectionCenter) }
    }

    @Test fun balanceShowsAccountBreakdownWithoutAnotherRefreshButton() {
        app.setContent { JumpflixTheme { XpopPanel(XpopState("qa", XpopSummary(2, 3, 4, 7))) } }
        app.onNodeWithText("225 XPOP").assertExists()
        app.onNodeWithText("Archive changes · 7").assertExists()
        app.onNodeWithText("70 XPOP").assertExists()
        app.onNodeWithText("Refresh XPOP").assertDoesNotExist()
        app.onRoot().captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.cacheDir, "xpop-panel-qa.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun signedInViewerCanChooseRatingAndExplicitlyPostReview() {
        val item = film()
        var rating: Int? = null
        var review: String? = null
        app.setContent {
            JumpflixTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    MediaAccountControls(item, MediaAccountState("qa", item.id, loaded = true), true, true, {}, {}, { rating = it }, { review = it }, {})
                }
            }
        }
        app.onNodeWithText("Rate & review").activate()
        app.onNodeWithText("8").activate()
        app.onNodeWithText("Save rating").activate()
        app.runOnIdle { assertEquals(8, rating) }
        app.onNode(hasSetTextAction()).performScrollTo().performTextInput("An emulator-only draft.")
        app.onNode(hasSetTextAction()).performImeAction()
        app.onNodeWithText("Post review").activate()
        app.runOnIdle { assertEquals("An emulator-only draft.", review) }
    }
    @Test fun guestCannotPostAndSyncFailureDoesNotInventZeroBalance() {
        val item = film()
        app.setContent { JumpflixTheme {
            Column {
                MediaAccountControls(item, MediaAccountState(), false, false, {}, {}, {}, {}, {})
                XpopPanel(XpopState(error = "Could not sync XPOP."))
            }
        } }
        app.onNodeWithText("Sign in").assertExists()
        app.onNodeWithText("Post review").assertDoesNotExist()
        app.onNodeWithText("0 XPOP").assertDoesNotExist()
        app.onNodeWithText("Could not sync XPOP.").assertExists()
    }
    @Test fun phoneWidthControlsSupportTouchAndExplicitWatchedAction() {
        val item = film()
        var marked = false
        var rating: Int? = null
        app.setContent { JumpflixTheme {
            Column(Modifier.width(360.dp).fillMaxSize().background(Background).verticalScroll(rememberScrollState()).testTag("phone-xpop")) {
                MediaAccountControls(item, MediaAccountState("qa", item.id, loaded = true), true, false, {}, {}, { rating = it }, {}, { marked = true })
            }
        } }
        app.onNodeWithText("Mark watched · +5 XPOP").performScrollTo().performClick()
        app.runOnIdle { assertTrue(marked) }
        app.onNodeWithText("Rate & review").performClick()
        app.onNodeWithText("10").performScrollTo().performClick()
        app.onNodeWithText("Save rating").performScrollTo().performClick()
        app.runOnIdle { assertEquals(10, rating) }
        app.onNodeWithTag("phone-xpop").captureToImage().asAndroidBitmap().let { bitmap ->
            File(context.cacheDir, "xpop-phone-width-qa.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
