package com.example.poswarnus.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.ui.components.BrandMark
import com.example.poswarnus.ui.components.CloseGlyph
import com.example.poswarnus.ui.components.EmptyCartGlyph
import com.example.poswarnus.ui.components.SearchGlyph
import com.example.poswarnus.ui.theme.AccentOrange
import com.example.poswarnus.ui.theme.BackgroundCream
import com.example.poswarnus.ui.theme.LineDefault
import com.example.poswarnus.ui.theme.LineStrong
import com.example.poswarnus.ui.theme.SurfaceWhite
import com.example.poswarnus.ui.theme.TextPrimary
import com.example.poswarnus.ui.theme.TextSecondary
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

// Cart panel is fixed-width per the mockup (docs/design-reference.md "Dashboard" § right pane),
// not a proportion of the window — only the catalog pane stretches with available space.
private val CartPaneWidth = 440.dp
private val TabletBreakpoint = 840.dp

@Composable
fun DashboardScreen(
    onLoggedOut: () -> Unit,
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    logoutViewModel: LogoutViewModel = hiltViewModel(),
) {
    val state by dashboardViewModel.uiState.collectAsState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BackgroundCream)) {
        val isTablet = maxWidth >= TabletBreakpoint
        Row(modifier = Modifier.fillMaxSize()) {
            CatalogPane(
                state = state,
                onCategorySelected = dashboardViewModel::onCategorySelected,
                onSearchQueryChanged = dashboardViewModel::onSearchQueryChanged,
                onRefresh = dashboardViewModel::refresh,
                onLogout = { logoutViewModel.logout(onLoggedOut) },
                modifier = Modifier.weight(1f),
            )
            if (isTablet) {
                CartPane(modifier = Modifier.width(CartPaneWidth).fillMaxHeight())
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
        // Header bar — docs/design-reference.md "Dashboard" § left pane header: 56dp, white,
        // bottom border, brand mark + outlet name, search field, logout action on the right.
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(SurfaceWhite)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrandMark(background = AccentOrange, foreground = SurfaceWhite, modifier = Modifier.size(32.dp))
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(text = "Warung Nusantara", style = MaterialTheme.typography.bodyLarge)
                    Text(text = "RestoPOS", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Spacer(modifier = Modifier.weight(1f))
                CatalogSearchField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChanged,
                    modifier = Modifier.width(320.dp),
                )
                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, LineStrong),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    modifier = Modifier.heightIn(min = 44.dp),
                ) {
                    Text("Keluar", style = MaterialTheme.typography.bodyMedium)
                }
            }
            HorizontalDivider(color = LineDefault, thickness = 1.dp)
        }

        // Category row — docs/design-reference.md "Dashboard" § category row.
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                shape = RoundedCornerShape(6.dp),
                color = LineDefault,
            ) {
                Text(
                    text = "Offline — menampilkan data tersimpan. Ketuk untuk mencoba lagi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
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
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp),
                        ) {
                            Text("Coba lagi")
                        }
                    }
                }
            }
            state.isLoading && state.categories.isEmpty() && state.visibleItems.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentOrange)
                }
            }
            state.visibleItems.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SearchGlyph(tint = TextSecondary, modifier = Modifier.size(40.dp))
                        Text(
                            text = "Menu tidak ditemukan",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            text = "Coba kata kunci lain atau pilih kategori Semua.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        if (state.searchQuery.isNotEmpty() || state.selectedCategoryId != null) {
                            OutlinedButton(
                                onClick = {
                                    onSearchQueryChanged("")
                                    onCategorySelected(null)
                                },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, LineStrong),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                                modifier = Modifier.padding(top = 8.dp).heightIn(min = 44.dp),
                            ) {
                                Text("Hapus pencarian")
                            }
                        }
                    }
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
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

/**
 * Custom search field matching the mockup's `.search` style (44dp, 1px line-strong border,
 * 6dp radius, leading magnifier, trailing clear button once text is typed) rather than
 * Material3's default [androidx.compose.material3.OutlinedTextField], whose minimum height and
 * outline styling can't match it.
 */
@Composable
private fun CatalogSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor = if (focused) AccentOrange else LineStrong
    Box(
        modifier = modifier
            .height(44.dp)
            .background(SurfaceWhite, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp)),
    ) {
        SearchGlyph(
            tint = TextSecondary,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(18.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
            cursorBrush = SolidColor(AccentOrange),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart)
                .padding(start = 40.dp, end = if (value.isEmpty()) 12.dp else 40.dp)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { innerField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Cari menu...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }
                    innerField()
                }
            },
        )
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(44.dp)
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                CloseGlyph(tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Right-hand order panel. No cart state exists yet in this phase, so this renders the mockup's
 * documented empty-cart scene verbatim (project/Dashboard-Empty.dc.html in the reference
 * artifact) rather than an ad-hoc placeholder.
 */
@Composable
private fun CartPane(modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        VerticalDivider(color = LineDefault, thickness = 1.dp)
        Column(modifier = Modifier.fillMaxHeight().weight(1f).background(SurfaceWhite)) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(text = "Pesanan", style = MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider(color = LineDefault, thickness = 1.dp)
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyCartGlyph(tint = TextSecondary, modifier = Modifier.size(44.dp))
                    Text(
                        text = "Belum ada pesanan",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = "Pilih menu di sebelah kiri untuk mulai menambahkan item.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
