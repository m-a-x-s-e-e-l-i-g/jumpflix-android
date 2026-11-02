// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.xpop

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import tv.jumpflix.auth.AccountRepository

class XpopRepository private constructor(context: Context) {
    private val accounts = AccountRepository.get(context)
    private val queue = XpopWatchQueue(context)
    private val api: XpopApi
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operations = Mutex()
    private val mutable = MutableStateFlow(XpopState())
    val state = mutable.asStateFlow()
    private var rewardId = 0L
    init {
        val config = JSONObject(context.assets.open("auth-config.json").bufferedReader().use { it.readText() })
        api = XpopApi(config.getString("url"), config.getString("publishableKey"))
    }
    suspend fun playbackOwner(): String? = accounts.signedInUserId()
    fun bind(owner: String?) { mutable.update { if (it.ownerId == owner) it else XpopState(ownerId = owner) } }
    fun dismissReward(id: Long) { mutable.update { if (it.reward?.id == id) it.copy(reward = null) else it } }

    suspend fun refresh(owner: String) = operations.withLock {
        if (mutable.value.ownerId != owner) return@withLock
        mutable.update { if (it.ownerId == owner) it.copy(loading = true, error = null) else it }
        try {
            val result = accounts.authenticated(owner) { session ->
                var added = 0
                for (item in queue.forOwner(owner)) {
                    if (api.markWatched(session, item)) added += 5
                    queue.remove(owner, item.mediaId)
                }
                api.summary(session) to added
            }
            mutable.update { if (it.ownerId == owner) it.copy(summary = result.first,
                reward = if (result.second > 0) XpopReward(++rewardId, result.second, "Watched") else it.reward) else it }
            try {
                val watched = accounts.authenticated(owner) { api.watchedMovies(it) + queue.forOwner(owner).map { pending -> pending.mediaId } }
                mutable.update { if (it.ownerId == owner) it.copy(watched = watched, loading = false) else it }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.update { if (it.ownerId == owner) it.copy(loading = false, error = "Your watched history could not refresh.") else it } }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) { mutable.update { if (it.ownerId == owner) it.copy(loading = false, error = "XPOP could not sync. Completed watches will retry when you refresh or reopen the app.") else it } }
    }

    fun completed(owner: String, item: WatchedItem) {
        scope.launch {
            try {
                queue.add(owner, item)
                mutable.update { if (it.ownerId == owner) it.copy(watched = it.watched?.plus(item.mediaId)) else it }
                refresh(owner)
            }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.update { if (it.ownerId == owner) it.copy(error = "Your completed watch could not be queued. Please mark it watched from the title page.") else it } }
        }
    }
    suspend fun details(owner: String, itemId: String) = accounts.authenticated(owner) { api.details(it, itemId) }

    suspend fun markWatched(owner: String, itemId: String, item: WatchedItem): MediaAccountState {
        withContext(Dispatchers.IO) { queue.add(owner, item) }
        mutable.update { if (it.ownerId == owner) it.copy(watched = it.watched?.plus(item.mediaId)) else it }
        refresh(owner)
        if (withContext(Dispatchers.IO) { queue.forOwner(owner).any { it.mediaId == item.mediaId } }) throw java.io.IOException("Your watch is waiting to sync.")
        return details(owner, itemId)
    }

    suspend fun rate(owner: String, itemId: String, value: Int?): MediaAccountState = edit(owner, itemId, "Rating locked in", 15) { session -> api.saveRating(session, itemId.substringAfter(':').toLong(), value) }
    suspend fun review(owner: String, itemId: String, body: String?): MediaAccountState = edit(owner, itemId, "Review posted", 25) { session -> api.saveReview(session, itemId.substringAfter(':').toLong(), body) }

    private suspend fun edit(owner: String, itemId: String, label: String, amount: Int, action: (tv.jumpflix.auth.AuthSession) -> Unit): MediaAccountState = operations.withLock {
        val result = accounts.authenticated(owner) { session ->
            val before = api.details(session, itemId)
            action(session)
            val after = api.summary(session)
            val saved = api.details(session, itemId)
            Triple(saved, after, if (amount == 15) before.rating == null && saved.rating != null else before.review.isBlank() && saved.review.isNotBlank())
        }
        mutable.update { if (it.ownerId == owner) it.copy(summary = result.second,
            reward = if (result.third) XpopReward(++rewardId, amount, label) else it.reward, error = null) else it }
        result.first
    }

    companion object {
        @Volatile private var instance: XpopRepository? = null
        fun get(context: Context): XpopRepository = instance ?: synchronized(this) {
            instance ?: XpopRepository(context.applicationContext).also { instance = it }
        }
    }
}
