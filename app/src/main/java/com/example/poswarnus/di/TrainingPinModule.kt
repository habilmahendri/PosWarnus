package com.example.poswarnus.di

import com.example.poswarnus.data.remote.SupabaseTrainingPinRemoteDataSource
import com.example.poswarnus.data.remote.TrainingPinRemoteDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TrainingPinModule {
    @Provides
    @Singleton
    fun provideTrainingPinRemoteDataSource(client: SupabaseClient): TrainingPinRemoteDataSource =
        SupabaseTrainingPinRemoteDataSource(
            client = client,
            sessionAvailable = { client.auth.currentSessionOrNull() != null },
        )
}
