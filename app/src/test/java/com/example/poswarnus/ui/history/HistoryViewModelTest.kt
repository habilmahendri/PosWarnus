package com.example.poswarnus.ui.history

import com.example.poswarnus.data.sync.SyncSummary
import com.example.poswarnus.data.transaction.HistoryEntry
import com.example.poswarnus.data.transaction.HistoryLine
import com.example.poswarnus.data.transaction.SaleSnapshot
import com.example.poswarnus.data.transaction.TransactionRepository
import com.example.poswarnus.data.transaction.shortCode
import com.example.poswarnus.ui.dashboard.formatRupiah
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

private class FakeHistoryRepository(initial: List<HistoryEntry> = emptyList()) : TransactionRepository {
    val entries = MutableStateFlow(initial)
    var requestedFrom: Long? = null
    override val syncSummary: Flow<SyncSummary> = flowOf(SyncSummary(0, 0, null))

    override suspend fun save(sale: SaleSnapshot, cashReceived: Long): Result<Unit> = Result.success(Unit)

    override fun observeHistory(fromMillis: Long): Flow<List<HistoryEntry>> {
        requestedFrom = fromMillis
        return entries
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun utcMillis(day: Int, hour: Int, minute: Int, second: Int = 0, ms: Int = 0): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, minute, second)
            set(Calendar.MILLISECOND, ms)
        }.timeInMillis

    private val now = utcMillis(24, 15, 30)
    private val startOfToday = utcMillis(24, 0, 0)
    private val startOfYesterday = utcMillis(23, 0, 0)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun entry(
        id: String,
        createdAt: Long,
        grandTotal: Long = 10_000,
        names: List<String> = listOf("Nasi Goreng"),
        isPending: Boolean = false,
    ): HistoryEntry {
        val lines = names.map { HistoryLine(it, "", 1, grandTotal, grandTotal) }
        return HistoryEntry(
            id = id,
            code = shortCode(id),
            createdAt = createdAt,
            lines = lines,
            itemCount = lines.size,
            subtotal = grandTotal,
            taxLabel = "Pajak",
            taxBasisPoints = 0,
            taxAmount = 0,
            grandTotal = grandTotal,
            cashReceived = grandTotal,
            changeAmount = 0,
            isPending = isPending,
        )
    }

    private fun viewModel(repository: FakeHistoryRepository) =
        HistoryViewModel(repository, now = { now }, timeZone = { utc })

    private fun TestScope.collect(vm: HistoryViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
    }

    @Test
    fun `sale at 23_59_59_999 yesterday is Kemarin and midnight is Hari ini`() = runTest(UnconfinedTestDispatcher()) {
        val lateYesterday = entry("aaaaaa01", utcMillis(23, 23, 59, 59, 999))
        val midnight = entry("bbbbbb02", startOfToday)
        val vm = viewModel(FakeHistoryRepository(listOf(midnight, lateYesterday)))
        collect(vm)

        assertEquals(listOf("bbbbbb02"), vm.uiState.value.rows.map { it.id })
        vm.onDayChange(HistoryDay.YESTERDAY)
        assertEquals(listOf("aaaaaa01"), vm.uiState.value.rows.map { it.id })
    }

    @Test
    fun `a sale older than the start of yesterday is in neither tab`() = runTest(UnconfinedTestDispatcher()) {
        val old = entry("cccccc03", startOfYesterday - 1)
        val vm = viewModel(FakeHistoryRepository(listOf(old)))
        collect(vm)

        assertTrue(vm.uiState.value.dayIsEmpty)
        vm.onDayChange(HistoryDay.YESTERDAY)
        assertTrue(vm.uiState.value.dayIsEmpty)
        assertTrue(vm.uiState.value.rows.isEmpty())
    }

    @Test
    fun `repository is called with the start of yesterday`() {
        val repository = FakeHistoryRepository()
        viewModel(repository)

        assertEquals(startOfYesterday, repository.requestedFrom)
    }

    @Test
    fun `summary counts the tab's sales and sums grand totals`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeHistoryRepository(
            listOf(
                entry("a00001", startOfToday + 3_000, grandTotal = 10_000),
                entry("a00002", startOfToday + 2_000, grandTotal = 25_000),
                entry("a00003", startOfToday + 1_000, grandTotal = 5_500),
                entry("b00001", startOfYesterday + 1_000, grandTotal = 99_000),
            ),
        )
        val vm = viewModel(repository)
        collect(vm)

        assertEquals("3 transaksi · ${formatRupiah(40_500L)}", vm.uiState.value.summary)
        vm.onQueryChange("a00001")
        assertEquals(1, vm.uiState.value.rows.size)
        assertEquals("3 transaksi · ${formatRupiah(40_500L)}", vm.uiState.value.summary)
    }

    @Test
    fun `search matches code with and without hash, any case, and item names`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeHistoryRepository(
            listOf(
                entry("abc12345-0000", startOfToday + 2_000, names = listOf("Es Teh")),
                entry("def67890-0000", startOfToday + 1_000, names = listOf("Mie Ayam", "Kopi Susu")),
            ),
        )
        val vm = viewModel(repository)
        collect(vm)

        fun idsFor(query: String): List<String> {
            vm.onQueryChange(query)
            return vm.uiState.value.rows.map { it.id }
        }

        assertEquals(listOf("abc12345-0000"), idsFor("abc123"))
        assertEquals(listOf("abc12345-0000"), idsFor("#ABC123"))
        assertEquals(listOf("abc12345-0000"), idsFor("#abc"))
        assertEquals(listOf("def67890-0000"), idsFor("kopi"))
        assertEquals(listOf("def67890-0000"), idsFor("  MIE ayam "))
        assertEquals(2, idsFor("").size)
    }

    @Test
    fun `search with no match gives empty rows but dayIsEmpty false`() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(FakeHistoryRepository(listOf(entry("a00001", startOfToday + 1))))
        collect(vm)

        vm.onQueryChange("zzzz")

        assertTrue(vm.uiState.value.rows.isEmpty())
        assertFalse(vm.uiState.value.dayIsEmpty)
        assertNull(vm.uiState.value.selected)
    }

    @Test
    fun `a tab without sales has dayIsEmpty true`() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(FakeHistoryRepository(listOf(entry("a00001", startOfToday + 1))))
        collect(vm)

        assertFalse(vm.uiState.value.dayIsEmpty)
        vm.onDayChange(HistoryDay.YESTERDAY)
        assertTrue(vm.uiState.value.dayIsEmpty)
        assertEquals("0 transaksi · Rp0", vm.uiState.value.summary)
    }

    @Test
    fun `first row is selected when nothing is chosen`() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(
            FakeHistoryRepository(listOf(entry("a00001", startOfToday + 2), entry("a00002", startOfToday + 1))),
        )
        collect(vm)

        assertEquals("a00001", vm.uiState.value.selected?.id)
    }

    @Test
    fun `onSelect keeps the id when the list changes`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeHistoryRepository(
            listOf(entry("a00001", startOfToday + 2), entry("a00002", startOfToday + 1)),
        )
        val vm = viewModel(repository)
        collect(vm)

        vm.onSelect("a00002")
        repository.entries.value = listOf(entry("a00003", startOfToday + 3)) + repository.entries.value

        assertEquals("a00002", vm.uiState.value.selected?.id)
    }

    @Test
    fun `a search that hides the selected row falls back to the first match`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeHistoryRepository(
            listOf(
                entry("a00001", startOfToday + 2, names = listOf("Es Teh")),
                entry("a00002", startOfToday + 1, names = listOf("Mie Ayam")),
            ),
        )
        val vm = viewModel(repository)
        collect(vm)
        vm.onSelect("a00002")

        vm.onQueryChange("teh")

        assertEquals("a00001", vm.uiState.value.selected?.id)
    }

    @Test
    fun `two entries with the same code select by full id`() = runTest(UnconfinedTestDispatcher()) {
        val first = entry("abc123-1111", startOfToday + 2)
        val second = entry("abc123-2222", startOfToday + 1)
        assertEquals(first.code, second.code)
        val vm = viewModel(FakeHistoryRepository(listOf(first, second)))
        collect(vm)

        vm.onSelect("abc123-2222")

        assertEquals("abc123-2222", vm.uiState.value.selected?.id)
    }

    @Test
    fun `onSelect opens detail and onCloseDetail closes it`() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(FakeHistoryRepository(listOf(entry("a00001", startOfToday + 1))))
        collect(vm)
        assertFalse(vm.uiState.value.detailOpen)

        vm.onSelect("a00001")
        assertTrue(vm.uiState.value.detailOpen)

        vm.onCloseDetail()
        assertFalse(vm.uiState.value.detailOpen)
    }

    @Test
    fun `changing tab or search closes detail and clears the choice`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeHistoryRepository(
            listOf(entry("a00001", startOfToday + 2), entry("a00002", startOfToday + 1)),
        )
        val vm = viewModel(repository)
        collect(vm)

        vm.onSelect("a00002")
        vm.onQueryChange("a0000")
        assertFalse(vm.uiState.value.detailOpen)
        assertEquals("a00001", vm.uiState.value.selected?.id)

        vm.onSelect("a00002")
        vm.onDayChange(HistoryDay.YESTERDAY)
        vm.onDayChange(HistoryDay.TODAY)
        assertFalse(vm.uiState.value.detailOpen)
        assertEquals("a00001", vm.uiState.value.selected?.id)
    }

    @Test
    fun `a row that turns synced drops isPending and keeps the selection`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeHistoryRepository(
            listOf(entry("a00001", startOfToday + 2), entry("a00002", startOfToday + 1, isPending = true)),
        )
        val vm = viewModel(repository)
        collect(vm)
        vm.onSelect("a00002")
        assertTrue(vm.uiState.value.rows.first { it.id == "a00002" }.isPending)

        repository.entries.value = repository.entries.value.map { it.copy(isPending = false) }

        assertFalse(vm.uiState.value.rows.first { it.id == "a00002" }.isPending)
        assertEquals("a00002", vm.uiState.value.selected?.id)
        assertFalse(vm.uiState.value.selected!!.isPending)
    }

    @Test
    fun `selectedWhen reads Hari ini or Kemarin plus HH_mm`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeHistoryRepository(
            listOf(
                entry("a00001", utcMillis(24, 9, 5)),
                entry("b00001", utcMillis(23, 18, 42)),
            ),
        )
        val vm = viewModel(repository)
        collect(vm)

        assertEquals("Hari ini · 09.05", vm.uiState.value.selectedWhen)
        assertEquals("09.05", vm.uiState.value.rows.single().timeText)
        vm.onDayChange(HistoryDay.YESTERDAY)
        assertEquals("Kemarin · 18.42", vm.uiState.value.selectedWhen)
    }
}
