package com.example.poswarnus.di

import android.content.Context
import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.local.TransactionDao
import com.example.poswarnus.data.remote.SupabaseTransactionRemoteDataSource
import com.example.poswarnus.data.remote.TransactionRemoteDataSource
import com.example.poswarnus.data.sync.RoomTransactionSyncStore
import com.example.poswarnus.data.sync.SyncScheduler
import com.example.poswarnus.data.sync.TransactionSyncStore
import com.example.poswarnus.data.sync.TransactionSyncer
import com.example.poswarnus.data.sync.WorkManagerSyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun provideTransactionSyncStore(dao: TransactionDao): TransactionSyncStore = RoomTransactionSyncStore(dao)

    @Provides
    @Singleton
    fun provideTransactionRemoteDataSource(client: SupabaseClient): TransactionRemoteDataSource =
        SupabaseTransactionRemoteDataSource(client, sessionAvailable = { client.auth.currentSessionOrNull() != null })

    @Provides
    @Singleton
    fun provideTransactionSyncer(
        store: TransactionSyncStore,
        remote: TransactionRemoteDataSource,
        authRepository: AuthRepository,
    ): TransactionSyncer = TransactionSyncer(store, remote, currentCashierId = { authRepository.currentUserId() })

    @Provides
    @Singleton
    fun provideSyncScheduler(@ApplicationContext context: Context): SyncScheduler = WorkManagerSyncScheduler(context)
}
