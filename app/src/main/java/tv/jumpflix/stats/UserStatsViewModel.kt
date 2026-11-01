// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import tv.jumpflix.auth.AccountRepository
import tv.jumpflix.auth.AuthFailure

class UserStatsViewModel(application: Application) : AndroidViewModel(application) {
    private val accounts = AccountRepository.get(application)
    private val config = JSONObject(application.assets.open("auth-config.json").bufferedReader().use { it.readText() })
    private val api = UserStatsApi(config.getString("url"), config.getString("publishableKey"))
    private val mutable = MutableStateFlow(UserStatsState())
    val state = mutable.asStateFlow()
    private var request: Job? = null
    private var generation = 0

    fun load(owner: String) {
        if (mutable.value.ownerId == owner && mutable.value.loading) return
        request?.cancel()
        val current = ++generation
        mutable.update { if (it.ownerId == owner) it.copy(loading = true, error = null) else UserStatsState(ownerId = owner, loading = true) }
        request = viewModelScope.launch {
            try {
                val data = accounts.authenticated(owner) { api.load(it) }
                if (current == generation) mutable.value = UserStatsState(owner, data = data)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val needsSignIn = (error is UserStatsFailure && error.status == 401) || (error is AuthFailure && error.status in setOf(400, 401, 403, 422))
                if (current == generation) mutable.update { it.copy(loading = false, error = if (needsSignIn)
                    "Sign in again from Account to load your stats." else "Your stats could not be refreshed. Check your connection and retry.") }
            }
        }
    }
    fun close() {
        generation++; request?.cancel(); request = null
        mutable.value = UserStatsState()
    }
}
