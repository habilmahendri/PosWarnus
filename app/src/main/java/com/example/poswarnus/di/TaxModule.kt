package com.example.poswarnus.di

import com.example.poswarnus.data.local.TaxSettingsDao
import com.example.poswarnus.data.remote.SupabaseTaxRemoteDataSource
import com.example.poswarnus.data.remote.TaxRemoteDataSource
import com.example.poswarnus.data.tax.TaxRepository
import com.example.poswarnus.data.tax.TaxRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TaxModule {
    @Provides
    @Singleton
    fun provideTaxRemoteDataSource(client: SupabaseClient): TaxRemoteDataSource =
        SupabaseTaxRemoteDataSource(
            client = client,
            sessionAvailable = { client.auth.currentSessionOrNull() != null },
        )

    @Provides
    @Singleton
    fun provideTaxRepository(dao: TaxSettingsDao, remote: TaxRemoteDataSource): TaxRepository =
        TaxRepositoryImpl(dao, remote)
}
