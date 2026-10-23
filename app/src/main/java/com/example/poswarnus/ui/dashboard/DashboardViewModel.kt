package com.example.poswarnus.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.catalog.CatalogRepository
import com.example.poswarnus.data.catalog.Category
import com.example.poswarnus.data.catalog.MenuItem
import com.example.poswarnus.data.catalog.RefreshResult
import com.example.poswarnus.data.sync.SyncScheduler
import com.example.poswarnus.data.tax.TaxRepository
import com.example.poswarnus.data.transaction.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val visibleItems: List<MenuItem> = emptyList(),
    val isOffline: Boolean = false,
    val loadError: Boolean = false,
    val isLoading: Boolean = false,
    val featuredItems: List<MenuItem> = emptyList(),
    val syncPill: SyncPill = SyncPill.None,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val taxRepository: TaxRepository,
    private val syncScheduler: SyncScheduler,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")

    // Set only by refresh()'s result — whether offline or a hard load error is shown is derived
    // below, inside the same combine as the raw Room flows, so it recomputes the instant Room's
    // (asynchronous) cache actually arrives instead of being frozen at refresh()-completion time.
    private val refreshFailed = MutableStateFlow(false)
    private val isLoading = MutableStateFlow(false)
    private var refreshInProgress = false

    // The raw Room cache and the refresh/loading status are combined in two steps (combine only
    // has a typed overload up to 5 flows) so loadError/isOffline/isLoading are recomputed every
    // time EITHER side changes — in particular, every time Room's cache flows emit, which is
    // asynchronous and can arrive after refresh() has already reported a failure.
    private data class CatalogSnapshot(val categories: List<Category>, val items: List<MenuItem>)
    private data class FilterAndStatus(
        val categoryId: String?,
        val query: String,
        val refreshFailed: Boolean,
        val isLoading: Boolean,
    )

    // Third step: the pill needs the catalog's offline state (derived below) and the sale summary.
    private val catalogState = combine(
        combine(repository.categories, repository.menuItems, ::CatalogSnapshot),
        combine(selectedCategoryId, searchQuery, refreshFailed, isLoading, ::FilterAndStatus),
    ) { snapshot, filterAndStatus ->
        val (categories, items) = snapshot
        val (categoryId, query, failed, loading) = filterAndStatus
        val filtered = items
            .filter { categoryId == null || it.categoryId == categoryId }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        val hasCache = categories.isNotEmpty() || items.isNotEmpty()
        DashboardUiState(
            categories = categories,
            selectedCategoryId = categoryId,
            searchQuery = query,
            visibleItems = filtered,
            isOffline = failed && hasCache,
            loadError = failed && !hasCache,
            isLoading = loading,
            featuredItems = if (query.isBlank()) items.filter { it.isFeatured && it.isAvailable } else emptyList(),
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        catalogState,
        transactionRepository.syncSummary,
    ) { state, summary ->
        state.copy(syncPill = syncPillOf(state.isOffline || state.loadError, summary))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState())

    init {
        refresh()
    }

    fun onCategorySelected(categoryId: String?) { selectedCategoryId.value = categoryId }
    fun onSearchQueryChanged(query: String) { searchQuery.value = query }

    fun refresh() {
        if (refreshInProgress) return
        refreshInProgress = true
        isLoading.value = true
        try {
            syncScheduler.requestSync()
        } catch (e: Exception) {
            // Scheduling is best effort; it must never break the catalog refresh.
        }
        // Independent of the catalog result; TaxRepository.refresh() swallows its own failures.
        viewModelScope.launch { taxRepository.refresh() }
        viewModelScope.launch {
            try {
                when (repository.refresh()) {
                    is RefreshResult.Success -> refreshFailed.value = false
                    is RefreshResult.Failure -> refreshFailed.value = true
                }
            } finally {
                isLoading.value = false
                refreshInProgress = false
            }
        }
    }
}
