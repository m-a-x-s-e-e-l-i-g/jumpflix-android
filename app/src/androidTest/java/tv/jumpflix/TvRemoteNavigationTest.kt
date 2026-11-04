// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import android.view.KeyEvent
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

/** Raw Android key events: deliberately never force a control's focus through semantics. */
class TvRemoteNavigationTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private fun key(code: Int, source: Int = InputDevice.SOURCE_DPAD) {
        val time = SystemClock.uptimeMillis()
        val event = KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, source)
        val input = InstrumentationRegistry.getInstrumentation().uiAutomation
        assertTrue(input.injectInputEvent(event, true))
        assertTrue(input.injectInputEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_UP), true))
        app.waitForIdle()
    }
    private fun home() {
        app.waitUntil(30_000) { app.onAllNodesWithText("Discover film").fetchSemanticsNodes().isNotEmpty() }
        app.onNode(hasContentDescription("Home") and hasClickAction()).assertIsFocused()
    }

    @Test fun remoteMovesFromMenuToFilmAndKeepsWorkingAfterBack() {
        home()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        app.onNodeWithText("Discover film").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        app.onNodeWithTag("title-details").assertExists()
        app.onNodeWithText("Back").assertIsFocused()
        key(KeyEvent.KEYCODE_BACK)
        app.onNode(hasContentDescription("Home") and hasClickAction()).assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        app.onNode(hasContentDescription("Search") and hasClickAction()).assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        app.onNode(hasSetTextAction()).assertExists()
        key(KeyEvent.KEYCODE_BACK)
        app.onNode(hasContentDescription("Home") and hasClickAction()).assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        app.onNodeWithText("Discover film").assertIsFocused()
    }

    @Test fun remoteCanTraverseAndOpenPortraitCards() {
        home()
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        val poster = isFocused() and hasClickAction() and hasAnyAncestor(hasTestTag("film-row-New"))
        val first = app.onNode(poster).fetchSemanticsNode().id
        key(KeyEvent.KEYCODE_DPAD_RIGHT)
        assertNotEquals(first, app.onNode(poster).fetchSemanticsNode().id)
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        app.onNodeWithTag("title-details").assertExists()
    }

    @Test fun tvHomeHasOnlyOnePopcornCharacter() {
        home()
        app.onAllNodesWithContentDescription("JUMPFLIX popcorn character").assertCountEquals(1)
    }

    @Test fun remoteReachesAboutAndReturnsHomeWithOneMenuMascot() {
        home()
        repeat(4) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
        app.onNode(hasContentDescription("About") and hasClickAction()).assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        app.onNodeWithText("Parkour cinema,\nwherever you watch.").assertExists()
        app.onAllNodesWithContentDescription("JUMPFLIX popcorn character").assertCountEquals(1)
        key(KeyEvent.KEYCODE_BACK)
        app.onNode(hasContentDescription("Home") and hasClickAction()).assertIsFocused()
    }

    @Test fun keyboardArrowsAndEnterCanOpenSearch() {
        home()
        key(KeyEvent.KEYCODE_DPAD_DOWN, InputDevice.SOURCE_KEYBOARD)
        app.onNode(hasContentDescription("Search") and hasClickAction()).assertIsFocused()
        key(KeyEvent.KEYCODE_ENTER, InputDevice.SOURCE_KEYBOARD)
        app.onNode(hasSetTextAction()).assertExists()
    }
}
