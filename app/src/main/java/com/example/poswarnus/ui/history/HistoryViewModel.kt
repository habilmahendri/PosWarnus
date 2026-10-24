package com.example.poswarnus.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.poswarnus.data.transaction.HistoryEntry
import com.example.poswarnus.data.transaction.TransactionRepository
import com.example.poswarnus.ui.dashboard.formatRupiah
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

enum class HistoryDay { TODAY, YESTERDAY }

data class HistoryRow(
    val id: String,
    val code: String,
    val timeText: String,
    val itemCount: Int,
    val itemNames: String,
    val grandTotal: Long,
    val isPending: Boolean,
)

data class HistoryUiState(
    val day: HistoryDay = HistoryDay.TODAY,
    val query: String = "",
    val rows: List<HistoryRow> = emptyList(),
    val summary: String = "0 transaksi · Rp0",
    val selected: HistoryEntry? = null,
    val selectedWhen: String = "",
    val dayIsEmpty: Boolean = true,
    val detailOpen: Boolean = false,
)

@HiltViewModel
class HistoryViewModel(
    repository: TransactionRepository,
    private val now: () -> Long,
    private val timeZone: () -> TimeZone,
) : ViewModel() {
    // Dagger cannot use default arguments, so production wiring goes through this constructor.
    @Inject
    constructor(repository: TransactionRepository) : this(
        repository,
        System::currentTimeMillis,
        TimeZone::getDefault,
    )

    private val day = MutableStateFlow(HistoryDay.TODAY)
    private val query = MutableStateFlow("")
    private val chosenId = MutableStateFlow<String?>(null)
    private val detailOpen = MutableStateFlow(false)

    private val history = repository.observeHistory(startOfDay(now(), timeZone(), daysBack = 1))

    val uiState: StateFlow<HistoryUiState> =
        combine(history, day, query, chosenId, detailOpen) { entries, day, query, chosenId, detailOpen ->
            buildState(entries, day, query, chosenId, detailOpen)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HistoryUiState(),
        )

    fun onDayChange(day: HistoryDay) {
        this.day.value = day
        chosenId.value = null
        detailOpen.value = false
    }

    fun onQueryChange(query: String) {
        this.query.value = query
        chosenId.value = null
        detailOpen.value = false
    }

    fun onSelect(id: String) {
        chosenId.value = id
        detailOpen.value = true
    }

    fun onCloseDetail() {
        detailOpen.value = false
    }

    private fun buildState(
        entries: List<HistoryEntry>,
        day: HistoryDay,
        query: String,
        chosenId: String?,
        detailOpen: Boolean,
    ): HistoryUiState {
        val zone = timeZone()
        val todayStart = startOfDay(now(), zone, daysBack = 0)
        val yesterdayStart = startOfDay(now(), zone, daysBack = 1)
        val dayEntries = entries.filter {
            when (day) {
                HistoryDay.TODAY -> it.createdAt >= todayStart
                HistoryDay.YESTERDAY -> it.createdAt in yesterdayStart until todayStart
            }
        }
        val matches = dayEntries.filter { it.matches(query) }
        val selected = matches.firstOrNull { it.id == chosenId } ?: matches.firstOrNull()
        val whenPrefix = if (day == HistoryDay.TODAY) "Hari ini" else "Kemarin"
        return HistoryUiState(
            day = day,
            query = query,
            rows = matches.map { it.toRow(zone) },
            summary = "${dayEntries.size} transaksi · ${formatRupiah(dayEntries.sumOf { it.grandTotal })}",
            selected = selected,
            selectedWhen = selected?.let { "$whenPrefix · ${formatTime(it.createdAt, zone)}" }.orEmpty(),
            dayIsEmpty = dayEntries.isEmpty(),
            detailOpen = detailOpen,
        )
    }

    private fun HistoryEntry.matches(query: String): Boolean {
        val needle = query.trim().lowercase(Locale.ROOT)
        if (needle.isEmpty()) return true
        val codeText = code.lowercase(Locale.ROOT)
        return codeText.contains(needle) ||
            codeText.removePrefix("#").contains(needle) ||
            lines.any { it.name.lowercase(Locale.ROOT).contains(needle) }
    }

    private fun HistoryEntry.toRow(zone: TimeZone) = HistoryRow(
        id = id,
        code = code,
        timeText = formatTime(createdAt, zone),
        itemCount = itemCount,
        itemNames = lines.joinToString(", ") { it.name },
        grandTotal = grandTotal,
        isPending = isPending,
    )

    private fun formatTime(millis: Long, zone: TimeZone): String =
        SimpleDateFormat("HH.mm", Locale.US).apply { timeZone = zone }.format(millis)

    private fun startOfDay(millis: Long, zone: TimeZone, daysBack: Int): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, -daysBack)
        }.timeInMillis
}
