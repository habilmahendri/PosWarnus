package com.example.poswarnus.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogoutViewModel @Inject constructor(private val authRepository: AuthRepository) : ViewModel() {
    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onLoggedOut()
        }
    }
}

@Composable
fun DashboardScreen(
    onLoggedOut: () -> Unit,
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    logoutViewModel: LogoutViewModel = hiltViewModel(),
) {
    val state by dashboardViewModel.uiState.collectAsState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 840.dp
        Row(modifier = Modifier.fillMaxSize()) {
            CatalogPane(
                state = state,
                onCategorySelected = dashboardViewModel::onCategorySelected,
                onSearchQueryChanged = dashboardViewModel::onSearchQueryChanged,
                onRefresh = dashboardViewModel::refresh,
                onLogout = { logoutViewModel.logout(onLoggedOut) },
                modifier = if (isTablet) Modifier.weight(1f) else Modifier.fillMaxWidth(),
            )
            if (isTablet) {
                Column(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                    // Empty placeholder panel — cart UI lands in Phase 3.
                }
            }
        }
    }
}

@Composable
private fun CatalogPane(
    state: DashboardUiState,
    onCategorySelected: (String?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Warung Nusantara", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onLogout) { Text("Logout") }
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text("Cari menu...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                CategoryChip(
                    category = null,
                    isSelected = state.selectedCategoryId == null,
                    onClick = { onCategorySelected(null) },
                )
            }
            items(state.categories, key = { it.id }) { category ->
                CategoryChip(
                    category = category,
                    isSelected = state.selectedCategoryId == category.id,
                    onClick = { onCategorySelected(category.id) },
                )
            }
        }

        if (state.isOffline) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable(onClick = onRefresh),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "Offline — menampilkan data tersimpan. Ketuk untuk mencoba lagi.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        when {
            state.loadError -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Tidak bisa memuat menu, periksa koneksi",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = onRefresh,
                            enabled = !state.isLoading,
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text("Coba lagi")
                        }
                    }
                }
            }
            state.isLoading && state.categories.isEmpty() && state.visibleItems.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.visibleItems.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Menu tidak ditemukan",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.visibleItems, key = { it.id }) { item ->
                        MenuItemCard(item = item, onClick = { })
                    }
                }
            }
        }
    }
}
