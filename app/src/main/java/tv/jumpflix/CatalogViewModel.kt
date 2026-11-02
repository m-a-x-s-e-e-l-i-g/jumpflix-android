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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.jumpflix.data.*
import tv.jumpflix.auth.AccountProfile
import tv.jumpflix.auth.AccountRepository
import tv.jumpflix.auth.AuthFailure
import tv.jumpflix.auth.GoogleOAuth
import tv.jumpflix.xpop.*

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
    val music: Map<String, List<MusicCredit>> = emptyMap(),
    val musicLoading: Boolean = false,
    val musicError: String? = null,
)

class CatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CatalogRepository(application)
    private val musicRepository = MusicSearchRepository(application)
    private val library = LibraryStore(application)
    private val accounts = AccountRepository.get(application)
    private val rewards = XpopRepository.get(application)
    val xpop = rewards.state
    private val interactionMutable = MutableStateFlow(MediaAccountState())
    val interaction = interactionMutable.asStateFlow()
    private var interactionGeneration = 0
    private val accountMutable = MutableStateFlow(AccountState())
    val account = accountMutable.asStateFlow()
    private var authGeneration = 0
    private val mutable = MutableStateFlow(CatalogState())
    val state = mutable.asStateFlow()

    init {
        restoreAccount()
        viewModelScope.launch {
            val local = withContext(Dispatchers.IO) { library.saved() to library.progress() }
            mutable.update { it.copy(saved = local.first, progress = local.second) }
            repository.saved()?.let { catalog -> mutable.update { it.copy(catalog = catalog) } }
            musicRepository.saved()?.let { music -> mutable.update { it.copy(music = music) } }
            refresh()
        }
    }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        refreshMusic()
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
    private var musicRefreshing = false

    fun refreshMusic() {
        if (musicRefreshing) return
        musicRefreshing = true
        mutable.update { it.copy(musicLoading = true, musicError = null) }
        viewModelScope.launch {
            try {
                val music = musicRepository.refresh()
                mutable.update { it.copy(music = music, musicLoading = false) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { mutable.update { it.copy(musicLoading = false, musicError = "Song credits could not be refreshed.") }
            } finally { musicRefreshing = false }
        }
    }

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
    fun reloadLibrary() {
        viewModelScope.launch {
            val progress = withContext(Dispatchers.IO) { library.progress() }
            mutable.update { it.copy(progress = progress) }
        }
    }

    fun restoreAccount() {
        if (accountMutable.value.loading || accountMutable.value.awaitingGoogle) return
        val generation = ++authGeneration
        accountMutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val profile = accounts.restore()
                if (generation == authGeneration) {
                    accountMutable.update { it.copy(profile = profile, loading = false, error = null) }
                    accountReady(profile)
                }
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
            try { accountMutable.value = AccountState(profile = accounts.signIn(email, password)); accountReady(accountMutable.value.profile) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { accountMutable.update { it.copy(loading = false, error = if (error is AuthFailure) error.message else "Could not reach the account service. Check your connection and try again.") } }
        }
    }

    fun signOut() {
        if (accountMutable.value.loading) return
        accountMutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try { accounts.signOut(); accountMutable.value = AccountState(); accountReady(null) }
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
                if (generation == authGeneration) { accountMutable.update { it.copy(profile = profile, loading = false) }; accountReady(profile) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                if (generation == authGeneration) accountMutable.update { it.copy(loading = false, error = when (error) {
                    is IllegalArgumentException -> error.message
                    else -> "Google sign-in could not be completed. Please start it again from the app."
                }) }
            }
        }
    }

    private fun accountReady(profile: AccountProfile?) {
        rewards.bind(profile?.id)
        if (profile == null) { interactionGeneration++; interactionMutable.value = MediaAccountState(); return }
        refreshXpop()
        mutable.value.selected?.let(::loadInteractions)
    }
    fun refreshXpop() {
        val owner = accountMutable.value.profile?.id ?: return
        viewModelScope.launch { rewards.refresh(owner) }
    }
    fun dismissXpop(id: Long) { rewards.dismissReward(id) }
    fun loadInteractions(item: CatalogItem) {
        val owner = accountMutable.value.profile?.id ?: run { interactionMutable.value = MediaAccountState(); return }
        if (interactionMutable.value.busy && interactionMutable.value.itemId == item.id && interactionMutable.value.ownerId == owner) return
        val generation = ++interactionGeneration
        interactionMutable.value = MediaAccountState(ownerId = owner, itemId = item.id)
        viewModelScope.launch {
            try {
                val result = rewards.details(owner, item.id)
                if (generation == interactionGeneration && accountMutable.value.profile?.id == owner) interactionMutable.value = result
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (generation == interactionGeneration && accountMutable.value.profile?.id == owner) interactionMutable.update { it.copy(error = "Could not load your rating and watched status. Please retry.") }
            }
        }
    }
    private fun changeInteraction(item: CatalogItem, action: suspend (String) -> MediaAccountState) {
        val owner = accountMutable.value.profile?.id ?: return
        val current = interactionMutable.value
        if (current.itemId != item.id || !current.loaded || current.busy || current.ownerId != owner) return
        val generation = ++interactionGeneration
        interactionMutable.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val result = action(owner)
                if (generation == interactionGeneration && accountMutable.value.profile?.id == owner) interactionMutable.value = result
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (generation == interactionGeneration && accountMutable.value.profile?.id == owner) interactionMutable.update { it.copy(busy = false, error = "Could not confirm this change. Retry to check the saved state.") }
            }
        }
    }
    fun rate(item: CatalogItem, rating: Int?) = changeInteraction(item) { rewards.rate(it, item.id, rating) }
    fun review(item: CatalogItem, body: String?) = changeInteraction(item) { rewards.review(it, item.id, body) }
    fun markWatched(item: CatalogItem, episode: Episode? = null) = changeInteraction(item) { owner ->
        val key = watchKey(item.id, item.playback, episode?.id)
        rewards.markWatched(owner, item.id, WatchedItem(key, if (episode == null) "movie" else "episode"))
    }
}
