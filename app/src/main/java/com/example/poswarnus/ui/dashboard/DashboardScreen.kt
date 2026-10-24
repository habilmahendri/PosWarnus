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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.catalog.MenuItem
import com.example.poswarnus.ui.cart.CartPanel
import com.example.poswarnus.data.training.TrainingModeStore
import com.example.poswarnus.ui.cart.CartStore
import com.example.poswarnus.ui.cart.CartViewModel
import com.example.poswarnus.ui.cart.MenuDetailDialog
import com.example.poswarnus.ui.payment.PaymentDialog
import com.example.poswarnus.ui.payment.PaymentViewModel
import com.example.poswarnus.ui.components.BrandMark
import com.example.poswarnus.ui.components.CloseGlyph
import com.example.poswarnus.ui.components.HistoryGlyph
import com.example.poswarnus.ui.components.SearchGlyph
import com.example.poswarnus.ui.components.SettingsGlyph
import com.example.poswarnus.ui.components.TrainingBanner
import com.example.poswarnus.ui.components.TrainingBannerColors
import com.example.poswarnus.ui.training.TrainingBannerViewModel
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
class LogoutViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val cartStore: CartStore,
    private val trainingModeStore: TrainingModeStore,
) : ViewModel() {
    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            // One cashier's order must never leak into the next session.
            cartStore.clear()
            trainingModeStore.reset()
            onLoggedOut()
        }
    }
}

// Cart panel is fixed-width per the mockup (docs/design-reference.md "Dashboard" § right pane),
// not a proportion of the window — only the catalog pane stretches with available space.
private val CartPaneWidth = 440.dp
private val TabletBreakpoint = 840.dp
// Height of the phone order bar's content (excluding the navigation-bar inset it also pads).
private val PhoneCartBarHeight = 72.dp
private val GridBottomPadding = 16.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onLoggedOut: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    logoutViewModel: LogoutViewModel = hiltViewModel(),
    cartViewModel: CartViewModel = hiltViewModel(),
    paymentViewModel: PaymentViewModel = hiltViewModel(),
    trainingBannerViewModel: TrainingBannerViewModel = hiltViewModel(),
) {
    val state by dashboardViewModel.uiState.collectAsState()
    val cartState by cartViewModel.uiState.collectAsState()
    val paymentState by paymentViewModel.uiState.collectAsState()
    val trainingEnabled by trainingBannerViewModel.enabled.collectAsState()
    val hasLines = cartState.lines.isNotEmpty()
    var showCartSheet by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BackgroundCream)) {
        val isTablet = maxWidth >= TabletBreakpoint
        val windowHeight = maxHeight
        val showPhoneBar = !isTablet && hasLines

        // The sheet only exists on phone width with something in it; reset the flag so it doesn't
        // pop back open when the cart refills or the window narrows again.
        LaunchedEffect(hasLines, isTablet) {
            if (!hasLines || isTablet) showCartSheet = false
        }

        val cartPanel: @Composable (Modifier) -> Unit = { panelModifier ->
            CartPanel(
                state = cartState,
                onLineClick = cartViewModel::onLineClicked,
                onIncrement = cartViewModel::incrementLine,
                onDecrement = cartViewModel::decrementLine,
                onRemove = cartViewModel::removeLine,
                onRequestClear = cartViewModel::requestClearAll,
                onConfirmClear = cartViewModel::confirmClearAll,
                onCancelClear = cartViewModel::cancelClearAll,
                onCheckout = {
                    // Close the phone sheet first so the payment window is not stacked on it.
                    showCartSheet = false
                    paymentViewModel.open()
                },
                modifier = panelModifier,
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
        if (trainingEnabled) {
            TrainingBanner(
                modifier = Modifier
                    .background(TrainingBannerColors.Background)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            )
        }
        // The banner already sits below the status bar, so the panes must not pad the top inset again.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(if (trainingEnabled) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)) else Modifier),
        ) {
            CatalogPane(
                state = state,
                inCartQuantities = cartState.quantityByMenuItem,
                onMenuItemClick = cartViewModel::onMenuItemClicked,
                gridBottomPadding = if (showPhoneBar) GridBottomPadding + PhoneCartBarHeight else GridBottomPadding,
                onCategorySelected = dashboardViewModel::onCategorySelected,
                onSearchQueryChanged = dashboardViewModel::onSearchQueryChanged,
                onRefresh = dashboardViewModel::refresh,
                onLogout = { logoutViewModel.logout(onLoggedOut) },
                onOpenSettings = onOpenSettings,
                onOpenHistory = onOpenHistory,
                modifier = Modifier.weight(1f),
            )
            if (isTablet) {
                VerticalDivider(color = LineDefault, thickness = 1.dp)
                cartPanel(
                    Modifier
                        .width(CartPaneWidth)
                        .fillMaxHeight()
                        .background(SurfaceWhite)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End),
                        ),
                )
            }
        }
        }

        if (showPhoneBar) {
            PhoneCartBar(
                totalQuantity = cartState.totalQuantity,
                total = cartState.totals.grandTotal,
                onOpen = { showCartSheet = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (showCartSheet && showPhoneBar) {
            ModalBottomSheet(
                onDismissRequest = { showCartSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = SurfaceWhite,
            ) {
                // Bounded so the list scrolls inside the sheet; the panel wraps shorter carts.
                cartPanel(Modifier.fillMaxWidth().heightIn(max = windowHeight * 0.85f))
            }
        }

        // Composed after the sheet in the same scope so its window is created above the sheet's,
        // including when both are recreated after a rotation.
        cartState.dialog?.let { dialog ->
            MenuDetailDialog(
                state = dialog,
                onToggle = cartViewModel::toggleOption,
                onIncrement = cartViewModel::incrementQuantity,
                onDecrement = cartViewModel::decrementQuantity,
                onConfirm = cartViewModel::confirmDialog,
                onDismiss = cartViewModel::dismissDialog,
            )
        }

        PaymentDialog(
            state = paymentState,
            onDigits = paymentViewModel::onDigits,
            onBackspace = paymentViewModel::onBackspace,
            onQuickAmount = paymentViewModel::onQuickAmount,
            onExact = paymentViewModel::onExactAmount,
            onConfirm = paymentViewModel::confirm,
            onDismiss = paymentViewModel::dismiss,
            onFinish = paymentViewModel::finish,
        )
    }
}

/**
 * Phone-width order bar (spec 5.3): "{N} item · {Rp total}" with a "Lihat Pesanan" button that
 * opens the cart sheet. Only shown while the cart has lines.
 */
@Composable
private fun PhoneCartBar(
    totalQuantity: Int,
    total: Long,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceWhite)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        HorizontalDivider(color = LineDefault, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(PhoneCartBarHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "$totalQuantity item · ${formatRupiah(total)}",
                style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onOpen,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = SurfaceWhite),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text("Lihat Pesanan", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CatalogHeader(
    compact: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    syncPill: SyncPill,
) {
    @Composable
    fun Brand(modifier: Modifier = Modifier) {
        BrandMark(background = AccentOrange, foreground = SurfaceWhite, modifier = Modifier.size(32.dp))
        Column(modifier = modifier) {
            Text(
                text = "Warung Nusantara",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "RestoPOS",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    @Composable
    fun HistoryButton() {
        IconButton(
            onClick = onOpenHistory,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Riwayat transaksi" },
        ) {
            HistoryGlyph(tint = TextPrimary, modifier = Modifier.size(24.dp))
        }
    }

    @Composable
    fun SettingsButton() {
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "Pengaturan" },
        ) {
            SettingsGlyph(tint = TextPrimary, modifier = Modifier.size(24.dp))
        }
    }

    @Composable
    fun LogoutButton() {
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

    Column {
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceWhite)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Brand(modifier = Modifier.weight(1f))
                    SyncStatusPill(pill = syncPill)
                    HistoryButton()
                    SettingsButton()
                    LogoutButton()
                }
                CatalogSearchField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(SurfaceWhite)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // The brand takes all the slack; a fill=false weight beside a Spacer would hand it only
                // half of what is left and ellipsize the title once the sync pill is on screen.
                Brand(modifier = Modifier.weight(1f))
                SyncStatusPill(pill = syncPill)
                CatalogSearchField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.width(320.dp),
                )
                HistoryButton()
                SettingsButton()
                LogoutButton()
            }
        }
        HorizontalDivider(color = LineDefault, thickness = 1.dp)
    }
}

@Composable
private fun CatalogPane(
    state: DashboardUiState,
    inCartQuantities: Map<String, Int>,
    onMenuItemClick: (MenuItem) -> Unit,
    gridBottomPadding: Dp,
    onCategorySelected: (String?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Header bar — docs/design-reference.md "Dashboard" § left pane header: 56dp, white,
        // bottom border, brand mark + outlet name, search field, logout action on the right.
        // Layout is chosen by this pane's own width, not the window's: on a 840-1040dp window
        // the catalog pane is only 400-600dp wide and needs the compact header too.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            CatalogHeader(
                compact = maxWidth < 600.dp,
                searchQuery = state.searchQuery,
                onSearchQueryChange = onSearchQueryChanged,
                onLogout = onLogout,
                onOpenSettings = onOpenSettings,
                onOpenHistory = onOpenHistory,
                syncPill = state.syncPill,
            )
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
                    contentPadding = PaddingValues(top = 4.dp, bottom = gridBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.featuredItems.isNotEmpty()) {
                        item(key = "featured", span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(
                                    text = "Menu Unggulan",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    items(state.featuredItems, key = { it.id }) { item ->
                                        MenuItemCard(
                                            item = item,
                                            modifier = Modifier.width(176.dp),
                                            inCartQuantity = inCartQuantities[item.id] ?: 0,
                                            onClick = { onMenuItemClick(item) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    items(state.visibleItems, key = { it.id }) { item ->
                        MenuItemCard(
                            item = item,
                            inCartQuantity = inCartQuantities[item.id] ?: 0,
                            onClick = { onMenuItemClick(item) },
                        )
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
