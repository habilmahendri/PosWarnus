package com.example.poswarnus.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.catalog.CatalogRepository
import com.example.poswarnus.data.catalog.Category
import com.example.poswarnus.data.catalog.MenuItem
import com.example.poswarnus.data.catalog.RefreshResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: String? = null,
    val searchQuery: String = "",
    val visibleItems: List<MenuItem> = emptyList(),
    val isOffline: Boolean = false,
    val loadError: Boolean = false,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {
    private val selectedCategoryId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val loadStatus = MutableStateFlow(LoadStatus())

    private data class LoadStatus(val isOffline: Boolean = false, val loadError: Boolean = false)

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.categories, repository.menuItems, selectedCategoryId, searchQuery, loadStatus,
    ) { categories, items, categoryId, query, status ->
        val filtered = items
            .filter { categoryId == null || it.categoryId == categoryId }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
        DashboardUiState(
            categories = categories,
            selectedCategoryId = categoryId,
            searchQuery = query,
            visibleItems = filtered,
            isOffline = status.isOffline,
            loadError = status.loadError,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DashboardUiState())

    init {
        refresh()
    }

    fun onCategorySelected(categoryId: String?) { selectedCategoryId.value = categoryId }
    fun onSearchQueryChanged(query: String) { searchQuery.value = query }

    fun refresh() {
        viewModelScope.launch {
            when (repository.refresh()) {
                is RefreshResult.Success -> loadStatus.update { it.copy(isOffline = false, loadError = false) }
                is RefreshResult.Failure -> {
                    val hasCache = uiState.value.visibleItems.isNotEmpty() || uiState.value.categories.isNotEmpty()
                    loadStatus.update { LoadStatus(isOffline = hasCache, loadError = !hasCache) }
                }
            }
        }
    }
}
