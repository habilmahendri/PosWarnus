package com.example.poswarnus.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

interface SessionStore {
    suspend fun save(sessionJson: String?)
    suspend fun load(): String?
}

class DataStoreSessionStore(private val dataStore: DataStore<Preferences>) : SessionStore {
    private val key = stringPreferencesKey("session_json")

    override suspend fun save(sessionJson: String?) {
        dataStore.edit { prefs ->
            if (sessionJson == null) prefs.remove(key) else prefs[key] = sessionJson
        }
    }

    override suspend fun load(): String? = dataStore.data.first()[key]
}
