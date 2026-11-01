// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import tv.jumpflix.auth.AccountProfile
import tv.jumpflix.ui.AccountScreen
import tv.jumpflix.ui.JumpflixTheme

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
}
