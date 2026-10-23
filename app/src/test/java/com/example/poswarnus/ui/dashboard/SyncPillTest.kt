package com.example.poswarnus.ui.dashboard

import com.example.poswarnus.data.sync.SyncSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SyncPillTest {
    private val jakarta = TimeZone.getTimeZone("Asia/Jakarta")
    private val utc = TimeZone.getTimeZone("UTC")

    private fun millis(zone: TimeZone, year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun summary(pending: Int, offline: Int, last: Long?) = SyncSummary(pending, offline, last)

    @Test
    fun `catalogOffline wins over synced`() {
        assertEquals(SyncPill.Offline(0), syncPillOf(true, summary(0, 0, 123)))
    }

    @Test
    fun `catalogOffline with pending keeps the count in Offline`() {
        assertEquals(SyncPill.Offline(4), syncPillOf(true, summary(4, 0, null)))
    }

    @Test
    fun `offlineCount greater than zero wins over pending`() {
        assertEquals(SyncPill.Offline(5), syncPillOf(false, summary(5, 1, null)))
    }

    @Test
    fun `rejected or session failures give Pending`() {
        assertEquals(SyncPill.Pending(2), syncPillOf(false, summary(2, 0, 123)))
    }

    @Test
    fun `pending wins over synced`() {
        assertEquals(SyncPill.Pending(1), syncPillOf(false, summary(1, 0, 999)))
    }

    @Test
    fun `lastSyncedAt only gives Synced`() {
        assertEquals(SyncPill.Synced(999), syncPillOf(false, summary(0, 0, 999)))
    }

    @Test
    fun `nothing gives None`() {
        assertEquals(SyncPill.None, syncPillOf(false, summary(0, 0, null)))
    }

    @Test
    fun `label for Pending is 3 menunggu sinkron`() {
        assertEquals("3 menunggu sinkron", syncPillLabel(SyncPill.Pending(3), 0L, jakarta))
    }

    @Test
    fun `label for Offline is Offline`() {
        assertEquals("Offline", syncPillLabel(SyncPill.Offline(7), 0L, jakarta))
    }

    @Test
    fun `label for None is empty`() {
        assertEquals("", syncPillLabel(SyncPill.None, 0L, jakarta))
    }

    @Test
    fun `label for Synced today is Tersinkron HH mm`() {
        val now = millis(jakarta, 2026, 10, 24, 20, 0)
        val at = millis(jakarta, 2026, 10, 24, 14, 5)
        assertEquals("Tersinkron 14:05", syncPillLabel(SyncPill.Synced(at), now, jakarta))
    }

    @Test
    fun `label for Synced on an earlier day is Tersinkron dd MM HH mm`() {
        val now = millis(jakarta, 2026, 10, 24, 9, 0)
        val at = millis(jakarta, 2026, 10, 23, 14, 5)
        assertEquals("Tersinkron 23/10 14:05", syncPillLabel(SyncPill.Synced(at), now, jakarta))
    }

    @Test
    fun `label compares calendar days in the given zone not UTC`() {
        // 2026-10-24 03:00 Jakarta is still 2026-10-23 20:00 UTC; 2026-10-23 23:00 Jakarta is 16:00 UTC.
        val now = millis(jakarta, 2026, 10, 24, 3, 0)
        val at = millis(jakarta, 2026, 10, 23, 23, 0)
        assertEquals("Tersinkron 23/10 23:00", syncPillLabel(SyncPill.Synced(at), now, jakarta))
        // The same two instants read in UTC fall on the same day.
        assertEquals("Tersinkron 16:00", syncPillLabel(SyncPill.Synced(at), now, utc))
    }

    @Test
    fun `description for Offline with 4 pending includes the count`() {
        assertEquals("Offline, 4 transaksi menunggu sinkron", syncPillDescription(SyncPill.Offline(4)))
    }

    @Test
    fun `description for Offline with 0 pending is Offline`() {
        assertEquals("Offline", syncPillDescription(SyncPill.Offline(0)))
    }

    @Test
    fun `description for Pending mirrors the label and None is empty`() {
        assertEquals("2 menunggu sinkron", syncPillDescription(SyncPill.Pending(2)))
        assertEquals("", syncPillDescription(SyncPill.None))
    }
}
