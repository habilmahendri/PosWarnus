package com.example.poswarnus.ui.cart

import com.example.poswarnus.data.catalog.CatalogRepository
import com.example.poswarnus.data.catalog.Category
import com.example.poswarnus.data.catalog.MenuItem
import com.example.poswarnus.data.catalog.Option
import com.example.poswarnus.data.catalog.OptionGroup
import com.example.poswarnus.data.catalog.OptionKind
import com.example.poswarnus.data.catalog.RefreshResult
import com.example.poswarnus.data.catalog.SelectionMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeCatalogRepository : CatalogRepository {
    val menuItemsFlow = MutableStateFlow<List<MenuItem>>(emptyList())
    val groupsByItem = mutableMapOf<String, List<OptionGroup>>()

    /** When set, observeOptionGroups suspends until completed, so a test can see the loading state. */
    var groupsGate: CompletableDeferred<Unit>? = null

    /** When set, observeOptionGroups fails with it instead of emitting. */
    var groupsError: Throwable? = null

    override val categories: Flow<List<Category>> = flowOf(emptyList())
    override val menuItems: Flow<List<MenuItem>> = menuItemsFlow
    override fun observeOptionGroups(menuItemId: String): Flow<List<OptionGroup>> = flow {
        groupsGate?.await()
        groupsError?.let { throw it }
        emit(groupsByItem[menuItemId].orEmpty())
    }
    override suspend fun refresh(): RefreshResult = RefreshResult.Success
}

private val nasiGoreng = MenuItem("1", "a", "Nasi Goreng", 25000.0, null, true)
private val esTeh = MenuItem("2", "b", "Es Teh", 5000.0, null, true)
private val sotoHabis = MenuItem("3", "a", "Soto Ayam", 22000.0, null, false)

private val regular = Option("o-reg", "g-size", "Regular", 0, 1, true)
private val large = Option("o-lg", "g-size", "Large", 5000, 2, true)
private val keju = Option("o-keju", "g-top", "Extra Keju", 3000, 1, true)
private val telur = Option("o-telur", "g-top", "Extra Telur", 4000, 2, true)

private val sizeGroup = OptionGroup("g-size", "1", "Ukuran", OptionKind.VARIANT, SelectionMode.SINGLE, true, 1, listOf(regular, large))
private val toppingGroup = OptionGroup("g-top", "1", "Topping", OptionKind.MODIFIER, SelectionMode.MULTI, false, 2, listOf(keju, telur))

private fun sel(o: Option, g: OptionGroup) = SelectedOption(o.id, g.id, o.name, o.priceDelta, g.kind)

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCatalogRepository().apply {
        menuItemsFlow.value = listOf(nasiGoreng, esTeh, sotoHabis)
        groupsByItem["1"] = listOf(sizeGroup, toppingGroup)
    }
    private val cartStore = CartStore()

    @Before
    fun setUp() { Dispatchers.setMain(dispatcher) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun advance() = dispatcher.scheduler.advanceUntilIdle()

    private fun viewModel() = CartViewModel(repository, cartStore).also { advance() }

    private val CartViewModel.state get() = uiState.value

    /** Adds Nasi Goreng with the given options and quantity through the dialog. */
    private fun CartViewModel.addNasi(vararg picks: Pair<String, String>, quantity: Int = 1) {
        onMenuItemClicked(nasiGoreng)
        advance()
        picks.forEach { (g, o) -> toggleOption(g, o) }
        repeat(quantity - 1) { incrementQuantity() }
        confirmDialog()
        advance()
    }

    @Test
    fun `unavailable item does not open the dialog`() = runTest {
        val vm = viewModel()

        vm.onMenuItemClicked(sotoHabis)
        advance()

        assertNull(vm.state.dialog)
    }

    @Test
    fun `available item opens New dialog and draft loads from groups`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.groupsGate = gate
        val vm = viewModel()

        vm.onMenuItemClicked(nasiGoreng)
        advance()

        val loading = vm.state.dialog
        assertNotNull(loading)
        assertEquals(DialogTarget.New("1"), loading!!.target)
        assertEquals(nasiGoreng, loading.item)
        assertNull(loading.draft)

        gate.complete(Unit)
        advance()

        val draft = vm.state.dialog!!.draft
        assertNotNull(draft)
        assertEquals(listOf(sizeGroup, toppingGroup), draft!!.groups)
        assertEquals(emptyMap<String, Set<String>>(), draft.selected)
        assertEquals(1, draft.quantity)
    }

    @Test
    fun `failing option group load closes the dialog`() = runTest {
        repository.groupsError = IllegalStateException("db closed")
        val vm = viewModel()

        vm.onMenuItemClicked(nasiGoreng)
        advance()

        assertNull(vm.state.dialog)
        vm.confirmDialog()
        advance()
        assertTrue(vm.state.lines.isEmpty())
    }

    @Test
    fun `toggle then confirm adds a line with snapshot selections`() = runTest {
        val vm = viewModel()

        vm.addNasi("g-size" to "o-lg", "g-top" to "o-keju", quantity = 2)

        val lines = vm.state.lines
        assertEquals(1, lines.size)
        val line = lines.single().line
        assertEquals("1", line.menuItemId)
        assertEquals("Nasi Goreng", line.name)
        assertEquals(25000L, line.basePrice)
        assertEquals(listOf(sel(large, sizeGroup), sel(keju, toppingGroup)), line.selections)
        assertEquals(2, line.quantity)
        assertEquals(66000L, vm.state.subtotal)
        assertEquals(2, vm.state.totalQuantity)
        assertEquals(mapOf("1" to 2), vm.state.quantityByMenuItem)
        assertFalse(lines.single().unavailable)
        assertNull(vm.state.dialog)
    }

    @Test
    fun `confirm is blocked while a required group is unmet`() = runTest {
        val vm = viewModel()
        vm.onMenuItemClicked(nasiGoreng)
        advance()

        vm.toggleOption("g-top", "o-keju")
        vm.confirmDialog()
        advance()

        assertTrue(vm.state.lines.isEmpty())
        assertNotNull(vm.state.dialog)
        assertEquals(false, vm.state.dialog!!.draft!!.canSubmit)
    }

    @Test
    fun `double confirm adds only one line`() = runTest {
        val vm = viewModel()
        vm.onMenuItemClicked(esTeh)
        advance()

        vm.confirmDialog()
        vm.confirmDialog()
        advance()

        assertEquals(1, vm.state.lines.size)
        assertEquals(1, vm.state.lines.single().line.quantity)
        assertNull(vm.state.dialog)
    }

    @Test
    fun `dismiss closes without adding`() = runTest {
        val vm = viewModel()
        vm.onMenuItemClicked(esTeh)
        advance()

        vm.dismissDialog()
        vm.confirmDialog()
        advance()

        assertNull(vm.state.dialog)
        assertTrue(vm.state.lines.isEmpty())
    }

    @Test
    fun `line click opens Edit dialog pre-filled`() = runTest {
        val vm = viewModel()
        vm.addNasi("g-size" to "o-lg", "g-top" to "o-keju", quantity = 2)
        val lineId = vm.state.lines.single().line.lineId

        vm.onLineClicked(lineId)
        advance()

        val dialog = vm.state.dialog!!
        assertEquals(DialogTarget.Edit(lineId), dialog.target)
        assertEquals(nasiGoreng, dialog.item)
        assertEquals(mapOf("g-size" to setOf("o-lg"), "g-top" to setOf("o-keju")), dialog.draft!!.selected)
        assertEquals(2, dialog.draft!!.quantity)
    }

    @Test
    fun `edit confirm updates in place without duplicate`() = runTest {
        val vm = viewModel()
        vm.addNasi("g-size" to "o-lg", "g-top" to "o-keju")
        val lineId = vm.state.lines.single().line.lineId

        vm.onLineClicked(lineId)
        advance()
        vm.toggleOption("g-top", "o-telur")
        vm.incrementQuantity()
        vm.confirmDialog()
        advance()

        val line = vm.state.lines.single().line
        assertEquals(lineId, line.lineId)
        assertEquals(
            listOf(sel(large, sizeGroup), sel(keju, toppingGroup), sel(telur, toppingGroup)),
            line.selections,
        )
        assertEquals(2, line.quantity)
        assertNull(vm.state.dialog)
    }

    @Test
    fun `edit to a config equal to another line merges`() = runTest {
        val vm = viewModel()
        vm.addNasi("g-size" to "o-lg")
        vm.addNasi("g-size" to "o-reg", quantity = 2)
        val (lineA, lineB) = vm.state.lines.map { it.line.lineId }

        vm.onLineClicked(lineB)
        advance()
        vm.toggleOption("g-size", "o-lg")
        vm.confirmDialog()
        advance()

        val merged = vm.state.lines.single().line
        assertEquals(lineA, merged.lineId)
        assertEquals(3, merged.quantity)
        assertNull(vm.state.dialog)
    }

    @Test
    fun `edit drops an option that vanished and re-applies required check`() = runTest {
        val vm = viewModel()
        vm.addNasi("g-size" to "o-lg", "g-top" to "o-keju")
        val lineId = vm.state.lines.single().line.lineId
        // Catalog refresh: Large and Extra Keju are gone.
        repository.groupsByItem["1"] = listOf(
            sizeGroup.copy(options = listOf(regular)),
            toppingGroup.copy(options = listOf(telur)),
        )

        vm.onLineClicked(lineId)
        advance()
        val draft = vm.state.dialog!!.draft!!
        assertEquals(emptyMap<String, Set<String>>(), draft.selected)
        assertFalse(draft.canSubmit)

        vm.confirmDialog()
        advance()
        assertNotNull(vm.state.dialog)
        assertEquals(
            listOf(sel(large, sizeGroup), sel(keju, toppingGroup)),
            vm.state.lines.single().line.selections,
        )

        vm.toggleOption("g-size", "o-reg")
        vm.confirmDialog()
        advance()
        assertEquals(listOf(sel(regular, sizeGroup)), vm.state.lines.single().line.selections)
        assertNull(vm.state.dialog)
    }

    @Test
    fun `line whose menu item no longer exists is flagged unavailable and click is a no-op`() = runTest {
        val vm = viewModel()
        vm.onMenuItemClicked(esTeh)
        advance()
        vm.confirmDialog()
        advance()
        val lineId = vm.state.lines.single().line.lineId

        repository.menuItemsFlow.value = listOf(nasiGoreng)
        advance()

        assertTrue(vm.state.lines.single().unavailable)
        vm.onLineClicked(lineId)
        advance()
        assertNull(vm.state.dialog)
    }

    @Test
    fun `line whose menu item became unavailable is flagged and still editable`() = runTest {
        val vm = viewModel()
        vm.addNasi("g-size" to "o-lg")
        val lineId = vm.state.lines.single().line.lineId

        repository.menuItemsFlow.value = listOf(nasiGoreng.copy(isAvailable = false), esTeh)
        advance()

        assertTrue(vm.state.lines.single().unavailable)
        vm.onLineClicked(lineId)
        advance()
        val dialog = vm.state.dialog!!
        assertEquals(DialogTarget.Edit(lineId), dialog.target)
        assertNotNull(dialog.draft)
    }

    @Test
    fun `decrementLine at quantity 1 removes the line`() = runTest {
        val vm = viewModel()
        vm.addNasi("g-size" to "o-lg")
        val lineId = vm.state.lines.single().line.lineId

        vm.incrementLine(lineId)
        advance()
        assertEquals(2, vm.state.lines.single().line.quantity)

        vm.decrementLine(lineId)
        vm.decrementLine(lineId)
        advance()
        assertTrue(vm.state.lines.isEmpty())
    }

    @Test
    fun `removeLine deletes the line`() = runTest {
        val vm = viewModel()
        vm.addNasi("g-size" to "o-lg", quantity = 3)

        vm.removeLine(vm.state.lines.single().line.lineId)
        advance()

        assertTrue(vm.state.lines.isEmpty())
    }

    @Test
    fun `clear all needs confirmation`() = runTest {
        val vm = viewModel()

        vm.requestClearAll()
        advance()
        assertFalse(vm.state.showClearConfirm)

        vm.addNasi("g-size" to "o-lg")
        vm.requestClearAll()
        advance()
        assertTrue(vm.state.showClearConfirm)
        assertEquals(1, vm.state.lines.size)

        vm.cancelClearAll()
        advance()
        assertFalse(vm.state.showClearConfirm)
        assertEquals(1, vm.state.lines.size)

        vm.requestClearAll()
        vm.confirmClearAll()
        advance()
        assertFalse(vm.state.showClearConfirm)
        assertTrue(vm.state.lines.isEmpty())
    }

    @Test
    fun `clear confirm hides when the cart becomes empty`() = runTest {
        val vm = viewModel()
        vm.addNasi("g-size" to "o-lg")
        vm.requestClearAll()
        advance()
        assertTrue(vm.state.showClearConfirm)

        vm.removeLine(vm.state.lines.single().line.lineId)
        advance()
        assertFalse(vm.state.showClearConfirm)

        // Refilling the cart must not resurface the stale request.
        vm.addNasi("g-size" to "o-lg")
        assertFalse(vm.state.showClearConfirm)
    }

    @Test
    fun `logout via store clear removes lines and dialog`() = runTest {
        val vm = viewModel()
        vm.addNasi("g-size" to "o-lg")
        vm.onLineClicked(vm.state.lines.single().line.lineId)
        advance()
        assertNotNull(vm.state.dialog)

        cartStore.clear()
        advance()

        assertTrue(vm.state.lines.isEmpty())
        assertNull(vm.state.dialog)
        // A late confirm on the vanished Edit target must not resurrect anything.
        vm.confirmDialog()
        advance()
        assertTrue(vm.state.lines.isEmpty())
        assertNull(vm.state.dialog)
    }
}
