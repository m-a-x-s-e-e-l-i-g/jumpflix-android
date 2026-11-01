// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.jumpflix.data.*
import tv.jumpflix.auth.AccountProfile
import tv.jumpflix.auth.AccountRepository
import tv.jumpflix.auth.AuthFailure
import tv.jumpflix.auth.GoogleOAuth

data class AccountState(val profile: AccountProfile? = null, val loading: Boolean = false, val error: String? = null,
    val googleUrl: String? = null, val awaitingGoogle: Boolean = false, val oauthResult: Int = 0)

data class CatalogState(
    val catalog: Catalog? = null,
    val loading: Boolean = true,
    val cached: Boolean = true,
    val error: String? = null,
    val saved: Set<String> = emptySet(),
    val progress: List<Progress> = emptyList(),
    val selected: CatalogItem? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
)

class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CatalogRepository(application)
    private val library = LibraryStore(application)
    private val accounts = AccountRepository(application)
    private val accountMutable = MutableStateFlow(AccountState())
    val account = accountMutable.asStateFlow()
    private var authGeneration = 0
    private val mutable = MutableStateFlow(CatalogState(saved = library.saved(), progress = library.progress()))
    val state = mutable.asStateFlow()

    init {
        restoreAccount()
        viewModelScope.launch {
            repository.saved()?.let { catalog -> mutable.update { it.copy(catalog = catalog) } }
            refresh()
        }
    }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val catalog = repository.refresh()
                mutable.update { it.copy(catalog = catalog, cached = false, loading = false) }
            } catch (exception: CancellationException) { throw exception
            } catch (exception: Exception) {
                mutable.update { it.copy(loading = false, error = exception.message ?: "Could not refresh the catalog.") }
            } finally { refreshing = false }
        }
    }
    private var refreshing = false

    fun select(item: CatalogItem) {
        mutable.update { it.copy(selected = item, detailError = null, detailLoading = item.type == "series") }
        if (item.type != "series") return
        viewModelScope.launch {
            try {
                val detail = repository.series(item.slug)
                mutable.update { if (it.selected?.id == item.id) it.copy(selected = detail, detailLoading = false) else it }
            } catch (exception: CancellationException) { throw exception
            } catch (_: Exception) {
                mutable.update { if (it.selected?.id == item.id) it.copy(detailLoading = false, detailError = "Episodes could not be refreshed. You can open this series on JUMPFLIX.") else it }
            }
        }
    }
    fun closeDetail() { mutable.update { it.copy(selected = null, detailLoading = false, detailError = null) } }
    fun toggleSaved(id: String) { mutable.update { it.copy(saved = library.toggle(id)) } }
    fun reloadLibrary() { mutable.update { it.copy(progress = library.progress()) } }

    fun restoreAccount() {
        if (accountMutable.value.loading || accountMutable.value.awaitingGoogle) return
        val generation = ++authGeneration
        accountMutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val profile = accounts.restore()
                if (generation == authGeneration) accountMutable.update { it.copy(profile = profile, loading = false, error = null) }
            }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { if (generation == authGeneration) accountMutable.update { it.copy(loading = false, error = "Could not restore your session. Please sign in again or retry when connected.") } }
        }
    }

    fun signIn(email: String, password: String) {
        if (accountMutable.value.loading) return
        if (email.isBlank() || password.isEmpty()) {
            accountMutable.update { it.copy(error = "Enter your JUMPFLIX email and password.") }
            return
        }
        accountMutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try { accountMutable.value = AccountState(profile = accounts.signIn(email, password)) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { accountMutable.update { it.copy(loading = false, error = if (error is AuthFailure) error.message else "Could not reach the account service. Check your connection and try again.") } }
        }
    }

    fun signOut() {
        if (accountMutable.value.loading) return
        accountMutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try { accounts.signOut(); accountMutable.value = AccountState() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { accountMutable.update { it.copy(loading = false, error = "Could not sign out securely. Check your connection and try again.") } }
        }
    }

    fun beginGoogle() {
        if (accountMutable.value.loading || accountMutable.value.awaitingGoogle) return
        ++authGeneration
        accountMutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val url = accounts.beginGoogle()
                accountMutable.update { it.copy(loading = false, googleUrl = url, awaitingGoogle = true) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { accountMutable.update { it.copy(loading = false, error = "Could not start Google sign-in. Please try again.") } }
        }
    }
    fun googleOpened() { accountMutable.update { it.copy(googleUrl = null) } }
    fun cancelGoogle() {
        ++authGeneration
        accountMutable.update { it.copy(googleUrl = null, awaitingGoogle = false, loading = false) }
        viewModelScope.launch { accounts.cancelGoogle() }
    }
    fun handleGoogleReturn(value: String?) {
        val uri = value?.let { runCatching { java.net.URI(it) }.getOrNull() } ?: return
        if (!GoogleOAuth.isReturnLink(uri)) return
        val generation = ++authGeneration
        accountMutable.update { it.copy(loading = true, googleUrl = null, awaitingGoogle = false, error = null, oauthResult = it.oauthResult + 1) }
        viewModelScope.launch {
            try {
                val profile = accounts.completeGoogle(uri)
                if (generation == authGeneration) accountMutable.update { it.copy(profile = profile, loading = false) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation == authGeneration) accountMutable.update { it.copy(loading = false, error = when (error) {
                    is IllegalArgumentException -> error.message
                    else -> "Google sign-in could not be completed. Please start it again from the app."
                }) }
            }
        }
    }
}
