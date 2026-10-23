package com.example.poswarnus.ui.dashboard

import app.cash.turbine.test
import com.example.poswarnus.data.catalog.Category
import com.example.poswarnus.data.catalog.CatalogRepository
import com.example.poswarnus.data.catalog.MenuItem
import com.example.poswarnus.data.catalog.OptionGroup
import com.example.poswarnus.data.catalog.RefreshResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeCatalogRepository(
    var refreshResult: RefreshResult = RefreshResult.Success,
) : CatalogRepository {
    val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
    val menuItemsFlow = MutableStateFlow<List<MenuItem>>(emptyList())
    override val categories: Flow<List<Category>> = categoriesFlow
    override val menuItems: Flow<List<MenuItem>> = menuItemsFlow
    override fun observeOptionGroups(menuItemId: String): Flow<List<OptionGroup>> = flowOf(emptyList())
    var refreshCallCount = 0
        private set
    /** When set, refresh() suspends here until the test completes it — lets a test observe an in-flight refresh. */
    var refreshGate: CompletableDeferred<Unit>? = null

    override suspend fun refresh(): RefreshResult {
        refreshCallCount++
        refreshGate?.await()
        return refreshResult
    }
}

private val makanan = Category("a", "Makanan", 1)
private val minuman = Category("b", "Minuman", 2)
private val nasiGoreng = MenuItem("1", "a", "Nasi Goreng", 25000.0, null, true)
private val esTeh = MenuItem("2", "b", "Es Teh", 5000.0, null, true)
private val sotoHabis = MenuItem("3", "a", "Soto Ayam", 22000.0, null, false)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(repository: FakeCatalogRepository) = DashboardViewModel(repository).also {
        dispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `visibleItems shows all items when no category or search filter is set`() = runTest {
        val repository = FakeCatalogRepository()
        repository.categoriesFlow.value = listOf(makanan, minuman)
        repository.menuItemsFlow.value = listOf(nasiGoreng, esTeh)
        val viewModel = viewModel(repository)

        assertEquals(listOf(nasiGoreng, esTeh), viewModel.uiState.value.visibleItems)
    }

    @Test
    fun `selecting a category filters visibleItems to that category only`() = runTest {
        val repository = FakeCatalogRepository()
        repository.menuItemsFlow.value = listOf(nasiGoreng, esTeh)
        val viewModel = viewModel(repository)

        viewModel.onCategorySelected("a")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(nasiGoreng), viewModel.uiState.value.visibleItems)
    }

    @Test
    fun `search query filters within the active category`() = runTest {
        val repository = FakeCatalogRepository()
        repository.menuItemsFlow.value = listOf(nasiGoreng, sotoHabis)
        val viewModel = viewModel(repository)

        viewModel.onCategorySelected("a")
        viewModel.onSearchQueryChanged("nasi")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(nasiGoreng), viewModel.uiState.value.visibleItems)
    }

    @Test
    fun `refresh failure with an existing cache sets isOffline without clearing items`() = runTest {
        val repository = FakeCatalogRepository(refreshResult = RefreshResult.Failure("network down"))
        repository.menuItemsFlow.value = listOf(nasiGoreng)
        val viewModel = viewModel(repository)

        viewModel.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.isOffline)
        assertEquals(listOf(nasiGoreng), viewModel.uiState.value.visibleItems)
    }

    @Test
    fun `refresh failure with no cache at all sets loadError`() = runTest {
        val repository = FakeCatalogRepository(refreshResult = RefreshResult.Failure("network down"))
        val viewModel = viewModel(repository)

        viewModel.refresh()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.loadError)
    }

    @Test
    fun `loadError flips to isOffline once Room's cache arrives asynchronously, without another refresh`() = runTest {
        // Simulates a fast network failure (e.g. airplane mode) that completes before Room's
        // Flow has emitted its first (asynchronous) read from disk.
        val repository = FakeCatalogRepository(refreshResult = RefreshResult.Failure("network down"))
        val viewModel = viewModel(repository)

        assertEquals(true, viewModel.uiState.value.loadError)
        assertEquals(false, viewModel.uiState.value.isOffline)

        // Room's cache arrives late — no refresh() is called again.
        repository.menuItemsFlow.value = listOf(nasiGoreng)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, viewModel.uiState.value.loadError)
        assertEquals(true, viewModel.uiState.value.isOffline)
        assertEquals(listOf(nasiGoreng), viewModel.uiState.value.visibleItems)
    }

    @Test
    fun `isLoading is true only while a refresh is in flight and a concurrent refresh is a no-op`() = runTest {
        val repository = FakeCatalogRepository()
        val gate = CompletableDeferred<Unit>()
        repository.refreshGate = gate
        // init() kicks off the first refresh, which now suspends on the gate mid-flight.
        val viewModel = viewModel(repository)

        assertEquals(true, viewModel.uiState.value.isLoading)
        assertEquals(1, repository.refreshCallCount)

        // A second refresh while one is in flight must not launch another repository.refresh().
        viewModel.refresh()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, repository.refreshCallCount)
        assertEquals(true, viewModel.uiState.value.isLoading)

        gate.complete(Unit)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isLoading)
    }
}
