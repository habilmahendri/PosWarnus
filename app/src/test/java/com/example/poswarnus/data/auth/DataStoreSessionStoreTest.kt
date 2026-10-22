package com.example.poswarnus.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSessionStoreTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore(): DataStoreSessionStore {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("session_test.preferences_pb") }
        )
        return DataStoreSessionStore(dataStore)
    }

    @Test
    fun `load returns null when nothing has been saved`() = runTest {
        assertNull(newStore().load())
    }

    @Test
    fun `save then load returns the same session json`() = runTest {
        val store = newStore()
        store.save("""{"access_token":"abc"}""")
        assertEquals("""{"access_token":"abc"}""", store.load())
    }

    @Test
    fun `saving null clears the stored session`() = runTest {
        val store = newStore()
        store.save("""{"access_token":"abc"}""")
        store.save(null)
        assertNull(store.load())
    }
}
