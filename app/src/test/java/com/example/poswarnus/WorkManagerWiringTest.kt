package com.example.poswarnus

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WorkManagerWiringTest {
    @Test
    fun `default WorkManager initializer is removed from the manifest`() {
        val context = RuntimeEnvironment.getApplication()
        val info = context.packageManager.getProviderInfo(
            ComponentName(context, "androidx.startup.InitializationProvider"),
            PackageManager.GET_META_DATA,
        )
        val keys = info.metaData?.keySet().orEmpty()
        assertFalse(keys.contains("androidx.work.WorkManagerInitializer"))
    }

    @Test
    fun `application provides a HiltWorkerFactory configuration`() {
        val app = RuntimeEnvironment.getApplication()
        val config = (app as Configuration.Provider).workManagerConfiguration
        assertTrue(config.workerFactory is HiltWorkerFactory)
    }
}
