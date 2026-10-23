package com.example.poswarnus.data.sync

import android.content.Context
import org.robolectric.RuntimeEnvironment
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerSyncSchedulerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    private fun infos(name: String) = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name).get()

    @Test
    fun `requestSync twice keeps exactly one unique one-time work`() {
        val scheduler = WorkManagerSyncScheduler(context)
        scheduler.requestSync()
        scheduler.requestSync()
        val live = infos("transaction-sync-now").filter { it.state != WorkInfo.State.CANCELLED }
        assertEquals(1, live.size)
    }

    @Test
    fun `ensurePeriodic twice keeps one periodic work`() {
        val scheduler = WorkManagerSyncScheduler(context)
        scheduler.ensurePeriodic()
        scheduler.ensurePeriodic()
        assertEquals(1, infos("transaction-sync-periodic").size)
    }
}
