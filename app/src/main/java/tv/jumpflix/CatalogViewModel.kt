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
    private val mutable = MutableStateFlow(CatalogState(saved = library.saved(), progress = library.progress()))
    val state = mutable.asStateFlow()

    init {
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
}
