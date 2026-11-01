// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tv.jumpflix.AccountState
import tv.jumpflix.xpop.XpopState

@Composable
internal fun AccountScreen(state: AccountState, tv: Boolean, signIn: (String, String) -> Unit, signOut: () -> Unit, retry: () -> Unit, google: () -> Unit, cancelGoogle: () -> Unit, xpop: XpopState = XpopState(), refreshXpop: () -> Unit = {}) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    // Password state is intentionally neither saveable nor persisted.
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    LaunchedEffect(state.profile) { if (state.profile != null) password = "" }
    LazyColumn(Modifier.fillMaxSize().imePadding().widthIn(max = 720.dp), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Brand() }
        val profile = state.profile
        if (state.awaitingGoogle) {
            item { Text("Finish signing in with Google in your browser, or cancel to try again.", color = Muted) }
            item { AccountButton("Cancel Google sign-in", tv, true, cancelGoogle) }
        }
        if (profile != null) {
            item { Text("Welcome back, ${profile.name}", color = Ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            item { Text("Signed in as ${profile.email}", color = Muted) }
            if ("google" in profile.providers) {
                item { Text("Google connected", color = Ink, fontWeight = FontWeight.Bold) }
            } else {
                item { Text("Connect Google to sign in to this same JUMPFLIX account next time.", color = Muted) }
                item { AccountButton("Connect Google account", tv, !state.loading && !state.awaitingGoogle, google) }
            }
            item { XpopPanel(xpop, tv, refreshXpop) }
            item { Text("Completed watches, ratings, reviews, and XPOP use your website account. Saved titles and resume positions are still stored on this device.", color = Muted) }
            item { AccountButton("Sign out", tv, !state.loading && !state.awaitingGoogle, signOut) }
        } else {
            item { Text("Your JUMPFLIX account", color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium) }
            item { AccountButton("Continue with Google", tv, !state.loading && !state.awaitingGoogle, google) }
            item { Text("Sign in with the email and password you use on jumpflix.tv.", color = Muted) }
            item { OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, enabled = !state.loading && !state.awaitingGoogle, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth()) }
            item {
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, enabled = !state.loading && !state.awaitingGoogle,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { IconButton({ showPassword = !showPassword }) { Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showPassword) "Hide password" else "Show password") } },
                    modifier = Modifier.fillMaxWidth())
            }
            item { AccountButton(if (state.loading) "Signing in…" else "Sign in", tv, !state.loading && !state.awaitingGoogle) { signIn(email, password) } }
            item { Text("Passwords go directly to JUMPFLIX’s account service and are never saved in the app.", color = Muted, style = MaterialTheme.typography.bodySmall) }
            item { HorizontalDivider(color = Surface) }
            item { Text("Forgot your password? Open jumpflix.tv and choose Forgot password to reset it.", color = Muted, style = MaterialTheme.typography.bodyMedium) }
            item { AccountButton("Open JUMPFLIX account help", tv, true) { openExternal(context, "https://www.jumpflix.tv/") } }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent) }
        state.error?.let { message ->
            item { Text(message, color = MaterialTheme.colorScheme.error) }
            if (profile == null) item { AccountButton("Retry saved session", tv, !state.loading, retry) }
        }
    }
}

@Composable
private fun AccountButton(text: String, tv: Boolean, enabled: Boolean, action: () -> Unit) {
    if (tv) androidx.tv.material3.Button(action, enabled = enabled) { androidx.tv.material3.Text(text) }
    else Button(action, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp)) { Text(text) }
}
