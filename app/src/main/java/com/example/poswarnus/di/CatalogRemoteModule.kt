package com.example.poswarnus.di

import com.example.poswarnus.data.remote.CatalogRemoteDataSource
import com.example.poswarnus.data.remote.SupabaseCatalogRemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CatalogRemoteModule {
    @Provides
    @Singleton
    fun provideCatalogRemoteDataSource(client: SupabaseClient): CatalogRemoteDataSource =
        SupabaseCatalogRemoteDataSource(client)
}
