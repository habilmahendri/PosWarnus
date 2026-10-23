package com.example.poswarnus.ui.dashboard

import com.example.poswarnus.data.sync.SyncSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

sealed interface SyncPill {
    data object None : SyncPill

    // Carries the pending count so the accessible description can include it.
    data class Offline(val pendingCount: Int) : SyncPill
    data class Pending(val count: Int) : SyncPill
    data class Synced(val atMillis: Long) : SyncPill
}

/** [catalogOffline] is `isOffline || loadError` of the dashboard state. First matching rule wins. */
fun syncPillOf(catalogOffline: Boolean, summary: SyncSummary): SyncPill = when {
    catalogOffline || summary.offlineCount > 0 -> SyncPill.Offline(summary.pendingCount)
    summary.pendingCount > 0 -> SyncPill.Pending(summary.pendingCount)
    summary.lastSyncedAt != null -> SyncPill.Synced(summary.lastSyncedAt)
    else -> SyncPill.None
}

fun syncPillLabel(pill: SyncPill, nowMillis: Long, timeZone: TimeZone): String = when (pill) {
    SyncPill.None -> ""
    is SyncPill.Offline -> "Offline"
    is SyncPill.Pending -> "${pill.count} menunggu sinkron"
    is SyncPill.Synced -> {
        val pattern = if (sameDay(pill.atMillis, nowMillis, timeZone)) "HH:mm" else "dd/MM HH:mm"
        val format = SimpleDateFormat(pattern, Locale.US).apply { this.timeZone = timeZone }
        "Tersinkron ${format.format(Date(pill.atMillis))}"
    }
}

fun syncPillDescription(pill: SyncPill): String = when (pill) {
    is SyncPill.Offline ->
        if (pill.pendingCount == 0) "Offline" else "Offline, ${pill.pendingCount} transaksi menunggu sinkron"
    else -> syncPillLabel(pill, System.currentTimeMillis(), TimeZone.getDefault())
}

private fun sameDay(a: Long, b: Long, timeZone: TimeZone): Boolean {
    val first = Calendar.getInstance(timeZone).apply { timeInMillis = a }
    val second = Calendar.getInstance(timeZone).apply { timeInMillis = b }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}
