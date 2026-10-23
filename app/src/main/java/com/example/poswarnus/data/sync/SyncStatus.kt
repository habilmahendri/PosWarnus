package com.example.poswarnus.data.sync

/** Upload state of a locally stored sale. There is deliberately no failed state: a sale stays PENDING until the server accepts it. */
object SyncStatus {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"
}
