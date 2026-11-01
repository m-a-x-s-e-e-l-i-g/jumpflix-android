// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import tv.jumpflix.auth.AccountProfile
import tv.jumpflix.ui.AccountScreen
import tv.jumpflix.ui.JumpflixTheme
import tv.jumpflix.xpop.XpopState
import tv.jumpflix.xpop.XpopSummary

class AccountScreenTest {
    @get:Rule val app = createComposeRule()

    private fun show(profile: AccountProfile?) {
        app.setContent { JumpflixTheme { AccountScreen(AccountState(profile = profile), false, { _, _ -> }, {}, {}, {}, {}) } }
    }
    @Test fun googleLinkedAccountShowsConnectionInsteadOfAnotherLogin() {
        show(AccountProfile("test-user", "viewer@example.test", "Viewer", setOf("email", "google")))
        app.onNodeWithText("Google connected").assertExists()
        app.onNodeWithText("Continue with Google").assertDoesNotExist()
        app.onNodeWithText("Connect Google account").assertDoesNotExist()
    }
    @Test fun passwordAccountCanConnectGoogleWithoutShowingSignIn() {
        show(AccountProfile("test-user", "viewer@example.test", "Viewer", setOf("email")))
        app.onNodeWithText("Connect Google account").assertExists()
        app.onNodeWithText("Continue with Google").assertDoesNotExist()
    }
    @Test fun signedOutAccountShowsGoogleSignIn() {
        show(null)
        app.onNodeWithText("Continue with Google").assertExists()
        app.onNodeWithText("Connect Google account").assertDoesNotExist()
    }
    @Test fun signedInPullRefreshKeepsIdentityAndDoesNotStartAnotherSignIn() {
        var refreshed = 0
        var retriedAuth = 0
        val profile = AccountProfile("test-user", "viewer@example.test", "Viewer", setOf("google"))
        app.setContent { JumpflixTheme { AccountScreen(AccountState(profile = profile), false, { _, _ -> }, {}, { retriedAuth++ }, {}, {},
            XpopState(profile.id, XpopSummary(2, 3, 4, 7)), { refreshed++ }) } }
        app.onNodeWithTag("account-list").performTouchInput { swipeDown(startY = height * .2f, endY = height * .9f, durationMillis = 600) }
        app.runOnIdle { assertEquals(1, refreshed); assertEquals(0, retriedAuth) }
        app.onNodeWithText("Google connected").assertExists()
        app.onNodeWithText("Continue with Google").assertDoesNotExist()
        app.onNodeWithText("Refresh XPOP").assertDoesNotExist()
    }
}
