// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tv.jumpflix.AccountState
import tv.jumpflix.xpop.XpopState

@Composable
internal fun AccountScreen(state: AccountState, tv: Boolean, signIn: (String, String) -> Unit, signOut: () -> Unit, retry: () -> Unit, google: () -> Unit, cancelGoogle: () -> Unit, xpop: XpopState = XpopState(), refreshXpop: () -> Unit = {}, navigationVersion: Int = 0, about: () -> Unit = {}) {
    val context = LocalContext.current
    val list = rememberLazyListState()
    var showStats by rememberSaveable(state.profile?.id, navigationVersion) { mutableStateOf(false) }
    val statsOwner = state.profile?.id
    if (showStats && statsOwner != null) {
        UserStatsScreen(statsOwner, tv) { showStats = false; refreshXpop() }
        return
    }
    var email by remember { mutableStateOf("") }
    // Password state is intentionally neither saveable nor persisted.
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    LaunchedEffect(state.profile) { if (state.profile != null) password = "" }
    RefreshableScreen(list, state.loading || xpop.loading, if (state.profile == null) retry else refreshXpop, tv, "Refresh account", "account-refresh", Modifier.widthIn(max = 640.dp).fillMaxSize().imePadding()) {
        LazyColumn(Modifier.fillMaxSize().testTag("account-list"), state = list, contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item { PageHeading("Account", if (state.profile == null) "Bring your JUMPFLIX account with you." else null) }
            val profile = state.profile
            if (state.awaitingGoogle) {
                item { Text("Finish signing in with Google in your browser, or cancel to try again.", color = Muted) }
                item { AccountButton("Cancel Google sign-in", tv, true, cancelGoogle) }
            }
            if (profile != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Welcome back, ${profile.name}", color = Ink, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Signed in as ${profile.email}", color = Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if ("google" in profile.providers) {
                    item { Row(Modifier.background(SurfaceRaised, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.CheckCircle, null, tint = Muted, modifier = Modifier.size(16.dp))
                        Text("Google connected", color = Ink, style = MaterialTheme.typography.labelMedium)
                    } }
                } else {
                    item { Text("Connect Google to sign in to this same JUMPFLIX account next time.", color = Muted) }
                    item { AccountButton("Connect Google account", tv, !state.loading && !state.awaitingGoogle, google) }
                }
                item { AppAction("Your stats", tv, modifier = Modifier.fillMaxWidth(), icon = Icons.Outlined.BarChart, enabled = !state.loading) { showStats = true } }
                item { XpopPanel(xpop) }
                item { Text("Watches, ratings, reviews, and XPOP sync with your account. My list and resume positions stay on this device.", color = Muted, style = MaterialTheme.typography.bodySmall) }
                item { AccountButton("Sign out", tv, !state.loading && !state.awaitingGoogle, signOut) }
            } else {
                item { AppAction("Continue with Google", tv, primary = true, enabled = !state.loading && !state.awaitingGoogle, modifier = Modifier.fillMaxWidth(), action = google) }
                item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(Modifier.weight(1f), color = Outline)
                    Text("or sign in with email", color = Muted, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider(Modifier.weight(1f), color = Outline)
                } }
                item { OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, enabled = !state.loading && !state.awaitingGoogle, shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth()) }
                item {
                    OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, enabled = !state.loading && !state.awaitingGoogle,
                        shape = RoundedCornerShape(12.dp), visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = { IconButton({ showPassword = !showPassword }) { Icon(if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showPassword) "Hide password" else "Show password") } },
                        modifier = Modifier.fillMaxWidth())
                }
                item { AppAction(if (state.loading) "Signing in…" else "Sign in", tv, primary = true, enabled = !state.loading && !state.awaitingGoogle, modifier = Modifier.fillMaxWidth()) { signIn(email, password) } }
                item { Text("Passwords go directly to JUMPFLIX’s account service and are never saved in the app.", color = Muted, style = MaterialTheme.typography.bodySmall) }
                item { Text("Forgot your password? Reset it on jumpflix.tv.", color = Muted, style = MaterialTheme.typography.bodySmall) }
                item { AccountButton("Open JUMPFLIX account help", tv, true) { openExternal(context, "https://www.jumpflix.tv/") } }
            }
            state.error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
                if (profile == null) item { AccountButton("Retry saved session", tv, !state.loading, retry) }
            }
            item { AccountButton("About JUMPFLIX", tv, true, about) }
        }
    }
}

@Composable
private fun AccountButton(text: String, tv: Boolean, enabled: Boolean, action: () -> Unit) {
    AppAction(text, tv, enabled = enabled, action = action)
}
