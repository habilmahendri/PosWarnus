package com.example.poswarnus.di

import com.example.poswarnus.data.auth.AuthGateway
import com.example.poswarnus.data.auth.AuthRepository
import com.example.poswarnus.data.auth.AuthRepositoryImpl
import com.example.poswarnus.data.auth.SessionStore
import com.example.poswarnus.data.auth.SupabaseAuthGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideAuthGateway(client: SupabaseClient): AuthGateway = SupabaseAuthGateway(client)

    @Provides
    @Singleton
    fun provideAuthRepository(gateway: AuthGateway, sessionStore: SessionStore): AuthRepository =
        AuthRepositoryImpl(gateway, sessionStore)
}
