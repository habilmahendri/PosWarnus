package com.example.poswarnus.data.sync

class FakeSyncScheduler(private val failure: Throwable? = null) : SyncScheduler {
    var requestSyncCount = 0
        private set
    var ensurePeriodicCount = 0
        private set

    override fun requestSync() {
        requestSyncCount++
        failure?.let { throw it }
    }

    override fun ensurePeriodic() {
        ensurePeriodicCount++
    }
}
