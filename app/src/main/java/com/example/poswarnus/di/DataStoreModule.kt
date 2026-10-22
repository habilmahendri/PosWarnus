package com.example.poswarnus.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.example.poswarnus.data.auth.DataStoreSessionStore
import com.example.poswarnus.data.auth.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "session")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideSessionStore(@ApplicationContext context: Context): SessionStore =
        DataStoreSessionStore(context.sessionDataStore)
}
