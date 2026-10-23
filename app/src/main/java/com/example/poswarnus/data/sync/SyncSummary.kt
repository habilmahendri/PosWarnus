package com.example.poswarnus.data.sync

/** What the sync pill needs to know about stored sales. Training sales are never counted. */
data class SyncSummary(
    val pendingCount: Int,
    val offlineCount: Int,
    val lastSyncedAt: Long?,
)
